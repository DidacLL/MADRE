#!/usr/bin/env python3
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time

if len(sys.argv) != 4:
    raise SystemExit("usage: lcr1_acceptance.py <kernel> <process-fixture> <java-classpath>")

KERNEL = pathlib.Path(sys.argv[1]).resolve()
FIXTURE = pathlib.Path(sys.argv[2]).resolve()
CLASSPATH = sys.argv[3]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"
WINDOWS = os.name == "nt"


def java(endpoint, *args, retries=20):
    command = ["java", "-cp", CLASSPATH, JAVA_MAIN, *args[:1], str(endpoint), *args[1:]]
    last = None
    for _ in range(retries):
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode == 0:
            return result.stdout.strip()
        last = result
        if "IPC_FAILURE" not in result.stderr and "FileNotFoundException" not in result.stderr:
            break
        time.sleep(0.05)
    raise AssertionError(f"Java client failed: {' '.join(command)}\nstdout={last.stdout if last else ''}\nstderr={last.stderr if last else ''}")


def endpoint_for(root, name):
    if WINDOWS:
        return pathlib.Path("\\\\.\\pipe\\" + f"madre-lcr1-{os.getpid()}-{name}")
    return root / f"{name}.sock"


def start_kernel(root, name, data=None):
    if data is None:
        data = root / f"data-{name}"
    endpoint = endpoint_for(root, name)
    log = open(root / f"{name}.log", "ab", buffering=0)
    process = subprocess.Popen(
        [str(KERNEL), "--data-dir", str(data), "--endpoint", str(endpoint), "--max-concurrent", "2"],
        stdout=log,
        stderr=subprocess.STDOUT,
    )
    time.sleep(0.12)
    if process.poll() is not None:
        raise AssertionError(f"Kernel exited early for {name}; see {root / f'{name}.log'}")
    return process, endpoint, data, log


def stop_kernel(process, log, hard=False):
    if process.poll() is None:
        if hard:
            process.kill()
        else:
            process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    log.close()


def inspect(endpoint, work_id):
    raw = java(endpoint, "inspect", work_id)
    parts = raw.split("|", 4)
    if len(parts) != 5:
        raise AssertionError(f"malformed inspect output: {raw}")
    return {
        "state": parts[0],
        "attempts": int(parts[1]),
        "candidate": parts[2],
        "target": parts[3],
        "failure": parts[4],
    }


def wait_state(endpoint, work_id, expected, timeout=8):
    deadline = time.time() + timeout
    latest = None
    while time.time() < deadline:
        latest = inspect(endpoint, work_id)
        if latest["state"] in expected:
            return latest
        time.sleep(0.04)
    raise AssertionError(f"Work {work_id} did not reach {expected}; latest={latest}")


def submit(endpoint, candidate, executable, input_text, *, eligible="-", deadline="-", timeout="-",
           safety="NEVER", attempts=1, delay=0, target="-", process_args=()):
    return java(
        endpoint,
        "submit",
        candidate,
        str(executable),
        input_text,
        str(eligible),
        str(deadline),
        str(timeout),
        safety,
        str(attempts),
        str(delay),
        target,
        *process_args,
    )


def wait_marker(path, lines=1, timeout=4):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if path.exists() and len(path.read_text().splitlines()) >= lines:
            return
        time.sleep(0.02)
    raise AssertionError(f"marker {path} did not reach {lines} lines")


def assert_lines(path, expected):
    actual = len(path.read_text().splitlines()) if path.exists() else 0
    if actual != expected:
        raise AssertionError(f"marker {path} has {actual} lines, expected {expected}")


with tempfile.TemporaryDirectory(prefix="madre-lcr1-") as temp:
    root = pathlib.Path(temp)

    # 1/2/3/4: native + Java physical contract, one-shot process, durable result after submitting caller exits.
    kernel, endpoint, _, log = start_kernel(root, "basic")
    try:
        work = submit(endpoint, "exact-process", FIXTURE, "durable-result", target="opaque-local-target", process_args=("--delay-ms", "250"))
        state = wait_state(endpoint, work, {"SUCCEEDED"})
        assert state["candidate"] == "exact-process"
        assert state["target"] == "opaque-local-target"
        assert java(endpoint, "result", work) == "durable-result"

        # 5: a single unavailable supplied candidate is not widened to some Kernel-owned alternative.
        missing = root / "does-not-exist"
        unavailable = submit(endpoint, "only-approved", missing, "never-runs")
        failed = wait_state(endpoint, unavailable, {"FAILED"})
        assert failed["attempts"] == 0
        assert "NO_DISPATCHABLE_CANDIDATE" in failed["failure"]

        # 6: with multiple approved candidates, physical routing remains inside that exact supplied set.
        routed = java(endpoint, "submit-two", "missing-first", str(missing), "approved-second", str(FIXTURE), "inside-set")
        routed_state = wait_state(endpoint, routed, {"SUCCEEDED"})
        assert routed_state["candidate"] == "approved-second"
        assert java(endpoint, "result", routed) == "inside-set"

        # 7a: attempt timeout remains physical and bounded.
        timed = submit(endpoint, "timeout", FIXTURE, "late", timeout="100", process_args=("--delay-ms", "800"))
        timed_state = wait_state(endpoint, timed, {"FAILED"})
        assert "ATTEMPT_TIMEOUT" in timed_state["failure"]

        # 7b: running cancellation terminates the one-shot process attempt.
        cancelled = submit(endpoint, "cancel", FIXTURE, "cancelled", process_args=("--delay-ms", "1500"))
        wait_state(endpoint, cancelled, {"RUNNING"})
        java(endpoint, "cancel", cancelled)
        wait_state(endpoint, cancelled, {"CANCELLED"})

        # 7c: deadline before dispatch remains terminal without inventing an attempt.
        now = int(time.time() * 1000)
        deadline_work = submit(endpoint, "deadline", FIXTURE, "never", eligible=now + 700, deadline=now + 150)
        deadline_state = wait_state(endpoint, deadline_work, {"FAILED"})
        assert deadline_state["attempts"] == 0
        assert "DEADLINE_EXPIRED" in deadline_state["failure"]

        # A definitely observed technical failure is retried only under explicit retry semantics.
        marker_definite = root / "definite.marker"
        definite = submit(
            endpoint, "definite", FIXTURE, "fails", safety="DEFINITE_FAILURES", attempts=2,
            process_args=("--marker", str(marker_definite), "--mode", "fail"),
        )
        definite_state = wait_state(endpoint, definite, {"FAILED"})
        assert definite_state["attempts"] == 2
        assert "PROCESS_EXIT_NONZERO: 17" in definite_state["failure"]
        wait_marker(marker_definite, lines=2)
        assert_lines(marker_definite, 2)
    finally:
        stop_kernel(kernel, log)

    # 8: queued future Work survives Kernel restart and executes later.
    data_future = root / "future-data"
    kernel, endpoint, _, log = start_kernel(root, "future-a", data_future)
    future_at = int(time.time() * 1000) + 900
    future = submit(endpoint, "future", FIXTURE, "after-restart", eligible=future_at)
    stop_kernel(kernel, log)
    kernel, endpoint, _, log = start_kernel(root, "future-b", data_future)
    try:
        wait_state(endpoint, future, {"SUCCEEDED"})
        assert java(endpoint, "result", future) == "after-restart"
    finally:
        stop_kernel(kernel, log)

    # 9: interrupted attempt defaults to honest UNKNOWN_COMPLETION and is not blindly repeated.
    data_unknown = root / "unknown-data"
    marker_unsafe = root / "unsafe.marker"
    kernel, endpoint, _, log = start_kernel(root, "unknown-a", data_unknown)
    unsafe = submit(
        endpoint, "unsafe", FIXTURE, "uncertain", safety="DEFINITE_FAILURES", attempts=2,
        process_args=("--marker", str(marker_unsafe), "--delay-ms", "1200"),
    )
    wait_marker(marker_unsafe)
    stop_kernel(kernel, log, hard=True)
    kernel, endpoint, _, log = start_kernel(root, "unknown-b", data_unknown)
    try:
        unknown = wait_state(endpoint, unsafe, {"UNKNOWN_COMPLETION"})
        assert unknown["attempts"] == 1
        assert "UNKNOWN_COMPLETION" in unknown["failure"]
        time.sleep(1.3)
        assert_lines(marker_unsafe, 1)
    finally:
        stop_kernel(kernel, log)

    # 10: retry after unknown completion occurs only when the caller explicitly declares it safe.
    data_safe = root / "safe-data"
    marker_safe = root / "safe.marker"
    kernel, endpoint, _, log = start_kernel(root, "safe-a", data_safe)
    safe = submit(
        endpoint, "safe", FIXTURE, "safe-retry", safety="INCLUDING_UNKNOWN_COMPLETION", attempts=2,
        process_args=("--marker", str(marker_safe), "--delay-ms", "700"),
    )
    wait_marker(marker_safe)
    stop_kernel(kernel, log, hard=True)
    kernel, endpoint, _, log = start_kernel(root, "safe-b", data_safe)
    try:
        safe_state = wait_state(endpoint, safe, {"SUCCEEDED"})
        assert safe_state["attempts"] == 2
        assert java(endpoint, "result", safe) == "safe-retry"
        wait_marker(marker_safe, lines=2)
        assert_lines(marker_safe, 2)
    finally:
        stop_kernel(kernel, log)

print("LCR1 acceptance passed")
