"""Typed security-object construction helpers for Module developers."""

from __future__ import annotations

from madre.security import (
    ActorSecurityValues,
    OperationSecurityValues,
    OrdinarySecurityLevel,
    SecurityContext,
    SecurityObject,
)


def actor_security(
    *,
    subject_id: str,
    subject_kind: str,
    trust: OrdinarySecurityLevel,
    isolation: OrdinarySecurityLevel,
) -> SecurityObject:
    if subject_kind not in {"module", "agent", "endpoint"}:
        raise ValueError("actor security applies only to Module, Agent, or endpoint subjects")
    return SecurityObject.issue(
        subject_id=subject_id,
        subject_kind=subject_kind,  # type: ignore[arg-type]
        values=ActorSecurityValues(trust=trust, isolation=isolation),
    )


def operation_security(
    *,
    operation_id: str,
    risk: OrdinarySecurityLevel,
    autonomy: OrdinarySecurityLevel,
) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=operation_id,
        subject_kind="operation",
        values=OperationSecurityValues(risk=risk, autonomy=autonomy),
    )


def security_context(*objects: SecurityObject) -> SecurityContext:
    if not objects:
        raise ValueError("a carried SecurityContext requires at least one SecurityObject")
    return SecurityContext(objects=objects)
