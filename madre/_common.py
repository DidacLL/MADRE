from __future__ import annotations

import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

MODULE_ID = "module:runtime-first-slice"
AGENT_ID = "agent:runtime-first-slice-model"
PLAN_REF = "plan:single-model-action"
LOCAL_SCOPE = "local-only"
PROFILE_ID = "profile:deterministic-v1"


class RuntimeSliceError(Exception):
    """Base error for the bounded runtime slice."""


class StorageMissing(RuntimeSliceError):
    """Raised when read-only inspection targets absent/uninitialized storage."""


class BusyWorker(RuntimeSliceError):
    """Raised when another worker owns the database worker lock."""


class BackendFailure(RuntimeSliceError):
    """Typed deterministic inference failure."""


class StorageInvariantError(RuntimeSliceError):
    """Raised when persisted state violates the slice lifecycle contract."""


def canonical_path(db_path: str | os.PathLike[str]) -> Path:
    return Path(db_path).expanduser().resolve(strict=False)


def utc_now(clock: Callable[[], datetime] | None = None) -> datetime:
    value = clock() if clock is not None else datetime.now(timezone.utc)
    if value.tzinfo is None:
        raise ValueError("clock must return a timezone-aware datetime")
    return value.astimezone(timezone.utc)


def format_utc(value: datetime) -> str:
    if value.tzinfo is None:
        raise ValueError("UTC timestamps must be timezone-aware")
    return (
        value.astimezone(timezone.utc)
        .isoformat(timespec="microseconds")
        .replace("+00:00", "Z")
    )


def parse_utc(value: str) -> datetime:
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ValueError(f"invalid ISO-8601 timestamp: {value}") from exc
    if parsed.tzinfo is None:
        raise ValueError("not_before must include a UTC offset or Z suffix")
    return parsed.astimezone(timezone.utc)
