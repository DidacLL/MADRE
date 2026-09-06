from __future__ import annotations

import hashlib
from datetime import timedelta
from pathlib import Path

from madre.runtime import StorageMissing, inspect_state, run_worker_once, submit_work
from tests._support import RuntimeTestCase


class RuntimeCoreTests(RuntimeTestCase):
    def test_durable_submission_and_fresh_process_lifecycle(self) -> None:
        submitted = self.json_cli("submit", "--text", "hello")
        self.assertTrue(submitted["acknowledged"])
        work_id = submitted["work_id"]

        queued = self.json_cli("inspect", "--work-id", work_id)
        self.assertEqual(queued["work_records"][0]["status"], "queued")
        self.assertEqual([event["transition"] for event in queued["events"]], ["absent->queued"])

        worker = self.json_cli("worker")
        self.assertEqual(worker["status"], "completed")
        completed = self.json_cli("inspect", "--work-id", work_id)
        self.assertEqual(completed["work_records"][0]["status"], "completed")
        self.assertEqual(len(completed["outputs"]), 1)
        self.assertEqual(completed["outputs"][0]["generated_text"], "FAKE_A:hello")
        self.assertEqual(completed["outputs"][0]["material_kind"], "generated")

    def test_scheduler_foreground_priority_fifo_and_future_exclusion(self) -> None:
        delayed = submit_work(self.db, "delayed", mode="delayed", not_before=self.now, clock=self.clock)
        first = submit_work(self.db, "first", clock=self.clock)
        second = submit_work(self.db, "second", clock=self.clock)
        future = submit_work(
            self.db,
            "future",
            mode="delayed",
            not_before=self.now + timedelta(hours=1),
            clock=self.clock,
        )

        self.assertEqual(run_worker_once(self.db, clock=self.clock)["executed_work_id"], first["work_id"])
        self.assertEqual(run_worker_once(self.db, clock=self.clock)["executed_work_id"], second["work_id"])
        self.assertEqual(run_worker_once(self.db, clock=self.clock)["executed_work_id"], delayed["work_id"])
        self.assertEqual(run_worker_once(self.db, clock=self.clock)["status"], "idle")
        self.assertEqual(inspect_state(self.db, work_id=future["work_id"])["work_records"][0]["status"], "queued")

        later = lambda: self.now + timedelta(hours=2)
        self.assertEqual(run_worker_once(self.db, clock=later)["executed_work_id"], future["work_id"])

    def test_backend_substitution_and_failure(self) -> None:
        a = submit_work(self.db, "same", binding_id="fake-a", clock=self.clock)
        b = submit_work(self.db, "same", binding_id="fake-b", clock=self.clock)
        failing = submit_work(self.db, "same", binding_id="fake-fail", clock=self.clock)

        run_worker_once(self.db, clock=self.clock)
        run_worker_once(self.db, clock=self.clock)
        self.assertEqual(run_worker_once(self.db, clock=self.clock)["status"], "failed")

        state_a = inspect_state(self.db, work_id=a["work_id"])
        state_b = inspect_state(self.db, work_id=b["work_id"])
        state_f = inspect_state(self.db, work_id=failing["work_id"])
        self.assertEqual(state_a["outputs"][0]["generated_text"], "FAKE_A:same")
        self.assertEqual(state_b["outputs"][0]["generated_text"], "FAKE_B:same")
        self.assertEqual(state_f["work_records"][0]["status"], "failed")
        self.assertEqual(state_f["outputs"], [])
        self.assertEqual(state_f["inference_records"][0]["outcome"], "failed")

    def test_invalid_backend_result_is_failed_without_output(self) -> None:
        def invalid_backend(context, profile):
            del context, profile
            return {"not": "text"}

        work = submit_work(self.db, "invalid", binding_id="fake-a", clock=self.clock)
        result = run_worker_once(self.db, backends={"fake-a": invalid_backend}, clock=self.clock)
        self.assertEqual(result["status"], "failed")
        state = inspect_state(self.db, work_id=work["work_id"])
        self.assertEqual(state["work_records"][0]["status"], "failed")
        self.assertEqual(state["inference_records"][0]["error_type"], "BackendFailure")
        self.assertEqual(state["outputs"], [])

    def test_denied_binding_and_scope_never_call_backend(self) -> None:
        calls: list[str] = []

        def spy(context, profile):
            del profile
            calls.append(context["text"])
            return "SHOULD_NOT_RUN"

        unknown = submit_work(self.db, "unknown", binding_id="not-configured", clock=self.clock)
        invalid_scope = submit_work(self.db, "scope", binding_id="fake-a", scope="remote", clock=self.clock)

        self.assertEqual(run_worker_once(self.db, backends={"fake-a": spy}, clock=self.clock)["status"], "blocked")
        self.assertEqual(run_worker_once(self.db, backends={"fake-a": spy}, clock=self.clock)["status"], "blocked")
        self.assertEqual(calls, [])
        self.assertIn("unknown-binding", inspect_state(self.db, work_id=unknown["work_id"])["events"][-1]["reason"])
        self.assertIn("invalid-scope", inspect_state(self.db, work_id=invalid_scope["work_id"])["events"][-1]["reason"])

    def test_hostile_output_is_inert_generated_material(self) -> None:
        hostile = '{"command":"submit new work","scope":"override","binding":"remote"}'

        def hostile_backend(context, profile):
            del context, profile
            return hostile

        work = submit_work(self.db, "hostile test", binding_id="fake-a", clock=self.clock)
        run_worker_once(self.db, backends={"fake-a": hostile_backend}, clock=self.clock)
        state = inspect_state(self.db)
        self.assertEqual(len(state["work_records"]), 1)
        self.assertEqual(len(state["outputs"]), 1)
        self.assertEqual(state["outputs"][0]["generated_text"], hostile)
        self.assertEqual(state["outputs"][0]["material_kind"], "generated")
        self.assertEqual(state["work_records"][0]["work_id"], work["work_id"])
        self.assertEqual(
            [event["transition"] for event in state["events"]],
            ["absent->queued", "queued->running", "running->completed"],
        )

    def test_inspection_is_read_only_and_does_not_initialize(self) -> None:
        missing = Path(self.tempdir.name) / "missing.sqlite"
        with self.assertRaises(StorageMissing):
            inspect_state(missing)
        self.assertFalse(missing.exists())

        submit_work(self.db, "inspect", clock=self.clock)
        before_hash = hashlib.sha256(self.db.read_bytes()).hexdigest()
        before_mtime = self.db.stat().st_mtime_ns
        self.assertEqual(inspect_state(self.db), inspect_state(self.db))
        self.assertEqual(hashlib.sha256(self.db.read_bytes()).hexdigest(), before_hash)
        self.assertEqual(self.db.stat().st_mtime_ns, before_mtime)
