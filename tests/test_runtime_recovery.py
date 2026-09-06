from __future__ import annotations

import json

from madre.runtime import inspect_state, run_worker_once, submit_work
from tests._support import RuntimeTestCase


class RuntimeRecoveryTests(RuntimeTestCase):
    def test_kill_before_submission_commit_leaves_no_work(self) -> None:
        process = self.start_probe("submit-before-commit")
        self.assertTrue(self.read_handshake(process).startswith("BEFORE_COMMIT work-"))
        self.kill(process)

        state = inspect_state(self.db)
        self.assertEqual(state["work_records"], [])
        self.assertEqual(state["events"], [])
        self.assertEqual(state["inference_records"], [])
        self.assertEqual(state["outputs"], [])

    def _assert_kill_recovery(self, phase: str, prefix: str) -> None:
        work = submit_work(self.db, phase, binding_id="fake-a")
        process = self.start_probe(phase)
        self.assertTrue(self.read_handshake(process).startswith(prefix))
        running = inspect_state(self.db, work_id=work["work_id"])
        self.assertEqual(running["work_records"][0]["status"], "running")
        self.assertEqual(running["inference_records"][0]["outcome"], "running")
        self.assertEqual(running["outputs"], [])
        first_attempt = running["inference_records"][0]["attempt_id"]

        self.kill(process)
        self.assertEqual(inspect_state(self.db, work_id=work["work_id"])["work_records"][0]["status"], "running")

        recovery_only = run_worker_once(self.db)
        self.assertEqual(recovery_only["status"], "idle")
        self.assertEqual(recovery_only["recovered"], [work["work_id"]])
        recovered = inspect_state(self.db, work_id=work["work_id"])
        self.assertEqual(recovered["work_records"][0]["status"], "recovered")
        self.assertEqual(recovered["inference_records"][0]["attempt_id"], first_attempt)
        self.assertEqual(recovered["inference_records"][0]["outcome"], "interrupted")
        self.assertIsNone(recovered["inference_records"][0]["output_ref"])
        self.assertEqual(recovered["outputs"], [])

        retry = run_worker_once(self.db)
        self.assertEqual(retry["status"], "completed")
        completed = inspect_state(self.db, work_id=work["work_id"])
        self.assertEqual(completed["work_records"][0]["status"], "completed")
        self.assertEqual(len(completed["inference_records"]), 2)
        self.assertEqual(
            {record["outcome"] for record in completed["inference_records"]},
            {"interrupted", "succeeded"},
        )
        self.assertNotEqual(
            completed["inference_records"][0]["attempt_id"],
            completed["inference_records"][1]["attempt_id"],
        )
        self.assertEqual(len(completed["outputs"]), 1)
        self.assertEqual(completed["outputs"][0]["generated_text"], f"FAKE_A:{phase}")
        self.assertEqual(
            [event["transition"] for event in completed["events"]],
            [
                "absent->queued",
                "queued->running",
                "running->recovered",
                "recovered->queued",
                "queued->running",
                "running->completed",
            ],
        )
        terminal = [
            event
            for event in completed["events"]
            if event["transition"] in {"running->completed", "running->failed", "queued->blocked"}
        ]
        self.assertEqual(len(terminal), 1)

    def test_process_kill_after_running_commit_recovers_then_retries(self) -> None:
        self._assert_kill_recovery("worker-running", "RUNNING ")

    def test_process_kill_after_inference_before_commit_recovers_then_retries(self) -> None:
        self._assert_kill_recovery("worker-inferred", "INFERRED ")

    def test_live_second_worker_reports_busy_without_mutation_and_lock_releases_on_death(self) -> None:
        work = submit_work(self.db, "locking")
        first = self.start_probe("worker-running")
        self.assertTrue(self.read_handshake(first).startswith("RUNNING "))
        before = inspect_state(self.db, work_id=work["work_id"])

        second = self.cli("worker", check=False)
        self.assertEqual(second.returncode, 3)
        self.assertEqual(json.loads(second.stderr)["status"], "busy")
        after = inspect_state(self.db, work_id=work["work_id"])
        self.assertEqual(before, after)

        self.kill(first)
        recovery = run_worker_once(self.db)
        self.assertEqual(recovery["recovered"], [work["work_id"]])
        self.assertEqual(recovery["status"], "idle")
