#!/usr/bin/env python3
import sqlite3
import stat
import subprocess
import sys
import tempfile
import time
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit("usage: c3_acceptance.py <kernel-binary> <fake-worker-binary> <java-classpath>")

KERNEL = str(Path(sys.argv[1]).resolve())
FAKE_WORKER = str(Path(sys.argv[2]).resolve())
CLASSPATH = sys.argv[3]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"


def java(socket_path, *args, timeout=20):
    result = subprocess.run(
        ["java", "-cp", CLASSPATH, JAVA_MAIN, *args[:1], str(socket_path), *args[1:]],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"Java command failed ({result.returncode}): {args}\nstdout={result.stdout}\nstderr={result.stderr}"
        )
    return result.stdout.strip()


def submit(socket_path, payload, effort="STANDARD", exact_engine="-", timeout="-",
           max_attempts=1, retry_delay=0):
    return java(
        socket_path,
        "submit-config",
        payload,
        effort,
        "NORMAL",
        "-",
        "-",
        exact_engine,
        "-",
        "-",
        "-",
        str(timeout),
        str(max_attempts),
        str(retry_delay),
    )


def db_connect(state_dir):
    con = sqlite3.connect(Path(state_dir) / "data" / "kernel.db", timeout=5)
    con.row_factory = sqlite3.Row
    return con


def work_row(state_dir, work_id):
    with db_connect(state_dir) as con:
        return con.execute("SELECT * FROM work WHERE id=?", (work_id,)).fetchone()


def attempts(state_dir, work_id):
    with db_connect(state_dir) as con:
        return con.execute(
            "SELECT attempt_number,engine_id,model_id,started_at_ms,ended_at_ms,state,technical_failure "
            "FROM attempts WHERE work_id=? ORDER BY attempt_number",
            (work_id,),
        ).fetchall()


def wait_until(predicate, description, timeout=8):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = predicate()
            last = value
            if value:
                return value
        except (sqlite3.OperationalError, FileNotFoundError, ProcessLookupError):
            pass
        time.sleep(0.02)
    raise AssertionError(f"timed out waiting for {description}; last={last!r}")


def wait_state(state_dir, work_id, expected, timeout=8):
    def check():
        row = work_row(state_dir, work_id)
        return row if row is not None and row["state"] == expected else None
    return wait_until(check, f"Work {work_id} -> {expected}", timeout)


def child_pids(proc):
    if proc.poll() is not None:
        return []
    children_path = Path(f"/proc/{proc.pid}/task/{proc.pid}/children")
    try:
        raw = children_path.read_text().strip()
    except FileNotFoundError:
        return []
    result = []
    for token in raw.split():
        pid = int(token)
        try:
            cmdline = Path(f"/proc/{pid}/cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")
        except FileNotFoundError:
            continue
        if "madre-fake-worker" in cmdline:
            result.append(pid)
    return sorted(result)


def start_kernel(state_dir, delay_ms=200, idle_ms=600, cpu=2, ram_mib=512, gpu_vram_mib=256):
    socket_path = Path(state_dir) / "kernel.sock"
    log_path = Path(state_dir) / f"kernel-{time.time_ns()}.log"
    log = open(log_path, "w", encoding="utf-8")
    proc = subprocess.Popen(
        [
            KERNEL,
            "--data-dir", str(Path(state_dir) / "data"),
            "--endpoint", str(socket_path),
            "--fake-worker", FAKE_WORKER,
            "--fake-delay-ms", str(delay_ms),
            "--worker-idle-ms", str(idle_ms),
            "--cpu-capacity", str(cpu),
            "--ram-capacity-mib", str(ram_mib),
            "--gpu-vram-mib", str(gpu_vram_mib),
        ],
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
    )

    def socket_ready():
        if proc.poll() is not None:
            return False
        try:
            return stat.S_ISSOCK(socket_path.stat().st_mode)
        except FileNotFoundError:
            return False

    try:
        wait_until(socket_ready, "Kernel socket", 5)
    except Exception:
        log.flush()
        log.close()
        raise AssertionError(f"Kernel failed to start\n{log_path.read_text(errors='replace')}")
    log.close()
    return proc, socket_path


def stop_kernel(proc, state_dir):
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=5)
    leftovers = child_pids(proc)
    if leftovers:
        raise AssertionError(f"fake workers survived Kernel termination: {leftovers}")


def assert_attempt_states(state_dir, work_id, states):
    rows = attempts(state_dir, work_id)
    actual = [row["state"] for row in rows]
    if actual != states:
        raise AssertionError(f"attempt states for {work_id}: expected {states}, got {actual}")
    return rows


def with_state(test):
    with tempfile.TemporaryDirectory(prefix="madre-c3-") as state_dir:
        test(state_dir)


def test_on_demand_reuse_idle(state_dir):
    proc, sock = start_kernel(state_dir, delay_ms=250, idle_ms=700)
    try:
        time.sleep(0.15)
        if child_pids(proc):
            raise AssertionError("fake worker exists before any Work requires it")

        first = submit(sock, "first", exact_engine="fake-standard")
        wait_state(state_dir, first, "RUNNING")
        first_pid = wait_until(lambda: child_pids(proc), "on-demand fake worker")[0]
        wait_state(state_dir, first, "SUCCEEDED")
        warm = child_pids(proc)
        if warm != [first_pid]:
            raise AssertionError(f"worker did not remain warm after success: first={first_pid}, warm={warm}")

        second = submit(sock, "second", exact_engine="fake-standard")
        wait_state(state_dir, second, "RUNNING")
        reused = wait_until(lambda: child_pids(proc), "reused fake worker")
        if reused != [first_pid]:
            raise AssertionError(f"suitable warm worker was not reused: first={first_pid}, second={reused}")
        wait_state(state_dir, second, "SUCCEEDED")
        wait_until(lambda: not child_pids(proc), "idle worker termination", 3)
    finally:
        stop_kernel(proc, state_dir)
    print("C3 on-demand launch, warm reuse and configurable idle termination passed")


def assert_followup_succeeds(state_dir, sock, label):
    work_id = submit(sock, f"followup-{label}", exact_engine="fake-standard")
    wait_state(state_dir, work_id, "SUCCEEDED", 5)


def test_release_every_path(state_dir):
    proc, sock = start_kernel(state_dir, delay_ms=220, idle_ms=800, cpu=1, ram_mib=64)
    try:
        succeeded = submit(sock, "success", exact_engine="fake-standard")
        wait_state(state_dir, succeeded, "SUCCEEDED")
        assert_followup_succeeds(state_dir, sock, "success")

        failed = submit(sock, "__C2_TECHNICAL_FAILURE__", exact_engine="fake-standard")
        wait_state(state_dir, failed, "FAILED")
        assert_attempt_states(state_dir, failed, ["FAILED"])
        assert_followup_succeeds(state_dir, sock, "failure")

        cancelled = submit(sock, "cancel-me", exact_engine="fake-standard")
        wait_state(state_dir, cancelled, "RUNNING")
        java(sock, "cancel-work", cancelled)
        wait_state(state_dir, cancelled, "CANCELLED")
        assert_attempt_states(state_dir, cancelled, ["CANCELLED"])
        assert_followup_succeeds(state_dir, sock, "cancellation")

        timed = submit(sock, "timeout", exact_engine="fake-standard", timeout=40)
        wait_state(state_dir, timed, "FAILED")
        assert_attempt_states(state_dir, timed, ["TIMED_OUT"])
        assert_followup_succeeds(state_dir, sock, "timeout")

        crashed = submit(sock, "__C3_WORKER_CRASH__", exact_engine="fake-standard")
        wait_state(state_dir, crashed, "FAILED")
        rows = assert_attempt_states(state_dir, crashed, ["FAILED"])
        if not rows[0]["technical_failure"].startswith("WORKER_CRASH"):
            raise AssertionError(f"worker crash was not recorded factually: {dict(rows[0])}")
        if proc.poll() is not None:
            raise AssertionError("worker crash killed Kernel")
        assert_followup_succeeds(state_dir, sock, "crash")
    finally:
        stop_kernel(proc, state_dir)
    print("C3 CPU/RAM reservations released after success, failure, cancellation, timeout and crash")


def test_worker_crash_retry(state_dir):
    proc, sock = start_kernel(state_dir, delay_ms=120, idle_ms=500, cpu=1, ram_mib=64)
    try:
        work_id = submit(
            sock,
            "__C3_WORKER_CRASH__",
            exact_engine="fake-standard",
            max_attempts=2,
            retry_delay=40,
        )
        wait_state(state_dir, work_id, "FAILED", 5)
        rows = assert_attempt_states(state_dir, work_id, ["FAILED", "FAILED"])
        if not all(row["technical_failure"].startswith("WORKER_CRASH") for row in rows):
            raise AssertionError(f"worker crash retry history is not factual: {[dict(row) for row in rows]}")
        if rows[1]["started_at_ms"] < rows[0]["ended_at_ms"] + 40:
            raise AssertionError("worker crash retry began before C2 retryDelayMs elapsed")
        if proc.poll() is not None:
            raise AssertionError("Kernel died while supervising crashing worker")
    finally:
        stop_kernel(proc, state_dir)
    print("C3 forced worker crash leaves Kernel alive and follows C2 retry/final-failure semantics")


def assert_serialized_by_capacity(state_dir, *, cpu, ram_mib, gpu_vram_mib,
                                  first_engine, second_engine, first_effort="STANDARD",
                                  second_effort="STANDARD", label):
    proc, sock = start_kernel(
        state_dir,
        delay_ms=500,
        idle_ms=100,
        cpu=cpu,
        ram_mib=ram_mib,
        gpu_vram_mib=gpu_vram_mib,
    )
    try:
        first = submit(sock, f"{label}-first", effort=first_effort, exact_engine=first_engine)
        second = submit(sock, f"{label}-second", effort=second_effort, exact_engine=second_engine)
        wait_state(state_dir, first, "RUNNING")
        time.sleep(0.12)
        second_row = work_row(state_dir, second)
        if second_row["state"] != "QUEUED" or second_row["attempt_count"] != 0:
            raise AssertionError(f"{label} capacity allowed concurrent dispatch: {dict(second_row)}")
        workers = child_pids(proc)
        if len(workers) != 1:
            raise AssertionError(f"{label} capacity launched {len(workers)} workers concurrently: {workers}")
        wait_state(state_dir, first, "SUCCEEDED", 5)
        wait_state(state_dir, second, "SUCCEEDED", 5)
    finally:
        stop_kernel(proc, state_dir)


def test_resource_capacity_serialization(_state_dir):
    with tempfile.TemporaryDirectory(prefix="madre-c3-cpu-") as state_dir:
        assert_serialized_by_capacity(
            state_dir,
            cpu=1,
            ram_mib=512,
            gpu_vram_mib=256,
            first_engine="fake-standard",
            second_engine="fake-capable",
            label="CPU",
        )
    with tempfile.TemporaryDirectory(prefix="madre-c3-ram-") as state_dir:
        assert_serialized_by_capacity(
            state_dir,
            cpu=2,
            ram_mib=128,
            gpu_vram_mib=256,
            first_engine="fake-standard",
            second_engine="fake-capable",
            label="RAM",
        )
    with tempfile.TemporaryDirectory(prefix="madre-c3-gpu-") as state_dir:
        assert_serialized_by_capacity(
            state_dir,
            cpu=2,
            ram_mib=512,
            gpu_vram_mib=96,
            first_engine="fake-capable",
            second_engine="fake-vision",
            first_effort="HIGH",
            second_effort="HIGH",
            label="GPU/VRAM",
        )
    print("C3 CPU, system RAM and GPU identity/VRAM capacities prevent overcommit")


def main():
    tests = [
        test_on_demand_reuse_idle,
        test_release_every_path,
        test_worker_crash_retry,
        test_resource_capacity_serialization,
    ]
    for test in tests:
        with_state(test)
    print("C3 worker/resource acceptance passed")


if __name__ == "__main__":
    main()
