"""Typed construction helpers for MADRE Security Algebra V2."""

from __future__ import annotations

from madre.security import (
    BindingEvidence,
    BoundarySecurityValues,
    Control,
    Disclosure,
    EffectExecution,
    EffectProfile,
    EffectProfileSecurityValues,
    OperationReference,
    OrdinarySecurityLevel,
    ParticipantSecurityValues,
    RiskEnvelope,
    SecurityHistory,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
)


def participant_security(
    *,
    owner_module_id: str,
    subject_id: str,
    subject_kind: str,
    assurance: OrdinarySecurityLevel,
    publication_revision: str = "1",
    subject_revision: str = "1",
    binding_evidence: tuple[BindingEvidence, ...] = (),
) -> SecurityObject:
    if subject_kind not in {"module", "agent", "endpoint"}:
        raise ValueError("participant security applies only to Module, Agent, or endpoint subjects")
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id=owner_module_id,
            subject_kind=subject_kind,  # type: ignore[arg-type]
            publication_revision=publication_revision,
            local_id=subject_id,
            subject_revision=subject_revision,
        ),
        values=ParticipantSecurityValues(assurance=assurance),
        binding_evidence=binding_evidence,
    )


def effect_profile(
    *,
    owner_module_id: str,
    operation_id: str,
    profile_id: str,
    control_risk: OrdinarySecurityLevel,
    effect_risk: OrdinarySecurityLevel,
    autonomy: OrdinarySecurityLevel,
    assurance: OrdinarySecurityLevel,
    disclosure_boundaries: tuple[SecurityObject, ...] = (),
    controllers: tuple[SecurityObject, ...] = (),
    executors: tuple[SecurityObject, ...] = (),
    input_controls: bool = True,
    caller_controls: bool = True,
    publication_revision: str = "1",
    operation_revision: str = "1",
    binding_evidence: tuple[BindingEvidence, ...] = (),
) -> EffectProfile:
    operation = OperationReference(
        module_id=owner_module_id,
        operation_id=operation_id,
        publication_revision=publication_revision,
        operation_revision=operation_revision,
    )
    security = SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id=owner_module_id,
            subject_kind="effect_profile",
            publication_revision=publication_revision,
            local_id=f"{operation_id}/{profile_id}",
            subject_revision=operation_revision,
            parent_local_id=operation_id,
        ),
        values=EffectProfileSecurityValues(
            risk=RiskEnvelope(control_risk=control_risk, effect_risk=effect_risk),
            autonomy=autonomy,
            assurance=assurance,
            disclosure_boundary_ids=tuple(x.security_id for x in disclosure_boundaries),
            controller_security_ids=tuple(x.security_id for x in controllers),
            executor_security_ids=tuple(x.security_id for x in executors),
            input_controls=input_controls,
            caller_controls=caller_controls,
        ),
        binding_evidence=binding_evidence,
    )
    return EffectProfile(
        id=profile_id,
        operation=operation,
        security=security,
        participants=(*disclosure_boundaries, *controllers, *executors),
    )


def security_history(*objects: SecurityObject) -> SecurityHistory:
    return SecurityHistory(objects=objects)


def disclosure(material: SecurityObject, *privacy_path: SecurityObject) -> SecurityTransition:
    if not privacy_path:
        raise ValueError("Disclosure requires an actual privacy path")
    return SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=material.security_id,
                boundary_security_ids=tuple(item.security_id for item in privacy_path),
            ),
        )
    )


def effect_transition(
    *,
    profile: EffectProfile,
    controllers: tuple[SecurityObject, ...],
    executors: tuple[SecurityObject, ...],
    disclosures: tuple[Disclosure, ...] = (),
) -> SecurityTransition:
    return SecurityTransition.issue(
        disclosures=disclosures,
        control=Control(
            effect_profile_security_id=profile.security.security_id,
            controller_security_ids=tuple(item.security_id for item in controllers),
        ),
        effect_execution=EffectExecution(
            operation=profile.operation,
            effect_profile_security_id=profile.security.security_id,
            executor_security_ids=tuple(item.security_id for item in executors),
        ),
    )


def disclosure_boundary(
    *,
    owner_module_id: str,
    boundary_id: str,
    privacy_capacity: OrdinarySecurityLevel,
    publication_revision: str = "1",
    binding_evidence: tuple[BindingEvidence, ...] = (),
) -> SecurityObject:
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id=owner_module_id,
            subject_kind="disclosure_boundary",
            publication_revision=publication_revision,
            local_id=boundary_id,
        ),
        values=BoundarySecurityValues(privacy_capacity=privacy_capacity),
        binding_evidence=binding_evidence,
    )
