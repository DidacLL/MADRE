from __future__ import annotations

import sqlite3
import uuid

from ._common import LOCAL_SCOPE, PROFILE_ID, StorageInvariantError
from ._storage import transaction

BINDINGS = {
    "fake-a": ("fake-a", PROFILE_ID),
    "fake-b": ("fake-b", PROFILE_ID),
    "fake-fail": ("fake-fail", PROFILE_ID),
}


def recover_running(connection: sqlite3.Connection, timestamp: str) -> set[str]:
    rows = connection.execute(
        """
        SELECT work_id, current_attempt_id
        FROM work_records
        WHERE status = 'running'
        ORDER BY submission_seq
        """
    ).fetchall()
    if not rows:
        return set()

    recovered: set[str] = set()
    with transaction(connection):
        for row in rows:
            work_id = row["work_id"]
            attempt_id = row["current_attempt_id"]
            if not attempt_id:
                raise StorageInvariantError(f"running work has no attempt: {work_id}")
            attempt = connection.execute(
                """
                SELECT outcome, output_ref
                FROM inference_records
                WHERE attempt_id = ? AND work_id = ?
                """,
                (attempt_id, work_id),
            ).fetchone()
            if attempt is None or attempt["outcome"] != "running":
                raise StorageInvariantError(f"running attempt evidence is inconsistent: {work_id}")
            if attempt["output_ref"] is not None:
                raise StorageInvariantError(f"running attempt already references output: {work_id}")

            connection.execute(
                """
                UPDATE inference_records
                SET outcome = 'interrupted', finished_at = ?,
                    error_type = 'ProcessInterrupted',
                    error_message = 'backend outcome unknown after process interruption'
                WHERE attempt_id = ?
                """,
                (timestamp, attempt_id),
            )
            connection.execute(
                """
                UPDATE work_records
                SET status = 'recovered', recovery_reason = 'process_interrupted', updated_at = ?
                WHERE work_id = ?
                """,
                (timestamp, work_id),
            )
            connection.execute(
                """
                INSERT INTO runtime_events (
                    work_id, attempt_id, transition, reason, timestamp
                ) VALUES (?, ?, 'running->recovered', 'process_interrupted', ?)
                """,
                (work_id, attempt_id, timestamp),
            )
            recovered.add(work_id)
    return recovered


def select_eligible(
    connection: sqlite3.Connection,
    timestamp: str,
    excluded_work_ids: set[str],
) -> sqlite3.Row | None:
    rows = connection.execute(
        """
        SELECT *
        FROM work_records
        WHERE status IN ('queued', 'recovered')
          AND not_before <= ?
        ORDER BY
            CASE mode WHEN 'foreground' THEN 0 ELSE 1 END,
            submission_seq ASC
        """,
        (timestamp,),
    ).fetchall()
    for row in rows:
        if row["work_id"] not in excluded_work_ids:
            return row
    return None


def policy_decision(scope: str, binding_id: str) -> tuple[bool, str, str | None, str | None]:
    if scope != LOCAL_SCOPE:
        return False, f"policy:invalid-scope:{scope}", None, None
    binding = BINDINGS.get(binding_id)
    if binding is None:
        return False, f"policy:unknown-binding:{binding_id}", None, None
    backend_id, profile_id = binding
    return True, "policy:allowed-local-fake", backend_id, profile_id


def _record_requeue_if_recovered(
    connection: sqlite3.Connection,
    work: sqlite3.Row,
    timestamp: str,
) -> None:
    if work["status"] != "recovered":
        return
    connection.execute(
        "UPDATE work_records SET status = 'queued', updated_at = ? WHERE work_id = ?",
        (timestamp, work["work_id"]),
    )
    connection.execute(
        """
        INSERT INTO runtime_events (
            work_id, attempt_id, transition, reason, timestamp
        ) VALUES (?, ?, 'recovered->queued', 'explicit_worker_retry', ?)
        """,
        (work["work_id"], work["current_attempt_id"], timestamp),
    )


def record_blocked(
    connection: sqlite3.Connection,
    work: sqlite3.Row,
    reason: str,
    timestamp: str,
) -> None:
    with transaction(connection):
        _record_requeue_if_recovered(connection, work, timestamp)
        connection.execute(
            "UPDATE work_records SET status = 'blocked', updated_at = ? WHERE work_id = ?",
            (timestamp, work["work_id"]),
        )
        connection.execute(
            """
            INSERT INTO runtime_events (
                work_id, attempt_id, transition, reason, timestamp
            ) VALUES (?, NULL, 'queued->blocked', ?, ?)
            """,
            (work["work_id"], reason, timestamp),
        )


def record_running(
    connection: sqlite3.Connection,
    work: sqlite3.Row,
    backend_id: str,
    profile_id: str,
    policy_reason: str,
    timestamp: str,
) -> str:
    attempt_id = f"attempt-{uuid.uuid4().hex}"
    with transaction(connection):
        _record_requeue_if_recovered(connection, work, timestamp)
        connection.execute(
            """
            INSERT INTO inference_records (
                attempt_id, work_id, backend_id, profile_id, policy_reason,
                started_at, outcome
            ) VALUES (?, ?, ?, ?, ?, ?, 'running')
            """,
            (attempt_id, work["work_id"], backend_id, profile_id, policy_reason, timestamp),
        )
        connection.execute(
            """
            UPDATE work_records
            SET status = 'running', current_attempt_id = ?,
                attempt_count = attempt_count + 1,
                recovery_reason = NULL, updated_at = ?
            WHERE work_id = ?
            """,
            (attempt_id, timestamp, work["work_id"]),
        )
        connection.execute(
            """
            INSERT INTO runtime_events (
                work_id, attempt_id, transition, reason, timestamp
            ) VALUES (?, ?, 'queued->running', ?, ?)
            """,
            (work["work_id"], attempt_id, policy_reason, timestamp),
        )
    return attempt_id


def record_failed(
    connection: sqlite3.Connection,
    work_id: str,
    attempt_id: str,
    error: Exception,
    timestamp: str,
) -> None:
    error_type = type(error).__name__
    error_message = str(error)
    with transaction(connection):
        connection.execute(
            """
            UPDATE inference_records
            SET outcome = 'failed', finished_at = ?, error_type = ?, error_message = ?
            WHERE attempt_id = ?
            """,
            (timestamp, error_type, error_message, attempt_id),
        )
        connection.execute(
            "UPDATE work_records SET status = 'failed', updated_at = ? WHERE work_id = ?",
            (timestamp, work_id),
        )
        connection.execute(
            """
            INSERT INTO runtime_events (
                work_id, attempt_id, transition, reason, timestamp
            ) VALUES (?, ?, 'running->failed', ?, ?)
            """,
            (work_id, attempt_id, f"backend:{error_type}:{error_message}", timestamp),
        )


def record_completed(
    connection: sqlite3.Connection,
    work_id: str,
    attempt_id: str,
    generated_text: str,
    timestamp: str,
) -> str:
    output_id = f"output-{uuid.uuid4().hex}"
    with transaction(connection):
        connection.execute(
            """
            INSERT INTO inference_outputs (
                output_id, attempt_id, work_id, generated_text, created_at
            ) VALUES (?, ?, ?, ?, ?)
            """,
            (output_id, attempt_id, work_id, generated_text, timestamp),
        )
        connection.execute(
            """
            UPDATE inference_records
            SET outcome = 'succeeded', finished_at = ?, output_ref = ?
            WHERE attempt_id = ?
            """,
            (timestamp, output_id, attempt_id),
        )
        connection.execute(
            "UPDATE work_records SET status = 'completed', updated_at = ? WHERE work_id = ?",
            (timestamp, work_id),
        )
        connection.execute(
            """
            INSERT INTO runtime_events (
                work_id, attempt_id, transition, reason, timestamp
            ) VALUES (?, ?, 'running->completed', 'inference_committed', ?)
            """,
            (work_id, attempt_id, timestamp),
        )
    return output_id
