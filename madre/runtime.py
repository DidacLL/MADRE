from __future__ import annotations

import os
import uuid
from datetime import datetime, timezone
from typing import Callable, Mapping

from ._common import (
    AGENT_ID,
    LOCAL_SCOPE,
    MODULE_ID,
    PLAN_REF,
    PROFILE_ID,
    BackendFailure,
    BusyWorker,
    RuntimeSliceError,
    StorageInvariantError,
    StorageMissing,
    canonical_path,
    format_utc,
    parse_utc,
    utc_now,
)
from ._journal import (
    policy_decision,
    record_blocked,
    record_completed,
    record_failed,
    record_running,
    recover_running,
    select_eligible,
)
from ._lock import worker_lock
from ._storage import (
    ensure_initialized_read_only,
    initialize_storage,
    read_only_connection,
    rows_as_dicts,
    transaction,
    writer_connection,
)


def fake_a(context: Mapping[str, str], profile: Mapping[str, str]) -> str:
    del profile
    return f"FAKE_A:{context['text']}"


def fake_b(context: Mapping[str, str], profile: Mapping[str, str]) -> str:
    del profile
    return f"FAKE_B:{context['text']}"


def fake_fail(context: Mapping[str, str], profile: Mapping[str, str]) -> str:
    del context, profile
    raise BackendFailure("deterministic fake failure")


DEFAULT_BACKENDS: Mapping[
    str, Callable[[Mapping[str, str], Mapping[str, str]], str]
] = {
    "fake-a": fake_a,
    "fake-b": fake_b,
    "fake-fail": fake_fail,
}


def submit_work(
    db_path: str | os.PathLike[str],
    text: str,
    *,
    mode: str = "foreground",
    not_before: str | datetime | None = None,
    binding_id: str = "fake-a",
    scope: str = LOCAL_SCOPE,
    request_id: str | None = None,
    source_ref: str | None = None,
    clock: Callable[[], datetime] | None = None,
    before_commit: Callable[[str], None] | None = None,
) -> dict[str, object]:
    if mode not in {"foreground", "delayed"}:
        raise ValueError("mode must be 'foreground' or 'delayed'")
    if not isinstance(text, str):
        raise ValueError("text must be a string")

    now = utc_now(clock)
    if mode == "foreground":
        if not_before is not None:
            raise ValueError("foreground work is immediately eligible and cannot set not_before")
        eligible_at = now
    else:
        if not_before is None:
            raise ValueError("delayed work requires not_before")
        eligible_at = parse_utc(not_before) if isinstance(not_before, str) else not_before
        if eligible_at.tzinfo is None:
            raise ValueError("not_before must be timezone-aware")
        eligible_at = eligible_at.astimezone(timezone.utc)

    work_id = f"work-{uuid.uuid4().hex}"
    request = request_id or f"request-{uuid.uuid4().hex}"
    source = source_ref or request
    context_ref = f"context:{work_id}"
    timestamp = format_utc(now)
    not_before_text = format_utc(eligible_at)
    path = canonical_path(db_path)

    connection = writer_connection(path)
    try:
        initialize_storage(connection)
        with transaction(connection):
            connection.execute(
                """
                INSERT INTO work_records (
                    work_id, request_id, module_id, agent_id, plan_ref,
                    mode, not_before, status, context_ref, context_text, source_ref,
                    scope, binding_id, submitted_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'queued', ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    work_id,
                    request,
                    MODULE_ID,
                    AGENT_ID,
                    PLAN_REF,
                    mode,
                    not_before_text,
                    context_ref,
                    text,
                    source,
                    scope,
                    binding_id,
                    timestamp,
                    timestamp,
                ),
            )
            connection.execute(
                """
                INSERT INTO runtime_events (
                    work_id, attempt_id, transition, reason, timestamp
                ) VALUES (?, NULL, 'absent->queued', 'submission_committed', ?)
                """,
                (work_id, timestamp),
            )
            if before_commit is not None:
                before_commit(work_id)
    finally:
        connection.close()

    return {
        "acknowledged": True,
        "work_id": work_id,
        "request_id": request,
        "status": "queued",
        "mode": mode,
        "not_before": not_before_text,
    }


def run_worker_once(
    db_path: str | os.PathLike[str],
    *,
    backends: Mapping[
        str, Callable[[Mapping[str, str], Mapping[str, str]], str]
    ] | None = None,
    clock: Callable[[], datetime] | None = None,
    hooks: Mapping[str, Callable[..., None]] | None = None,
) -> dict[str, object]:
    path = canonical_path(db_path)
    backend_registry = DEFAULT_BACKENDS if backends is None else backends
    hooks = {} if hooks is None else hooks

    with worker_lock(path):
        connection = writer_connection(path)
        try:
            initialize_storage(connection)
            timestamp = format_utc(utc_now(clock))
            recovered_now = recover_running(connection, timestamp)
            work = select_eligible(connection, timestamp, recovered_now)
            if work is None:
                return {
                    "status": "idle",
                    "recovered": sorted(recovered_now),
                    "executed_work_id": None,
                }

            allowed, policy_reason, backend_id, profile_id = policy_decision(
                work["scope"], work["binding_id"]
            )
            if not allowed:
                record_blocked(connection, work, policy_reason, timestamp)
                return {
                    "status": "blocked",
                    "recovered": sorted(recovered_now),
                    "executed_work_id": work["work_id"],
                    "reason": policy_reason,
                }

            assert backend_id is not None and profile_id is not None
            attempt_id = record_running(
                connection, work, backend_id, profile_id, policy_reason, timestamp
            )

            after_running = hooks.get("after_running_commit")
            if after_running is not None:
                after_running(work["work_id"], attempt_id)

            backend = backend_registry.get(backend_id)
            if backend is None:
                backend_error = BackendFailure(f"configured backend unavailable: {backend_id}")
                failed_at = format_utc(utc_now(clock))
                record_failed(connection, work["work_id"], attempt_id, backend_error, failed_at)
                return {
                    "status": "failed",
                    "recovered": sorted(recovered_now),
                    "executed_work_id": work["work_id"],
                    "attempt_id": attempt_id,
                    "error": str(backend_error),
                }

            context = {
                "text": work["context_text"],
                "source_ref": work["source_ref"],
                "request_id": work["request_id"],
                "scope": work["scope"],
            }
            profile = {"id": profile_id, "backend_id": backend_id}
            try:
                generated_text = backend(context, profile)
                if not isinstance(generated_text, str):
                    raise BackendFailure("backend returned non-text result")
            except Exception as exc:
                failed_at = format_utc(utc_now(clock))
                record_failed(connection, work["work_id"], attempt_id, exc, failed_at)
                return {
                    "status": "failed",
                    "recovered": sorted(recovered_now),
                    "executed_work_id": work["work_id"],
                    "attempt_id": attempt_id,
                    "error": str(exc),
                }

            after_backend = hooks.get("after_backend_return")
            if after_backend is not None:
                after_backend(work["work_id"], attempt_id, generated_text)

            completed_at = format_utc(utc_now(clock))
            output_id = record_completed(
                connection, work["work_id"], attempt_id, generated_text, completed_at
            )
            return {
                "status": "completed",
                "recovered": sorted(recovered_now),
                "executed_work_id": work["work_id"],
                "attempt_id": attempt_id,
                "output_id": output_id,
            }
        finally:
            connection.close()


def inspect_state(
    db_path: str | os.PathLike[str],
    *,
    work_id: str | None = None,
) -> dict[str, object]:
    path = canonical_path(db_path)
    connection = read_only_connection(path)
    try:
        ensure_initialized_read_only(connection)
        if work_id is None:
            work_rows = connection.execute(
                "SELECT * FROM work_records ORDER BY submission_seq"
            ).fetchall()
            event_rows = connection.execute(
                "SELECT * FROM runtime_events ORDER BY event_id"
            ).fetchall()
            inference_rows = connection.execute(
                "SELECT * FROM inference_records ORDER BY started_at, attempt_id"
            ).fetchall()
            output_rows = connection.execute(
                "SELECT * FROM inference_outputs ORDER BY created_at, output_id"
            ).fetchall()
        else:
            work_rows = connection.execute(
                "SELECT * FROM work_records WHERE work_id = ? ORDER BY submission_seq",
                (work_id,),
            ).fetchall()
            event_rows = connection.execute(
                "SELECT * FROM runtime_events WHERE work_id = ? ORDER BY event_id",
                (work_id,),
            ).fetchall()
            inference_rows = connection.execute(
                """
                SELECT * FROM inference_records
                WHERE work_id = ? ORDER BY started_at, attempt_id
                """,
                (work_id,),
            ).fetchall()
            output_rows = connection.execute(
                """
                SELECT * FROM inference_outputs
                WHERE work_id = ? ORDER BY created_at, output_id
                """,
                (work_id,),
            ).fetchall()

        outputs = rows_as_dicts(output_rows)
        for output in outputs:
            output["material_kind"] = "generated"

        return {
            "database": str(path),
            "work_records": rows_as_dicts(work_rows),
            "events": rows_as_dicts(event_rows),
            "inference_records": rows_as_dicts(inference_rows),
            "outputs": outputs,
        }
    finally:
        connection.close()


__all__ = [
    "AGENT_ID",
    "BackendFailure",
    "BusyWorker",
    "DEFAULT_BACKENDS",
    "LOCAL_SCOPE",
    "MODULE_ID",
    "PLAN_REF",
    "PROFILE_ID",
    "RuntimeSliceError",
    "StorageInvariantError",
    "StorageMissing",
    "fake_a",
    "fake_b",
    "fake_fail",
    "inspect_state",
    "run_worker_once",
    "submit_work",
]
