#!/usr/bin/env python3
import os
import signal
import sqlite3
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit(
        "usage: c5_acceptance.py <kernel> <fake-worker> <java-classpath>"
    )

KERNEL = str(Path(sys.argv[1]).resolve())
FAKE_WORKER = str(Path(sys.argv[2]).resolve())
CLASSPATH = sys.argv[3]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"
WINDOWS = os.name == "nt"


def endpoint_for(state_dir):
    if WINDOWS:
        return r"\\.\pipe\madre-c5-" + str(os.getpid()) + "-" + uuid.uuid4().hex
    return str(Path(state_dir) / "kernel.sock")


def java(endpoint, *args, timeout=20, check=True):
    result = subprocess.run(
        ["java", "-cp", CLASSPATH, JAVA_MAIN, *args[:1], str(endpoint), *args[1:]],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if check and result.returncode != 0:
        raise AssertionError(
            f"Java command failed ({result.returncode}): {args}\n"
            f"stdout={result.stdout}\nstderr={result.stderr}"
        )
    return result.stdout.strip() if check else result


def db_connect(state_dir):
    con = sqlite3.connect(Path(state_dir) / "data" / "kernel.db", timeout=5)
    con.row_factory = sqlite3.Row
    return con


def work_row(state_dir, work_id):
    with db_connect(state_dir) as con:
        return con.execute(
            "SELECT * FROM work WHERE id=?", (work_id,)
        ).fetchone()


def attempts(state_dir, work_id):
    with db_connect(state_dir) as con:
        return con.execute(
            "SELECT attempt_number,engine_id,model_id,state,technical_failure "
            "FROM attempts WHERE work_id=? ORDER BY attempt_number",
            (work_id,),
        ).fetchall()


def wait_until(predicate, description, timeout=10):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = predicate()
            if last:
                return last
        except (
            FileNotFoundError,
            ProcessLookupError,
            sqlite3.OperationalError,
            subprocess.SubprocessError,
        ):
            pass
        time.sleep(0.03)
    raise AssertionError(
        f"timed out waiting for {description}; last={last!r}"
    )


def wait_state(state_dir, work_id, expected, timeout=10):
    def check():
        row = work_row(state_dir, work_id)
        return row if row is not None and row["state"] == expected else None
    return wait_until(check, f"Work {work_id} -> {expected}", timeout)


def submit(
    endpoint,
    payload,
    *,
    effort="STANDARD",
    urgency="NORMAL",
    exact_engine="fake-standard",
    exact_model="-",
    eligible_at="-",
    timeout="-",
    max_attempts=1,
    retry_delay=0,
):
    return java(
        endpoint,
        "submit-config",
        payload,
        effort,
        urgency,
        "-",
        "-",
        exact_engine,
        exact_model,
        str(eligible_at),
        "-",
        str(timeout),
        str(max_attempts),
        str(retry_delay),
    )


def child_pids(parent_pid):
    if WINDOWS:
        command = (
            "$p=@(Get-CimInstance Win32_Process -Filter "
            f"'ParentProcessId = {parent_pid}' | "
            "Select-Object -ExpandProperty ProcessId); $p -join ','"
        )
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", command],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=10,
            check=False,
        )
        if result.returncode != 0 or not result.stdout.strip():
            return []
        return sorted(
            int(value)
            for value in result.stdout.strip().split(",")
            if value.strip().isdigit()
        )

    found = []
    for status_path in Path("/proc").glob("[0-9]*/status"):
        try:
            status = status_path.read_text(errors="replace")
            line = next(
                item for item in status.splitlines()
                if item.startswith("PPid:")
            )
            if int(line.split()[1]) == parent_pid:
                found.append(int(status_path.parent.name))
        except (
            FileNotFoundError,
            ProcessLookupError,
            StopIteration,
            ValueError,
        ):
            continue
    return sorted(found)


def process_exists(pid):
    if WINDOWS:
        result = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                f"if (Get-Process -Id {pid} -ErrorAction SilentlyContinue) "
                "{ exit 0 } else { exit 1 }",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=10,
            check=False,
        )
        return result.returncode == 0
    return Path(f"/proc/{pid}").exists()


def rss_kib(pid):
    if WINDOWS:
        result = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                f"(Get-Process -Id {pid}).WorkingSet64",
            ],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=10,
            check=True,
        )
        return int(result.stdout.strip()) // 1024
    status = Path(f"/proc/{pid}/status").read_text(errors="replace")
    line = next(
        item for item in status.splitlines()
        if item.startswith("VmRSS:")
    )
    return int(line.split()[1])


def assert_no_tcp(pid, description):
    if WINDOWS:
        result = subprocess.run(
            ["netstat", "-ano", "-p", "tcp"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=10,
            check=True,
        )
        owned = [
            line.strip()
            for line in result.stdout.splitlines()
            if len(line.split()) >= 5 and line.split()[-1] == str(pid)
        ]
        if owned:
            raise AssertionError(
                f"{description} owns TCP sockets: {owned}"
            )
        return

    sockets = set()
    for fd in Path(f"/proc/{pid}/fd").glob("*"):
        try:
            target = str(fd.readlink())
        except (FileNotFoundError, ProcessLookupError, OSError):
            continue
        if target.startswith("socket:[") and target.endswith("]"):
            sockets.add(target[8:-1])
    tcp = set()
    for table in (Path("/proc/net/tcp"), Path("/proc/net/tcp6")):
        if not table.exists():
            continue
        for line in table.read_text(errors="replace").splitlines()[1:]:
            columns = line.split()
            if len(columns) > 9:
                tcp.add(columns[9])
    overlap = sockets & tcp
    if overlap:
        raise AssertionError(
            f"{description} owns TCP socket inode(s): {sorted(overlap)}"
        )


def start_kernel(
    state_dir,
    *,
    delay_ms=300,
    idle_ms=600,
    cpu=2,
    gpu=True,
    close_stdin=False,
):
    endpoint = endpoint_for(state_dir)
    log_path = Path(state_dir) / f"kernel-{time.time_ns()}.log"
    log = open(log_path, "w", encoding="utf-8")
    command = [
        KERNEL,
        "--data-dir", str(Path(state_dir) / "data"),
        "--endpoint", endpoint,
        "--fake-worker", FAKE_WORKER,
        "--fake-delay-ms", str(delay_ms),
        "--worker-idle-ms", str(idle_ms),
        "--cpu-capacity", str(cpu),
        "--ram-capacity-mib", "512",
    ]
    if gpu:
        command += ["--gpu-capacity", "fake-gpu-0=256"]

    kwargs = {}
    stdin = subprocess.DEVNULL
    if WINDOWS:
        kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
    elif close_stdin:
        stdin = None
        kwargs["preexec_fn"] = lambda: os.close(0)

    proc = subprocess.Popen(
        command,
        stdin=stdin,
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
        **kwargs,
    )
    log.close()

    def ready():
        if proc.poll() is not None:
            return False
        result = java(
            endpoint, "engine-ids", timeout=3, check=False
        )
        return result.returncode == 0

    try:
        wait_until(ready, "Kernel local IPC", 10)
    except Exception:
        raise AssertionError(
            "Kernel failed to start\n" +
            log_path.read_text(errors="replace")
        )
    return proc, endpoint, log_path


def stop_kernel(proc, *, clean=True):
    if proc.poll() is None:
        if WINDOWS:
            proc.send_signal(signal.CTRL_BREAK_EVENT)
        else:
            proc.terminate()
        try:
            proc.wait(timeout=8)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=5)
            if clean:
                raise AssertionError(
                    "Kernel did not complete clean shutdown"
                )
    if clean and proc.returncode != 0:
        raise AssertionError(
            f"clean Kernel shutdown returned {proc.returncode}"
        )


def collect(endpoint, work_id, expected):
    return java(endpoint, "collect", work_id, expected, timeout=20)


def test_background_reconnect_selection_and_ipc():
    with tempfile.TemporaryDirectory(prefix="madre-c5-bg-") as state_dir:
        proc, endpoint, log_path = start_kernel(state_dir)
        try:
            idle_rss = rss_kib(proc.pid)
            java(endpoint, "inspect")
            ids = java(endpoint, "engine-ids")
            if ids != "fake-standard,fake-capable,fake-vision":
                raise AssertionError(
                    f"unexpected engine inventory: {ids!r}"
                )
            assert_no_tcp(proc.pid, "Kernel")

            eligible_at = int(time.time() * 1000) + 900
            work_id = submit(
                endpoint,
                "future-background",
                urgency="BACKGROUND",
                eligible_at=eligible_at,
            )
            wait_state(state_dir, work_id, "QUEUED")
            queued_rss = rss_kib(proc.pid)
            if child_pids(proc.pid):
                raise AssertionError(
                    "future queued Work launched a worker early"
                )

            collect(
                endpoint, work_id, "fake:future-background"
            )

            selected = submit(
                endpoint,
                "exact-selection",
                effort="HIGH",
                exact_engine="fake-capable",
                exact_model="high-v1",
            )
            wait_state(state_dir, selected, "RUNNING")
            workers = wait_until(
                lambda: child_pids(proc.pid),
                "active fake worker",
            )
            assert_no_tcp(proc.pid, "Kernel with active worker")
            for worker_pid in workers:
                assert_no_tcp(worker_pid, "fake worker")
            collect(
                endpoint, selected, "fake:exact-selection"
            )
            rows = attempts(state_dir, selected)
            if (
                len(rows) != 1
                or rows[0]["engine_id"] != "fake-capable"
                or rows[0]["model_id"] != "high-v1"
                or rows[0]["state"] != "SUCCEEDED"
            ):
                raise AssertionError(
                    "exact selection was not preserved: " +
                    repr([dict(row) for row in rows])
                )

            platform = "WINDOWS" if WINDOWS else "LINUX"
            transport = (
                "named-pipe" if WINDOWS
                else "unix-domain-socket"
            )
            print(f"C5_{platform}_KERNEL_IDLE_RSS_KIB={idle_rss}")
            print(
                f"C5_{platform}_KERNEL_QUEUED_NO_WORKER_RSS_KIB="
                f"{queued_rss}"
            )
            print(f"C5_{platform}_LOCAL_IPC={transport}")
            print("C5_BACKGROUND_RECONNECT=passed")
            print(
                "C5_EXACT_SELECTION_AND_ENGINE_INSPECTION=passed"
            )
            print("C5_FAKE_KERNEL_WORKER_TCP_DEPENDENCY=none")
        except Exception:
            print(
                log_path.read_text(errors="replace"),
                file=sys.stderr,
            )
            raise
        finally:
            stop_kernel(proc)


def test_clean_shutdown_and_restart_recovery():
    with tempfile.TemporaryDirectory(
        prefix="madre-c5-restart-"
    ) as state_dir:
        proc, endpoint, log_path = start_kernel(
            state_dir, delay_ms=2500, cpu=1
        )
        try:
            work_id = submit(
                endpoint,
                "restart-recovery",
                max_attempts=2,
                retry_delay=20,
            )
            wait_state(state_dir, work_id, "RUNNING")
            workers = wait_until(
                lambda: child_pids(proc.pid),
                "worker before clean shutdown",
            )
            stop_kernel(proc)
            for pid in workers:
                wait_until(
                    lambda pid=pid: not process_exists(pid),
                    f"worker {pid} release after shutdown",
                    5,
                )
            row = work_row(state_dir, work_id)
            if row["state"] != "RUNNING":
                raise AssertionError(
                    "clean shutdown rewrote active durable Work "
                    "before restart recovery"
                )

            proc2, endpoint2, log_path2 = start_kernel(
                state_dir, delay_ms=60, idle_ms=250, cpu=1
            )
            try:
                collect(
                    endpoint2,
                    work_id,
                    "fake:restart-recovery",
                )
                states = [
                    row["state"]
                    for row in attempts(state_dir, work_id)
                ]
                if states != ["INTERRUPTED", "SUCCEEDED"]:
                    raise AssertionError(
                        f"restart history was not truthful: {states}"
                    )
                print(
                    "C5_CLEAN_SHUTDOWN_ACTIVE_ATTEMPT=passed"
                )
                print(
                    "C5_KERNEL_RESTART_RECOVERY="
                    "INTERRUPTED_then_SUCCEEDED"
                )
            except Exception:
                print(
                    log_path2.read_text(errors="replace"),
                    file=sys.stderr,
                )
                raise
            finally:
                stop_kernel(proc2)
        except Exception:
            print(
                log_path.read_text(errors="replace"),
                file=sys.stderr,
            )
            if proc.poll() is None:
                stop_kernel(proc, clean=False)
            raise


def test_crash_cancel_resource_and_generic_capacity():
    with tempfile.TemporaryDirectory(
        prefix="madre-c5-supervision-"
    ) as state_dir:
        proc, endpoint, log_path = start_kernel(
            state_dir, delay_ms=650, idle_ms=250, cpu=1
        )
        try:
            crashed = submit(
                endpoint, "__C3_WORKER_CRASH__"
            )
            wait_state(state_dir, crashed, "FAILED")
            rows = attempts(state_dir, crashed)
            if (
                len(rows) != 1
                or rows[0]["state"] != "FAILED"
                or not rows[0]["technical_failure"].startswith(
                    "WORKER_CRASH"
                )
            ):
                raise AssertionError(
                    "worker crash was not factual: " +
                    repr([dict(row) for row in rows])
                )
            if proc.poll() is not None:
                raise AssertionError("worker crash killed Kernel")

            cancelled = submit(endpoint, "cancel-active")
            wait_state(state_dir, cancelled, "RUNNING")
            cancel_workers = wait_until(
                lambda: child_pids(proc.pid),
                "worker before cancellation",
            )
            java(endpoint, "cancel-work", cancelled)
            wait_state(state_dir, cancelled, "CANCELLED")
            for pid in cancel_workers:
                wait_until(
                    lambda pid=pid: not process_exists(pid),
                    f"cancelled worker {pid} exit",
                    5,
                )

            first = submit(endpoint, "capacity-first")
            wait_state(state_dir, first, "RUNNING")
            second = submit(endpoint, "capacity-second")
            time.sleep(0.12)
            second_row = work_row(state_dir, second)
            if (
                second_row["state"] != "QUEUED"
                or second_row["attempt_count"] != 0
            ):
                raise AssertionError(
                    "resource exclusion allowed concurrent dispatch: "
                    + repr(dict(second_row))
                )
            if len(child_pids(proc.pid)) != 1:
                raise AssertionError(
                    "CPU capacity launched multiple workers"
                )
            collect(
                endpoint, first, "fake:capacity-first"
            )
            collect(
                endpoint, second, "fake:capacity-second"
            )
            print("C5_WORKER_CRASH_SUPERVISION=passed")
            print("C5_RUNNING_CANCELLATION_RELEASE=passed")
            print("C5_RESOURCE_EXCLUSION=passed")
        except Exception:
            print(
                log_path.read_text(errors="replace"),
                file=sys.stderr,
            )
            raise
        finally:
            stop_kernel(proc)

    with tempfile.TemporaryDirectory(
        prefix="madre-c5-generic-"
    ) as state_dir:
        proc, endpoint, log_path = start_kernel(
            state_dir, delay_ms=50, gpu=False
        )
        try:
            work_id = submit(
                endpoint,
                "no-fixture-gpu-default",
                effort="HIGH",
                exact_engine="fake-capable",
            )
            row = wait_state(
                state_dir, work_id, "FAILED"
            )
            if not row["technical_failure"].startswith(
                "INSUFFICIENT_CAPACITY"
            ):
                raise AssertionError(
                    "normal Kernel capacity still implied fixture GPU: "
                    + repr(dict(row))
                )
            print(
                "C5_GENERIC_RESOURCE_CONFIGURATION="
                "no implicit fake-gpu-0"
            )
        except Exception:
            print(
                log_path.read_text(errors="replace"),
                file=sys.stderr,
            )
            raise
        finally:
            stop_kernel(proc)


def test_posix_closed_standard_descriptor_launch():
    if WINDOWS:
        return
    with tempfile.TemporaryDirectory(
        prefix="madre-c5-fd-"
    ) as state_dir:
        proc, endpoint, log_path = start_kernel(
            state_dir, delay_ms=50, close_stdin=True
        )
        try:
            work_id = submit(
                endpoint, "__C3_CHECK_NO_EXTRA_FDS__"
            )
            collect(
                endpoint,
                work_id,
                "fake:__C3_CHECK_NO_EXTRA_FDS__",
            )
            print(
                "C5_POSIX_CLOSED_STANDARD_DESCRIPTOR_LAUNCH=passed"
            )
        except Exception:
            print(
                log_path.read_text(errors="replace"),
                file=sys.stderr,
            )
            raise
        finally:
            stop_kernel(proc)


def main():
    test_background_reconnect_selection_and_ipc()
    test_clean_shutdown_and_restart_recovery()
    test_crash_cancel_resource_and_generic_capacity()
    test_posix_closed_standard_descriptor_launch()
    print("C5 cross-platform hardening acceptance passed")


if __name__ == "__main__":
    main()
