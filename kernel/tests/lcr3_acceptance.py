#!/usr/bin/env python3
import http.server
import os
import pathlib
import socket
import subprocess
import sys
import tempfile
import threading
import time

if len(sys.argv) != 4:
    raise SystemExit("usage: lcr3_acceptance.py <kernel> <process-fixture> <java-classpath>")

KERNEL = pathlib.Path(sys.argv[1]).resolve()
FIXTURE = pathlib.Path(sys.argv[2]).resolve()
CLASSPATH = sys.argv[3]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"
JAVA_LCR3 = "io.github.didacll.madre.kernel.client.Lcr3KernelClientProcess"
WINDOWS = os.name == "nt"


def endpoint_for(root, name):
    if WINDOWS:
        return pathlib.Path("\\\\.\\pipe\\" + f"madre-lcr3-{os.getpid()}-{name}")
    return root / f"{name}.sock"


def start_kernel(root, name, *, data=None, max_concurrent=2, extra_env=None):
    if data is None:
        data = root / f"data-{name}"
    endpoint = endpoint_for(root, name)
    log_path = root / f"{name}.log"
    log = open(log_path, "ab", buffering=0)
    env = os.environ.copy()
    if extra_env:
        env.update(extra_env)
    process = subprocess.Popen(
        [str(KERNEL), "--data-dir", str(data), "--endpoint", str(endpoint),
         "--max-concurrent", str(max_concurrent)],
        stdout=log, stderr=subprocess.STDOUT, env=env)
    time.sleep(0.15)
    if process.poll() is not None:
        log.close()
        raise AssertionError(f"Kernel exited early for {name}; log={log_path.read_text(errors='replace') if log_path.exists() else ''}")
    return process, endpoint, data, log, log_path


def stop_kernel(process, log):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    log.close()


def java(endpoint, *args, main=JAVA_MAIN, timeout=6, env=None):
    command = ["java", "-cp", CLASSPATH, main, *args[:1], str(endpoint), *args[1:]]
    child_env = os.environ.copy()
    if env:
        child_env.update(env)
    last = None
    for _ in range(30):
        try:
            result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    env=child_env, timeout=timeout)
        except subprocess.TimeoutExpired as ex:
            raise AssertionError(f"Java client timed out while Kernel should remain responsive: {' '.join(command)}") from ex
        if result.returncode == 0:
            return result.stdout.strip()
        last = result
        if "IPC_FAILURE" not in result.stderr and "FileNotFoundException" not in result.stderr:
            break
        time.sleep(0.05)
    raise AssertionError(f"Java client failed: {' '.join(command)}\nstdout={last.stdout if last else ''}\nstderr={last.stderr if last else ''}")


def parse_inspect(raw):
    parts = raw.split("|", 13)
    if len(parts) != 14:
        raise AssertionError(f"malformed inspect output: {raw}")
    return {
        "state": parts[0], "attempts": int(parts[1]), "released": parts[2] == "true",
        "attempt_number": int(parts[3]) if parts[3] else None, "candidate": parts[4], "kind": parts[5],
        "target": parts[6], "attempt_state": parts[7], "started": int(parts[8]) if parts[8] else None,
        "ended": int(parts[9]) if parts[9] else None, "exit": int(parts[10]) if parts[10] else None,
        "http": int(parts[11]) if parts[11] else None, "attempt_failure": parts[12], "failure": parts[13],
        "raw": raw,
    }


def inspect(endpoint, work):
    return parse_inspect(java(endpoint, "inspect", work))


def wait_for(endpoint, work, predicate, description, timeout=8):
    deadline = time.time() + timeout
    latest = None
    while time.time() < deadline:
        latest = inspect(endpoint, work)
        if predicate(latest):
            return latest
        time.sleep(0.04)
    raise AssertionError(f"Work {work} did not reach {description}; latest={latest}")


def wait_state(endpoint, work, states, timeout=8):
    return wait_for(endpoint, work, lambda value: value["state"] in states, str(states), timeout)


def submit_process(endpoint, cid, body, *, eligible="-", deadline="-", timeout="-", safety="NEVER", attempts=1, delay=0, process_args=()):
    return java(endpoint, "submit-process", cid, str(FIXTURE), body, str(eligible), str(deadline), str(timeout),
                safety, str(attempts), str(delay), "-", *process_args)


def submit_urgency(endpoint, urgency, cid, body, delay_ms):
    return java(endpoint, "submit-urgency", urgency, cid, str(FIXTURE), body, str(delay_ms), main=JAVA_LCR3)


def open_stalled_client(endpoint):
    if WINDOWS:
        deadline = time.time() + 4
        while True:
            try:
                stream = open(str(endpoint), "r+b", buffering=0)
                stream.write(b"MADR")
                stream.flush()
                return stream
            except OSError:
                if time.time() >= deadline:
                    raise
                time.sleep(0.05)
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    deadline = time.time() + 4
    while True:
        try:
            sock.connect(str(endpoint))
            break
        except OSError:
            if time.time() >= deadline:
                sock.close()
                raise
            time.sleep(0.05)
    sock.sendall(b"MADR")
    return sock


def close_stalled_client(client):
    try:
        client.close()
    except OSError:
        pass


def contains_bytes(root, needle):
    for path in root.rglob("*"):
        if path.is_file():
            try:
                if needle in path.read_bytes():
                    return path
            except OSError:
                pass
    return None


class HeaderProbe(http.server.BaseHTTPRequestHandler):
    requests = []
    def do_POST(self):
        HeaderProbe.requests.append((self.path, dict(self.headers.items())))
        body = b"ok"
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, format, *args):
        return


class ProbeServer(http.server.ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


with tempfile.TemporaryDirectory(prefix="madre-lcr3-") as temp:
    root = pathlib.Path(temp)

    # A deliberately incomplete frame must not monopolize the local control plane.
    kernel, endpoint, data, log, _ = start_kernel(root, "ipc")
    stalled = None
    try:
        stalled = open_stalled_client(endpoint)
        work = submit_process(endpoint, "responsive-client", "independent-client")
        state = wait_state(endpoint, work, {"SUCCEEDED"})
        assert state["attempts"] == 1
        assert java(endpoint, "result", work) == "independent-client"
    finally:
        if stalled is not None:
            close_stalled_client(stalled)
        stop_kernel(kernel, log)

    # release is terminal payload ownership release: missing files and repetition are harmless.
    kernel, endpoint, data, log, _ = start_kernel(root, "release")
    try:
        work = submit_process(endpoint, "release-idempotent", "release-body")
        wait_state(endpoint, work, {"SUCCEEDED"})
        work_dir = data / "work" / work
        for path in work_dir.glob("*.bin"):
            path.unlink(missing_ok=True)
        assert java(endpoint, "release", work) == "released"
        assert java(endpoint, "release", work) == "released"
        released = inspect(endpoint, work)
        assert released["released"] and released["state"] == "SUCCEEDED"
        assert java(endpoint, "result", work) == "<none>"
        assert not list(work_dir.glob("*.bin"))
    finally:
        stop_kernel(kernel, log)

    # Current scheduler: one global execution bound; urgency among eligible Work; future eligibility/deadline/retry delay do not head-of-line block.
    kernel, endpoint, data, log, _ = start_kernel(root, "scheduler", max_concurrent=1)
    try:
        blocker = submit_process(endpoint, "capacity-blocker", "blocker", process_args=("--delay-ms", "900"))
        wait_state(endpoint, blocker, {"RUNNING"})
        background = submit_urgency(endpoint, "BACKGROUND", "background", "background", 120)
        interactive = submit_urgency(endpoint, "INTERACTIVE", "interactive", "interactive", 120)
        bg_queued = inspect(endpoint, background)
        int_queued = inspect(endpoint, interactive)
        assert bg_queued["state"] == "QUEUED" and bg_queued["attempts"] == 0
        assert int_queued["state"] == "QUEUED" and int_queued["attempts"] == 0
        int_done = wait_state(endpoint, interactive, {"SUCCEEDED"})
        bg_done = wait_state(endpoint, background, {"SUCCEEDED"})
        assert int_done["started"] < bg_done["started"]

        now = int(time.time() * 1000)
        delayed = submit_process(endpoint, "future-eligible", "future", eligible=now + 900)
        immediate = submit_process(endpoint, "eligible-now", "now")
        wait_state(endpoint, immediate, {"SUCCEEDED"})
        delayed_before = inspect(endpoint, delayed)
        assert delayed_before["state"] == "QUEUED" and delayed_before["attempts"] == 0
        wait_state(endpoint, delayed, {"SUCCEEDED"})

        now = int(time.time() * 1000)
        expired = submit_process(endpoint, "deadline-before-eligibility", "never", eligible=now + 1000, deadline=now + 250)
        expired_state = wait_state(endpoint, expired, {"FAILED"})
        assert expired_state["attempts"] == 0 and "DEADLINE_EXPIRED" in expired_state["failure"]

        retrying = submit_process(endpoint, "retry-delay", "fails", safety="DEFINITE_FAILURES", attempts=2, delay=900,
                                  process_args=("--mode", "fail"))
        wait_for(endpoint, retrying,
                 lambda value: value["state"] == "QUEUED" and value["attempts"] == 1,
                 "queued retry delay after first failure")
        bypass = submit_process(endpoint, "retry-delay-bypass", "bypass")
        bypass_done = wait_state(endpoint, bypass, {"SUCCEEDED"})
        retry_waiting = inspect(endpoint, retrying)
        assert retry_waiting["state"] == "QUEUED" and retry_waiting["attempts"] == 1
        retry_done = wait_state(endpoint, retrying, {"FAILED"})
        assert retry_done["attempts"] == 2
        assert bypass_done["started"] < retry_done["started"]
    finally:
        stop_kernel(kernel, log)

    # Resolved environment content stays ephemeral and cannot inject another HTTP header.
    HeaderProbe.requests = []
    probe = ProbeServer(("127.0.0.1", 0), HeaderProbe)
    probe_thread = threading.Thread(target=probe.serve_forever, daemon=True)
    probe_thread.start()
    secret_env = "MADRE_LCR3_HEADER_VALUE"
    sentinel = "lcr3-secret-token\r\nX-Madre-Injected: yes"
    kernel, endpoint, secret_data, log, secret_log = start_kernel(root, "header", extra_env={secret_env: sentinel})
    try:
        uri = f"http://127.0.0.1:{probe.server_port}/must-not-arrive"
        work = java(endpoint, "submit-http", "header-injection", uri, "body", "-", "-", "-", "NEVER", "1", "0", "-",
                    "--env", "Authorization", secret_env, "Bearer ", "")
        state = wait_state(endpoint, work, {"FAILED"})
        assert state["attempts"] == 1
        assert state["attempt_failure"] == "HTTP_HEADER_VALUE_CONTAINS_CR_OR_LF"
        assert not HeaderProbe.requests
        assert sentinel not in state["raw"]
        assert contains_bytes(secret_data, sentinel.encode()) is None
    finally:
        stop_kernel(kernel, log)
        probe.shutdown()
        probe.server_close()
    assert contains_bytes(secret_data, sentinel.encode()) is None
    assert sentinel.encode() not in secret_log.read_bytes()

print("LCR3 acceptance passed")
