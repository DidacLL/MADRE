#!/usr/bin/env python3
import sqlite3
import stat
import subprocess
import sys
import tempfile
import time
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: c2_acceptance.py <kernel-binary> <java-classpath>")

KERNEL = str(Path(sys.argv[1]).resolve())
CLASSPATH = sys.argv[2]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"


def now_ms():
    return int(time.time() * 1000)


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


def submit(socket_path, payload, effort="STANDARD", urgency="NORMAL", capabilities="-", allowlist="-",
           exact_engine="-", exact_model="-", eligible_at="-", deadline="-", timeout="-",
           max_attempts=1, retry_delay=0):
    return java(
        socket_path,
        "submit-config",
        payload,
        effort,
        urgency,
        capabilities,
        allowlist,
        exact_engine,
        exact_model,
        str(eligible_at),
        str(deadline),
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
        except (sqlite3.OperationalError, FileNotFoundError):
            pass
        time.sleep(0.02)
    raise AssertionError(f"timed out waiting for {description}; last={last!r}")


def wait_state(state_dir, work_id, expected, timeout=8):
    def check():
        row = work_row(state_dir, work_id)
        return row if row is not None and row["state"] == expected else None
    return wait_until(check, f"Work {work_id} -> {expected}", timeout)


def wait_running(state_dir, work_id, timeout=5):
    return wait_state(state_dir, work_id, "RUNNING", timeout)


def start_kernel(state_dir, delay_ms):
    socket_path = Path(state_dir) / "kernel.sock"
    log = open(Path(state_dir) / f"kernel-{time.time_ns()}.log", "w", encoding="utf-8")
    proc = subprocess.Popen(
        [KERNEL, "--data-dir", str(Path(state_dir) / "data"), "--endpoint", str(socket_path),
         "--fake-delay-ms", str(delay_ms)],
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
        logs = "\n".join(p.read_text(errors="replace") for p in Path(state_dir).glob("kernel-*.log"))
        raise AssertionError(f"Kernel failed to start\n{logs}")
    log.close()
    return proc, socket_path


def stop_kernel(proc, hard=False):
    if proc.poll() is not None:
        return
    if hard:
        proc.kill()
    else:
        proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=5)


def assert_attempt_states(state_dir, work_id, states):
    rows = attempts(state_dir, work_id)
    actual = [row["state"] for row in rows]
    if actual != states:
        raise AssertionError(f"attempt states for {work_id}: expected {states}, got {actual}")
    return rows


def with_state(test):
    with tempfile.TemporaryDirectory(prefix="madre-c2-") as state_dir:
        test(state_dir)


def test_restart_retry(state_dir):
    proc, sock = start_kernel(state_dir, 1500)
    try:
        work_id = submit(sock, "restart-two", max_attempts=2)
        wait_running(state_dir, work_id)
        stop_kernel(proc, hard=True)
        proc, sock = start_kernel(state_dir, 40)
        wait_state(state_dir, work_id, "SUCCEEDED")
        assert_attempt_states(state_dir, work_id, ["INTERRUPTED", "SUCCEEDED"])
    finally:
        stop_kernel(proc)
    print("C2 restart maxAttempts=2: INTERRUPTED then SUCCEEDED")


def test_restart_final_failure(state_dir):
    proc, sock = start_kernel(state_dir, 1500)
    try:
        work_id = submit(sock, "restart-one", max_attempts=1)
        wait_running(state_dir, work_id)
        stop_kernel(proc, hard=True)
        proc, sock = start_kernel(state_dir, 40)
        wait_state(state_dir, work_id, "FAILED")
        assert_attempt_states(state_dir, work_id, ["INTERRUPTED"])
    finally:
        stop_kernel(proc)
    print("C2 restart maxAttempts=1: INTERRUPTED then FAILED")


def test_future_background(state_dir):
    proc, sock = start_kernel(state_dir, 40)
    try:
        eligible = now_ms() + 1200
        work_id = submit(sock, "future-background", urgency="BACKGROUND", eligible_at=eligible)
        row = work_row(state_dir, work_id)
        if row["state"] != "QUEUED" or row["attempt_count"] != 0:
            raise AssertionError(f"future Work dispatched early: {dict(row)}")
        wait_state(state_dir, work_id, "SUCCEEDED", 5)
        rows = assert_attempt_states(state_dir, work_id, ["SUCCEEDED"])
        if rows[0]["started_at_ms"] < eligible:
            raise AssertionError("future BACKGROUND Work started before eligibleAtMs")
    finally:
        stop_kernel(proc)
    print("C2 future BACKGROUND Work completed after submitter JVM exited")


def test_capability_and_effort_selection(state_dir):
    proc, sock = start_kernel(state_dir, 30)
    try:
        structured = submit(sock, "structured", capabilities="structured-output")
        wait_state(state_dir, structured, "SUCCEEDED")
        row = work_row(state_dir, structured)
        if row["selected_engine_id"] != "fake-capable":
            raise AssertionError(f"capability filtering selected incompatible engine: {dict(row)}")

        image = submit(sock, "image", effort="HIGH", capabilities="image-input")
        wait_state(state_dir, image, "SUCCEEDED")
        row = work_row(state_dir, image)
        if row["selected_engine_id"] != "fake-vision":
            raise AssertionError(f"image capability filtering selected incompatible engine: {dict(row)}")

        standard = submit(sock, "standard")
        high = submit(sock, "high", effort="HIGH")
        wait_state(state_dir, standard, "SUCCEEDED")
        wait_state(state_dir, high, "SUCCEEDED")
        if work_row(state_dir, standard)["selected_engine_id"] != "fake-standard":
            raise AssertionError("STANDARD did not select first eligible STANDARD engine")
        if work_row(state_dir, high)["selected_engine_id"] != "fake-capable":
            raise AssertionError("HIGH dispatched to engine without HIGH support")
    finally:
        stop_kernel(proc)
    print("C2 capability filtering and STANDARD/HIGH effort eligibility passed")


def test_exact_selection(state_dir):
    proc, sock = start_kernel(state_dir, 30)
    try:
        exact = submit(sock, "exact", effort="HIGH", exact_engine="fake-capable", exact_model="shared-v1")
        wait_state(state_dir, exact, "SUCCEEDED")
        row = work_row(state_dir, exact)
        if (row["selected_engine_id"], row["selected_model_id"]) != ("fake-capable", "shared-v1"):
            raise AssertionError(f"exact engine/model not honored: {dict(row)}")

        incompatible = submit(
            sock, "exact-incompatible", exact_engine="fake-standard", exact_model="high-v1",
            max_attempts=3, retry_delay=1)
        failed = wait_state(state_dir, incompatible, "FAILED")
        if not failed["technical_failure"].startswith("EXACT_SELECTION_UNSATISFIED"):
            raise AssertionError(f"exact incompatibility was not explicit: {dict(failed)}")
        if attempts(state_dir, incompatible):
            raise AssertionError("static exact-selection failure created an execution attempt")

        missing = submit(sock, "exact-missing", exact_engine="does-not-exist", max_attempts=3, retry_delay=1)
        failed = wait_state(state_dir, missing, "FAILED")
        if not failed["technical_failure"].startswith("EXACT_SELECTION_UNSATISFIED"):
            raise AssertionError(f"missing exact engine was not explicit: {dict(failed)}")
        if attempts(state_dir, missing):
            raise AssertionError("missing exact engine broadened or retried")
    finally:
        stop_kernel(proc)
    print("C2 exact engine/model selection is strict with no fallback")


def test_timeout_and_technical_retry(state_dir):
    proc, sock = start_kernel(state_dir, 300)
    try:
        timed = submit(sock, "timeout", timeout=60, max_attempts=2, retry_delay=20)
        wait_state(state_dir, timed, "FAILED")
        rows = assert_attempt_states(state_dir, timed, ["TIMED_OUT", "TIMED_OUT"])
        if not all(row["technical_failure"].startswith("ATTEMPT_TIMEOUT") for row in rows):
            raise AssertionError("timeout attempt history lacks technical failure")
    finally:
        stop_kernel(proc)

    proc, sock = start_kernel(state_dir, 10)
    try:
        failed = submit(sock, "__C2_TECHNICAL_FAILURE__", max_attempts=2, retry_delay=10)
        wait_state(state_dir, failed, "FAILED")
        rows = assert_attempt_states(state_dir, failed, ["FAILED", "FAILED"])
        if not all(row["technical_failure"].startswith("FAKE_TECHNICAL_FAILURE") for row in rows):
            raise AssertionError("technical failure retry history is not factual")
    finally:
        stop_kernel(proc)
    print("C2 timeout and technical-failure retries exhaust exactly at maxAttempts")


def test_deadlines(state_dir):
    proc, sock = start_kernel(state_dir, 5000)
    try:
        pre_deadline = now_ms() + 300
        pre_eligible = now_ms() + 1500
        pre = submit(sock, "deadline-before-dispatch", eligible_at=pre_eligible, deadline=pre_deadline,
                     max_attempts=2, retry_delay=10)
        failed = wait_state(state_dir, pre, "FAILED", 3)
        if not failed["technical_failure"].startswith("DEADLINE_EXPIRED"):
            raise AssertionError(f"pre-dispatch deadline not explicit: {dict(failed)}")
        if attempts(state_dir, pre):
            raise AssertionError("deadline expiry before dispatch invented an attempt")

        during_deadline = now_ms() + 1800
        during = submit(sock, "deadline-during-attempt", deadline=during_deadline, max_attempts=2)
        wait_running(state_dir, during)
        wait_state(state_dir, during, "FAILED", 4)
        rows = assert_attempt_states(state_dir, during, ["TIMED_OUT"])
        if not rows[0]["technical_failure"].startswith("DEADLINE_EXPIRED"):
            raise AssertionError("running deadline did not stop attempt at deadline")
    finally:
        stop_kernel(proc)
    print("C2 deadlines fail pre-dispatch without attempts and stop running attempts")


def test_urgency_ordering(state_dir):
    proc, sock = start_kernel(state_dir, 80)
    try:
        eligible = now_ms() + 1800
        background = submit(sock, "background", urgency="BACKGROUND", eligible_at=eligible)
        normal1 = submit(sock, "normal-1", urgency="NORMAL", eligible_at=eligible)
        interactive = submit(sock, "interactive", urgency="INTERACTIVE", eligible_at=eligible)
        normal2 = submit(sock, "normal-2", urgency="NORMAL", eligible_at=eligible)
        ids = [background, normal1, interactive, normal2]
        for work_id in ids:
            wait_state(state_dir, work_id, "SUCCEEDED", 5)
        with db_connect(state_dir) as con:
            actual = [row[0] for row in con.execute(
                "SELECT a.work_id FROM attempts a WHERE a.work_id IN (?,?,?,?) ORDER BY a.started_at_ms,a.rowid",
                ids,
            ).fetchall()]
        expected = [interactive, normal1, normal2, background]
        if actual != expected:
            raise AssertionError(f"urgency/FIFO order mismatch: expected {expected}, got {actual}")
    finally:
        stop_kernel(proc)
    print("C2 urgency ordering INTERACTIVE > NORMAL > BACKGROUND with FIFO within urgency passed")


def test_cancellation_no_retry(state_dir):
    proc, sock = start_kernel(state_dir, 1500)
    try:
        work_id = submit(sock, "cancel-running", max_attempts=3, retry_delay=10)
        wait_running(state_dir, work_id)
        java(sock, "cancel-work", work_id)
        wait_state(state_dir, work_id, "CANCELLED")
        assert_attempt_states(state_dir, work_id, ["CANCELLED"])
        time.sleep(0.2)
        if len(attempts(state_dir, work_id)) != 1:
            raise AssertionError("cancelled Work retried")
    finally:
        stop_kernel(proc)
    print("C2 running cancellation is terminal and never retries")


def main():
    tests = [
        test_restart_retry,
        test_restart_final_failure,
        test_future_background,
        test_capability_and_effort_selection,
        test_exact_selection,
        test_timeout_and_technical_retry,
        test_deadlines,
        test_urgency_ordering,
        test_cancellation_no_retry,
    ]
    for test in tests:
        with_state(test)
    print("C2 scheduling/recovery acceptance passed")


if __name__ == "__main__":
    main()
