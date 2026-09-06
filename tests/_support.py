from __future__ import annotations

import json
import queue
import subprocess
import sys
import tempfile
import threading
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "tests" / "_process_probe.py"


class RuntimeTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tempdir.cleanup)
        self.db = Path(self.tempdir.name) / "runtime.sqlite"
        self.now = datetime(2026, 9, 6, 12, 0, tzinfo=timezone.utc)
        self.clock = lambda: self.now

    def cli(self, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        completed = subprocess.run(
            [sys.executable, "-m", "madre", "--db", str(self.db), *args],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if check and completed.returncode != 0:
            self.fail(
                f"CLI failed ({completed.returncode}): {' '.join(args)}\n"
                f"stdout={completed.stdout}\nstderr={completed.stderr}"
            )
        return completed

    def start_probe(self, phase: str) -> subprocess.Popen[str]:
        process = subprocess.Popen(
            [sys.executable, str(PROBE), phase, str(self.db)],
            cwd=ROOT,
            text=True,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=1,
        )
        self.addCleanup(self.cleanup_process, process)
        return process

    @staticmethod
    def cleanup_process(process: subprocess.Popen[str]) -> None:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=5)
        for stream in (process.stdin, process.stdout, process.stderr):
            if stream is not None and not stream.closed:
                stream.close()

    def read_handshake(self, process: subprocess.Popen[str]) -> str:
        assert process.stdout is not None
        messages: queue.Queue[str] = queue.Queue()

        def reader() -> None:
            messages.put(process.stdout.readline())

        threading.Thread(target=reader, daemon=True).start()
        try:
            line = messages.get(timeout=5)
        except queue.Empty:
            stderr = ""
            if process.stderr is not None and process.poll() is not None:
                stderr = process.stderr.read()
            self.fail(f"probe handshake timeout; returncode={process.poll()} stderr={stderr}")
        if not line:
            stderr = process.stderr.read() if process.stderr is not None else ""
            self.fail(f"probe exited before handshake; returncode={process.poll()} stderr={stderr}")
        return line.strip()

    @staticmethod
    def kill(process: subprocess.Popen[str]) -> None:
        process.kill()
        process.wait(timeout=5)

    def json_cli(self, *args: str, check: bool = True) -> dict[str, object]:
        completed = self.cli(*args, check=check)
        stream = completed.stdout if completed.returncode == 0 else completed.stderr
        return json.loads(stream)
