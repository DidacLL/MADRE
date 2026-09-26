#!/usr/bin/env python3
import http.server
import os
import pathlib
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
from collections import defaultdict

if len(sys.argv) != 4:
    raise SystemExit("usage: lcr2_acceptance.py <kernel> <process-fixture> <java-classpath>")

KERNEL = pathlib.Path(sys.argv[1]).resolve()
FIXTURE = pathlib.Path(sys.argv[2]).resolve()
CLASSPATH = sys.argv[3]
JAVA_MAIN = "io.github.didacll.madre.kernel.client.KernelClientProcess"
WINDOWS = os.name == "nt"


class HttpState:
    def __init__(self):
        self.lock = threading.Lock()
        self.condition = threading.Condition(self.lock)
        self.requests = []
        self.counts = defaultdict(int)

    def record(self, path, body, headers):
        with self.condition:
            self.counts[path] += 1
            item = {"path": path, "body": body, "headers": dict(headers), "count": self.counts[path]}
            self.requests.append(item)
            self.condition.notify_all()
            return item

    def wait_path(self, path, count=1, timeout=5):
        deadline = time.time() + timeout
        with self.condition:
            while True:
                matches = [r for r in self.requests if r["path"] == path]
                if len(matches) >= count:
                    return matches
                remaining = deadline - time.time()
                if remaining <= 0:
                    raise AssertionError(f"HTTP fixture did not receive {path} x{count}; requests={self.requests}")
                self.condition.wait(remaining)


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "MADRELCR2Fixture/1"
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        item = self.server.state.record(self.path, body, self.headers.items())
        if self.path.startswith("/status503"):
            payload = b"provider-specific error body"
            self.send_response(503)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        if self.path.startswith("/drop-first"):
            if item["count"] == 1:
                self.close_connection = True
                try:
                    self.connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                self.connection.close()
                return
        elif self.path.startswith("/drop"):
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.connection.close()
            return
        if self.path.startswith("/slow") or self.path.startswith("/restart"):
            time.sleep(2.0)
        payload = body
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass

    def log_message(self, format, *args):
        return


class FixtureServer(http.server.ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    def __init__(self, state):
        super().__init__(("127.0.0.1", 0), Handler)
        self.state = state


def start_http_fixture():
    state = HttpState()
    server = FixtureServer(state)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, state, f"http://127.0.0.1:{server.server_port}"


def java(endpoint, *args, retries=30, env=None):
    command = ["java", "-cp", CLASSPATH, JAVA_MAIN, *args[:1], str(endpoint), *args[1:]]
    last = None
    child_env = os.environ.copy()
    if env:
        child_env.update(env)
    for _ in range(retries):
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=child_env)
        if result.returncode == 0:
            return result.stdout.strip()
        last = result
        if "IPC_FAILURE" not in result.stderr and "FileNotFoundException" not in result.stderr:
            break
        time.sleep(0.05)
    raise AssertionError(f"Java client failed: {' '.join(command)}\nstdout={last.stdout if last else ''}\nstderr={last.stderr if last else ''}")


def endpoint_for(root, name):
    if WINDOWS:
        return pathlib.Path("\\\\.\\pipe\\" + f"madre-lcr2-{os.getpid()}-{name}")
    return root / f"{name}.sock"


def start_kernel(root, name, data=None, extra_env=None):
    if data is None:
        data = root / f"data-{name}"
    endpoint = endpoint_for(root, name)
    log_path = root / f"{name}.log"
    log = open(log_path, "ab", buffering=0)
    env = os.environ.copy()
    if extra_env:
        env.update(extra_env)
    process = subprocess.Popen(
        [str(KERNEL), "--data-dir", str(data), "--endpoint", str(endpoint), "--max-concurrent", "2"],
        stdout=log,
        stderr=subprocess.STDOUT,
        env=env,
    )
    time.sleep(0.15)
    if process.poll() is not None:
        log.close()
        raise AssertionError(f"Kernel exited early for {name}; log={log_path.read_text(errors='replace') if log_path.exists() else ''}")
    return process, endpoint, data, log, log_path


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


def wait_state(endpoint, work, expected, timeout=8):
    deadline = time.time() + timeout
    latest = None
    while time.time() < deadline:
        latest = inspect(endpoint, work)
        if latest["state"] in expected:
            return latest
        time.sleep(0.04)
    raise AssertionError(f"Work {work} did not reach {expected}; latest={latest}")


def submit_process(endpoint, cid, executable, body, *, eligible="-", deadline="-", timeout="-", safety="NEVER", attempts=1, delay=0, target="-", process_args=()):
    return java(endpoint, "submit-process", cid, str(executable), body, str(eligible), str(deadline), str(timeout), safety, str(attempts), str(delay), target, *process_args)


def submit_http(endpoint, cid, uri, body, *, eligible="-", deadline="-", timeout="-", safety="NEVER", attempts=1, delay=0, target="-", headers=()):
    flags = []
    for header in headers:
        if header[0] == "literal":
            flags += ["--literal", header[1], header[2]]
        elif header[0] == "env":
            flags += ["--env", header[1], header[2], header[3], header[4]]
        else:
            raise AssertionError(f"unknown header spec {header}")
    return java(endpoint, "submit-http", cid, uri, body, str(eligible), str(deadline), str(timeout), safety, str(attempts), str(delay), target, *flags)


def unused_http_uri():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    return f"http://127.0.0.1:{port}/unreachable"


def assert_payload_removed(data, work):
    work_dir = data / "work" / work
    retained = [p for p in work_dir.glob("*.bin") if p.exists()]
    if retained:
        raise AssertionError(f"retained payload files remain after release for {work}: {retained}")


def release_and_assert(endpoint, data, work):
    assert java(endpoint, "release", work) == "released"
    state = inspect(endpoint, work)
    assert state["released"]
    assert java(endpoint, "result", work) == "<none>"
    assert_payload_removed(data, work)


def contains_bytes(root, needle):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            if needle in path.read_bytes():
                return path
        except OSError:
            pass
    return None


server, http_state, base_uri = start_http_fixture()
server2, http_state2, base_uri2 = start_http_fixture()
try:
    with tempfile.TemporaryDirectory(prefix="madre-lcr2-") as temp:
        root = pathlib.Path(temp)

        # ProcessInvocation remains one-shot and candidate-specific.
        kernel, endpoint, data, log, _ = start_kernel(root, "basic")
        try:
            process_ok = submit_process(endpoint, "process-exact", FIXTURE, "process-own-stdin", target="opaque-process", process_args=("--delay-ms", "80"))
            pstate = wait_state(endpoint, process_ok, {"SUCCEEDED"})
            assert pstate["candidate"] == "process-exact" and pstate["kind"] == "PROCESS" and pstate["target"] == "opaque-process"
            assert pstate["exit"] == 0
            assert java(endpoint, "result", process_ok) == "process-own-stdin"

            process_timeout = submit_process(endpoint, "process-timeout", FIXTURE, "late", timeout=100, process_args=("--delay-ms", "800"))
            pt = wait_state(endpoint, process_timeout, {"FAILED"})
            assert "ATTEMPT_TIMEOUT" in pt["failure"] and pt["attempt_state"] == "TIMED_OUT"

            process_cancel = submit_process(endpoint, "process-cancel", FIXTURE, "cancel", process_args=("--delay-ms", "1500"))
            wait_state(endpoint, process_cancel, {"RUNNING"})
            java(endpoint, "cancel", process_cancel)
            pc = wait_state(endpoint, process_cancel, {"CANCELLED"})
            assert pc["attempt_state"] == "CANCELLED"

            # Generic HTTP: exact opaque provider-shaped bytes and literal header arrive unchanged.
            path = "/echo-provider-shape"
            provider_body = '{"provider_specific":{"messages":[{"role":"user","content":"opaque"}]}}'
            http_ok = submit_http(endpoint, "http-exact", base_uri + path, provider_body, target="opaque-http-target",
                                  headers=(("literal", "Content-Type", "application/x-provider-json"),))
            hs = wait_state(endpoint, http_ok, {"SUCCEEDED"})
            assert hs["kind"] == "HTTP" and hs["candidate"] == "http-exact" and hs["target"] == "opaque-http-target"
            assert hs["http"] == 200 and java(endpoint, "result", http_ok) == provider_body
            received = http_state.wait_path(path)[0]
            assert received["body"] == provider_body.encode()
            assert received["headers"].get("Content-Type") == "application/x-provider-json"

            # Endpoint is entirely submitted data: another independent target needs no Kernel rebuild/change.
            endpoint_change = submit_http(endpoint, "http-other-endpoint", base_uri2 + "/endpoint-change", "same-kernel-different-target")
            wait_state(endpoint, endpoint_change, {"SUCCEEDED"})
            assert http_state2.wait_path("/endpoint-change")[0]["body"] == b"same-kernel-different-target"

            # A single candidate with an unavailable late-bound credential fails without inventing a destination or attempt.
            missing_only_path = "/missing-only"
            missing_only = submit_http(endpoint, "missing-only", base_uri + missing_only_path, "must-not-send",
                                       headers=(("env", "Authorization", "MADRE_LCR2_MISSING_ONLY", "Bearer ", ""),))
            missing_only_state = wait_state(endpoint, missing_only, {"FAILED"})
            assert missing_only_state["attempts"] == 0 and "NO_DISPATCHABLE_CANDIDATE" in missing_only_state["failure"]
            assert not [r for r in http_state.requests if r["path"] == missing_only_path]

            # Two HTTP candidates carry independent bodies. Missing late binding skips only that supplied candidate.
            two_path = "/two-candidates"
            two = java(endpoint, "submit-two-http", "missing-secret-first", base_uri + two_path, "WRONG-FIRST-BODY", "MADRE_LCR2_MISSING_SECRET",
                       "approved-second", base_uri + two_path, "RIGHT-SECOND-BODY", "-", "NEVER", "1", "-")
            two_state = wait_state(endpoint, two, {"SUCCEEDED"})
            assert two_state["candidate"] == "approved-second" and two_state["target"] == "second-target"
            assert java(endpoint, "result", two) == "RIGHT-SECOND-BODY"
            matches = http_state.wait_path(two_path)
            assert len(matches) == 1 and matches[0]["body"] == b"RIGHT-SECOND-BODY"

            # Mixed supplied set: unavailable process routes to the supplied HTTP candidate, never elsewhere.
            mixed_path = "/mixed"
            missing_exe = root / "not-present-executable"
            mixed = java(endpoint, "submit-mixed", "missing-process", str(missing_exe), "PROCESS-ONLY-BODY", "supplied-http", base_uri + mixed_path, "HTTP-ONLY-BODY")
            mixed_state = wait_state(endpoint, mixed, {"SUCCEEDED"})
            assert mixed_state["candidate"] == "supplied-http" and mixed_state["kind"] == "HTTP"
            assert http_state.wait_path(mixed_path)[0]["body"] == b"HTTP-ONLY-BODY"

            # Received non-success status is definite factual failure, with no provider-body interpretation.
            failure_work = submit_http(endpoint, "http-503", base_uri + "/status503", '{"whatever":"provider means"}')
            failure_state = wait_state(endpoint, failure_work, {"FAILED"})
            assert failure_state["http"] == 503 and failure_state["attempt_failure"] == "HTTP_STATUS_503"

            # Pre-send connect failure is definite and may retry under DEFINITE_FAILURES.
            presend = submit_http(endpoint, "pre-send", unused_http_uri(), "never-arrives", timeout=500, safety="DEFINITE_FAILURES", attempts=2)
            presend_state = wait_state(endpoint, presend, {"FAILED"})
            assert presend_state["attempts"] == 2
            assert "BEFORE_SUBMISSION" in presend_state["failure"]

            # Timeout after body reaches server is UNKNOWN_COMPLETION and not retried by DEFINITE_FAILURES.
            timeout_path = "/slow-timeout"
            uncertain = submit_http(endpoint, "timeout-after-send", base_uri + timeout_path, "may-have-completed", timeout=150, safety="DEFINITE_FAILURES", attempts=2)
            http_state.wait_path(timeout_path)
            uncertain_state = wait_state(endpoint, uncertain, {"UNKNOWN_COMPLETION"})
            assert uncertain_state["attempts"] == 1 and uncertain_state["attempt_state"] == "UNKNOWN_COMPLETION"
            assert "may have reached remote target" in uncertain_state["failure"]

            # Unknown completion retries only under explicit INCLUDING_UNKNOWN_COMPLETION.
            retry_path = "/drop-first-retry"
            safe_retry = submit_http(endpoint, "unknown-safe", base_uri + retry_path, "retry-exact-body", safety="INCLUDING_UNKNOWN_COMPLETION", attempts=2)
            safe_state = wait_state(endpoint, safe_retry, {"SUCCEEDED"})
            assert safe_state["attempts"] == 2 and safe_state["http"] == 200
            assert java(endpoint, "result", safe_retry) == "retry-exact-body"
            retry_requests = http_state.wait_path(retry_path, 2)
            assert [r["body"] for r in retry_requests] == [b"retry-exact-body", b"retry-exact-body"]

            # Cancellation after remote submission is unknown, never confirmed remote cancellation, never auto-retried.
            cancel_path = "/slow-cancel"
            remote_cancel = submit_http(endpoint, "cancel-after-send", base_uri + cancel_path, "cancel-body", safety="INCLUDING_UNKNOWN_COMPLETION", attempts=2)
            http_state.wait_path(cancel_path)
            java(endpoint, "cancel", remote_cancel)
            cancel_state = wait_state(endpoint, remote_cancel, {"UNKNOWN_COMPLETION"})
            assert cancel_state["attempts"] == 1 and "CANCELLED_AFTER_SUBMISSION" in cancel_state["failure"]

            # Release succeeds for success, failure, cancellation and unknown completion.
            release_and_assert(endpoint, data, process_ok)
            release_and_assert(endpoint, data, failure_work)
            release_and_assert(endpoint, data, process_cancel)
            release_and_assert(endpoint, data, uncertain)
        finally:
            stop_kernel(kernel, log)

        # Late-bound credential reaches target but raw secret never becomes Kernel durable/log/inspection data.
        sentinel = "MADRE_LCR2_SECRET_6f4dbf7044514eb68731"
        secret_env = "MADRE_LCR2_TEST_TOKEN"
        kernel, endpoint, secret_data, log, secret_log = start_kernel(root, "secret", extra_env={secret_env: sentinel})
        try:
            secret_path = "/secret"
            secret_work = submit_http(endpoint, "secret-http", base_uri + secret_path, "non-secret-body", target="secret-target",
                                      headers=(("literal", "X-Madre-Literal", "literal-ok"), ("env", "Authorization", secret_env, "Bearer ", "")))
            secret_state = wait_state(endpoint, secret_work, {"SUCCEEDED"})
            request = http_state.wait_path(secret_path)[0]
            assert request["headers"].get("X-Madre-Literal") == "literal-ok"
            assert request["headers"].get("Authorization") == "Bearer " + sentinel
            assert sentinel not in secret_state["raw"]
            time.sleep(0.1)
            found = contains_bytes(secret_data, sentinel.encode())
            if found is not None:
                raise AssertionError(f"late-bound secret persisted in Kernel data before release: {found}")
            release_and_assert(endpoint, secret_data, secret_work)
        finally:
            stop_kernel(kernel, log)
        found_after_stop = contains_bytes(secret_data, sentinel.encode())
        if found_after_stop is not None:
            raise AssertionError(f"late-bound secret persisted in Kernel data after stop: {found_after_stop}")
        if sentinel.encode() in secret_log.read_bytes():
            raise AssertionError("late-bound secret appeared in Kernel ordinary log")

        # Restart during active HTTP remains unknown completion, not definite failure/retry under DEFINITE_FAILURES.
        restart_data = root / "restart-data"
        restart_path = "/restart-active"
        kernel, endpoint, _, log, _ = start_kernel(root, "restart-a", restart_data)
        restart_work = submit_http(endpoint, "restart-http", base_uri + restart_path, "restart-body", safety="DEFINITE_FAILURES", attempts=2)
        http_state.wait_path(restart_path)
        stop_kernel(kernel, log, hard=True)
        kernel, endpoint, _, log, _ = start_kernel(root, "restart-b", restart_data)
        try:
            restart_state = wait_state(endpoint, restart_work, {"UNKNOWN_COMPLETION"})
            assert restart_state["attempts"] == 1 and restart_state["attempt_state"] == "UNKNOWN_COMPLETION"
            release_and_assert(endpoint, restart_data, restart_work)
        finally:
            stop_kernel(kernel, log)

        # Process restart semantics remain conservative too.
        process_restart_data = root / "process-restart-data"
        marker = root / "process-restart.marker"
        kernel, endpoint, _, log, _ = start_kernel(root, "process-restart-a", process_restart_data)
        process_restart = submit_process(endpoint, "process-restart", FIXTURE, "process-restart-body", safety="DEFINITE_FAILURES", attempts=2,
                                         process_args=("--marker", str(marker), "--delay-ms", "1400"))
        deadline = time.time() + 4
        while time.time() < deadline and not marker.exists():
            time.sleep(0.02)
        assert marker.exists()
        stop_kernel(kernel, log, hard=True)
        kernel, endpoint, _, log, _ = start_kernel(root, "process-restart-b", process_restart_data)
        try:
            state = wait_state(endpoint, process_restart, {"UNKNOWN_COMPLETION"})
            assert state["attempts"] == 1
            time.sleep(1.5)
            assert len(marker.read_text().splitlines()) == 1
            release_and_assert(endpoint, process_restart_data, process_restart)
        finally:
            stop_kernel(kernel, log)

    print("LCR2 acceptance passed")
finally:
    server.shutdown(); server.server_close()
    server2.shutdown(); server2.server_close()
