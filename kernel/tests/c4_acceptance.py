#!/usr/bin/env python3
import sqlite3
import stat
import subprocess
import sys
import tempfile
import time
from pathlib import Path

if len(sys.argv) != 5:
    raise SystemExit(
        "usage: c4_acceptance.py <kernel> <llamacpp-worker> "
        "<model.gguf> <java-classpath>"
    )

KERNEL = str(Path(sys.argv[1]).resolve())
LLAMA_WORKER = str(Path(sys.argv[2]).resolve())
MODEL = str(Path(sys.argv[3]).resolve())
CLASSPATH = sys.argv[4]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"
ENGINE_ID = "c4-local-smollm2"
MODEL_ID = "smollm2-135m-instruct-q8_0"


def java(socket_path, *args, timeout=90):
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
            f"Java command failed ({result.returncode}): {args}\n"
            f"stdout={result.stdout}\nstderr={result.stderr}"
        )
    return result.stdout.strip()


def submit(socket_path, payload, timeout="-", exact_selection=True):
    exact_engine = ENGINE_ID if exact_selection else "-"
    exact_model = MODEL_ID if exact_selection else "-"
    return java(
        socket_path,
        "submit-config",
        payload,
        "STANDARD",
        "NORMAL",
        "-",
        "-",
        exact_engine,
        exact_model,
        "-",
        "-",
        str(timeout),
        "1",
        "0",
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
            "SELECT attempt_number,engine_id,model_id,state,technical_failure "
            "FROM attempts WHERE work_id=? ORDER BY attempt_number",
            (work_id,),
        ).fetchall()


def wait_until(predicate, description, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = predicate()
            if last:
                return last
        except (FileNotFoundError, ProcessLookupError, sqlite3.OperationalError):
            pass
        time.sleep(0.02)
    raise AssertionError(
        f"timed out waiting for {description}; last={last!r}"
    )


def wait_state(state_dir, work_id, expected, timeout=90):
    def check():
        row = work_row(state_dir, work_id)
        return row if row is not None and row["state"] == expected else None

    return wait_until(check, f"Work {work_id} -> {expected}", timeout)


def direct_children(proc, needle):
    if proc.poll() is not None:
        return []
    found = []
    for status_path in Path("/proc").glob("[0-9]*/status"):
        try:
            status = status_path.read_text(errors="replace")
            ppid_line = next(
                line for line in status.splitlines() if line.startswith("PPid:")
            )
            if int(ppid_line.split()[1]) != proc.pid:
                continue
            pid = int(status_path.parent.name)
            cmdline = (
                (status_path.parent / "cmdline")
                .read_bytes()
                .replace(b"\0", b" ")
                .decode(errors="replace")
            )
            if needle in cmdline:
                found.append(pid)
        except (FileNotFoundError, ProcessLookupError, StopIteration, ValueError):
            continue
    return sorted(found)


def rss_kib(pid):
    status = Path(f"/proc/{pid}/status").read_text(errors="replace")
    line = next(line for line in status.splitlines() if line.startswith("VmRSS:"))
    return int(line.split()[1])


def process_socket_inodes(pid):
    inodes = set()
    for fd in Path(f"/proc/{pid}/fd").glob("*"):
        try:
            target = str(fd.readlink())
        except (FileNotFoundError, ProcessLookupError, OSError):
            continue
        if target.startswith("socket:[") and target.endswith("]"):
            inodes.add(target[8:-1])
    return inodes


def tcp_socket_inodes():
    result = set()
    for table in (Path("/proc/net/tcp"), Path("/proc/net/tcp6")):
        if not table.exists():
            continue
        for line in table.read_text(errors="replace").splitlines()[1:]:
            columns = line.split()
            if len(columns) > 9:
                result.add(columns[9])
    return result


def assert_no_tcp(pid, description):
    overlap = process_socket_inodes(pid) & tcp_socket_inodes()
    if overlap:
        raise AssertionError(
            f"{description} unexpectedly owns TCP socket inode(s): {sorted(overlap)}"
        )


def start_kernel(state_dir, idle_ms=800):
    socket_path = Path(state_dir) / "kernel.sock"
    log_path = Path(state_dir) / "kernel.log"
    log = open(log_path, "w", encoding="utf-8")
    args = [
        KERNEL,
        "--data-dir", str(Path(state_dir) / "data"),
        "--endpoint", str(socket_path),
        "--worker-idle-ms", str(idle_ms),
        "--cpu-capacity", "2",
        "--ram-capacity-mib", "1024",
        "--worker-engine-id", ENGINE_ID,
        "--worker-executable", LLAMA_WORKER,
        "--worker-model-id", MODEL_ID,
        "--worker-work-types", "text-generation/v1",
        "--worker-capabilities", "basic-text",
        "--worker-efforts", "STANDARD",
        "--worker-cpu-slots", "1",
        "--worker-ram-mib", "512",
        "--worker-arg", "--engine-id",
        "--worker-arg", ENGINE_ID,
        "--worker-arg", "--model",
        "--worker-arg", MODEL,
        "--worker-arg", "--n-predict",
        "--worker-arg", "128",
        "--worker-arg", "--ctx-size",
        "--worker-arg", "512",
        "--worker-arg", "--gpu-layers",
        "--worker-arg", "0",
    ]
    proc = subprocess.Popen(
        args,
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
        wait_until(socket_ready, "Kernel socket", 10)
    except Exception:
        log.flush()
        log.close()
        raise AssertionError(
            f"Kernel failed to start\n"
            f"{log_path.read_text(errors='replace')}"
        )
    log.close()
    return proc, socket_path, log_path


def stop_kernel(proc):
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=5)


def main():
    with tempfile.TemporaryDirectory(prefix="madre-c4-") as state_dir:
        proc, sock, log_path = start_kernel(state_dir)
        try:
            kernel_idle_rss = rss_kib(proc.pid)
            engine_ids = java(sock, "engine-ids")
            if engine_ids != ENGINE_ID:
                raise AssertionError(
                    "C4 Kernel inventory must contain only the configured real worker; "
                    f"got {engine_ids!r}"
                )
            if "fake-" in engine_ids:
                raise AssertionError(f"fake engine leaked into C4 inventory: {engine_ids!r}")
            java(
                sock,
                "assert-engine-descriptor",
                ENGINE_ID,
                MODEL_ID,
                "1",
                str(512 * 1024 * 1024),
                "-",
                "0",
            )
            if direct_children(proc, "madre-llamacpp-worker"):
                raise AssertionError(
                    "llama.cpp worker exists before physical Work requires it"
                )
            assert_no_tcp(proc.pid, "Kernel")

            future_at = int(time.time() * 1000) + 600_000
            queued = java(
                sock,
                "submit-config",
                "queued-memory-measurement",
                "STANDARD",
                "BACKGROUND",
                "-",
                "-",
                ENGINE_ID,
                MODEL_ID,
                str(future_at),
                "-",
                "-",
                "1",
                "0",
            )
            wait_state(state_dir, queued, "QUEUED", 5)
            kernel_queued_rss = rss_kib(proc.pid)
            if direct_children(proc, "madre-llamacpp-worker"):
                raise AssertionError(
                    "future queued Work launched a worker before eligibility"
                )
            java(sock, "cancel-work", queued)
            wait_state(state_dir, queued, "CANCELLED", 5)

            timed = submit(
                sock,
                "Timeout supervision must kill this native inference.",
                timeout=1,
            )
            wait_state(state_dir, timed, "FAILED", 20)
            timed_attempts = attempts(state_dir, timed)
            if [row["state"] for row in timed_attempts] != ["TIMED_OUT"]:
                raise AssertionError(
                    f"native timeout attempt was not TIMED_OUT: {timed_attempts}"
                )
            if not timed_attempts[0]["technical_failure"].startswith(
                "ATTEMPT_TIMEOUT"
            ):
                raise AssertionError(
                    "native timeout failure was not factual: "
                    f"{dict(timed_attempts[0])}"
                )
            wait_until(
                lambda: not direct_children(proc, "madre-llamacpp-worker"),
                "timed-out native worker exit",
                10,
            )

            prompt = (
                "<|im_start|>user\n"
                "Complete this sentence with a short factual answer: "
                "The capital of France is"
                "<|im_end|>\n<|im_start|>assistant\n"
            )
            succeeded = submit(sock, prompt, exact_selection=False)
            wait_state(state_dir, succeeded, "SUCCEEDED", 90)
            success_attempts = attempts(state_dir, succeeded)
            if (
                len(success_attempts) != 1
                or success_attempts[0]["state"] != "SUCCEEDED"
            ):
                raise AssertionError(
                    f"real inference attempt did not succeed: {success_attempts}"
                )
            if (
                success_attempts[0]["engine_id"] != ENGINE_ID
                or success_attempts[0]["model_id"] != MODEL_ID
            ):
                raise AssertionError(
                    "real inference persisted wrong physical selection: "
                    f"{dict(success_attempts[0])}"
                )

            worker_pid = wait_until(
                lambda: (
                    pids[0]
                    if (pids := direct_children(
                        proc, "madre-llamacpp-worker"
                    ))
                    else None
                ),
                "warm native worker",
                10,
            )
            worker_loaded_rss = rss_kib(worker_pid)
            assert_no_tcp(proc.pid, "Kernel during native inference")
            assert_no_tcp(worker_pid, "llama.cpp worker")
            row = work_row(state_dir, succeeded)
            result_path = Path(row["result_path"])
            result_bytes = result_path.read_bytes()
            if not result_bytes:
                raise AssertionError(
                    "real text-generation/v1 produced an empty durable result"
                )
            result_text = result_bytes.decode("utf-8")
            java(sock, "collect", succeeded, result_text, timeout=30)
            if result_path.exists():
                raise AssertionError(
                    "acknowledged result file remained after Kernel collection"
                )

            wait_until(
                lambda: not Path(f"/proc/{worker_pid}").exists(),
                "idle native worker process/resource release",
                10,
            )
            kernel_after_release_rss = rss_kib(proc.pid)

            cancelled = submit(
                sock,
                "<|im_start|>user\n"
                "Write a long list of integers and explanations."
                "<|im_end|>\n<|im_start|>assistant\n",
            )
            wait_state(state_dir, cancelled, "RUNNING", 10)
            cancel_pid = wait_until(
                lambda: (
                    pids[0]
                    if (pids := direct_children(
                        proc, "madre-llamacpp-worker"
                    ))
                    else None
                ),
                "native worker for cancellation",
                10,
            )
            java(sock, "cancel-work", cancelled)
            wait_state(state_dir, cancelled, "CANCELLED", 10)
            cancelled_attempts = attempts(state_dir, cancelled)
            if (
                len(cancelled_attempts) != 1
                or cancelled_attempts[0]["state"] != "CANCELLED"
            ):
                raise AssertionError(
                    "native cancellation attempt was not CANCELLED: "
                    f"{cancelled_attempts}"
                )
            wait_until(
                lambda: not Path(f"/proc/{cancel_pid}").exists(),
                "cancelled native worker exit",
                10,
            )

            print(f"C4_ENGINE_INVENTORY={engine_ids}")
            print("C4_UNPINNED_DISPATCH=real configured worker selected without exact engine/model")
            print(f"C4_REAL_RESULT={result_text!r}")
            print(f"C4_KERNEL_IDLE_RSS_KIB={kernel_idle_rss}")
            print(f"C5_KERNEL_IDLE_RSS_KIB={kernel_idle_rss}")
            print(
                "C5_KERNEL_QUEUED_NO_WORKER_RSS_KIB="
                f"{kernel_queued_rss}"
            )
            print(
                "C5_WORKER_PRE_MODEL_LOAD_RSS=not-measurable "
                "(worker loads the configured model before serving Work; "
                "C5 adds no readiness protocol)"
            )
            print(f"C4_WORKER_LOADED_RSS_KIB={worker_loaded_rss}")
            print(
                "C5_WORKER_RAM_AFTER_MODEL_LOAD_RSS_KIB="
                f"{worker_loaded_rss}"
            )
            print(
                "C4_WORKER_GPU_VRAM_MIB=0 "
                "(CPU-only acceptance configuration)"
            )
            print(
                "C5_WORKER_VRAM_AFTER_MODEL_LOAD_MIB=0 "
                "(CPU-only CI runner; VRAM unavailable)"
            )
            print(
                "C4_KERNEL_RSS_AFTER_WORKER_RELEASE_KIB="
                f"{kernel_after_release_rss}"
            )
            print(
                "C4_IDLE_RELEASE=worker process absent; "
                "model/context process resources released"
            )
            print(
                "C5_RELEASE_AFTER_WORKER_TERMINATION="
                f"process=absent kernel_rss_kib={kernel_after_release_rss} "
                "worker_vram_mib=0"
            )
            print("C5_LOCAL_HTTP_TCP_DEPENDENCY=none-observed")
            print(
                "C4_TIMEOUT=TIMED_OUT native worker killed and "
                "reservation released"
            )
            print(
                "C4_CANCELLATION=CANCELLED native worker killed and "
                "resources released"
            )
        except Exception:
            print(log_path.read_text(errors="replace"), file=sys.stderr)
            raise
        finally:
            stop_kernel(proc)


if __name__ == "__main__":
    main()
