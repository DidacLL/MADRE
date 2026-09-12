"""Typed construction helpers for MADRE security scopes and relations."""

from __future__ import annotations

from madre.security import (
    Autonomy,
    CompositionResult,
    ControlNormalForm,
    DirectUserAction,
    DisclosureNormalForm,
    EffectExecutionNormalForm,
    EffectProfile,
    Integrity,
    OperationReference,
    Privacy,
    Risk,
    ScopeBinding,
    SecurityObject,
    SecurityScopeRef,
    Sensitivity,
)


def material_security(
    *,
    owner_module_id: str,
    scope_id: str,
    sensitivity: Sensitivity,
    integrity: Integrity | None = None,
    publication_revision: str = "1",
    scope_revision: str = "1",
    content_digest: str | None = None,
) -> SecurityObject:
    """Issue an exact material scope."""
    return SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id=owner_module_id,
            scope_id=scope_id,
            publication_revision=publication_revision,
            scope_revision=scope_revision,
        ),
        sensitivity=sensitivity,
        integrity=integrity,
        binding=ScopeBinding(content_digest=content_digest),
    )


def observer_security(
    *,
    owner_module_id: str,
    scope_id: str,
    privacy: Privacy,
    publication_revision: str = "1",
    scope_revision: str = "1",
    contract_digest: str | None = None,
) -> SecurityObject:
    """Issue an exact privacy-bearing observation boundary."""
    return SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id=owner_module_id,
            scope_id=scope_id,
            publication_revision=publication_revision,
            scope_revision=scope_revision,
        ),
        privacy=privacy,
        binding=ScopeBinding(contract_digest=contract_digest),
    )


def participant_security(
    *,
    owner_module_id: str,
    scope_id: str,
    privacy: Privacy,
    sensitivity: Sensitivity | None = None,
    integrity: Integrity | None = None,
    publication_revision: str = "1",
    scope_revision: str = "1",
    contract_digest: str | None = None,
) -> SecurityObject:
    """Issue an exact Module/Agent/endpoint scope with only its applicable facets."""
    return SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id=owner_module_id,
            scope_id=scope_id,
            publication_revision=publication_revision,
            scope_revision=scope_revision,
        ),
        sensitivity=sensitivity,
        privacy=privacy,
        integrity=integrity,
        binding=ScopeBinding(contract_digest=contract_digest),
    )


def executor_security(
    *,
    owner_module_id: str,
    scope_id: str,
    integrity: Integrity,
    publication_revision: str = "1",
    scope_revision: str = "1",
    contract_digest: str | None = None,
) -> SecurityObject:
    """Issue an exact Integrity-bearing controller or executor scope."""
    return SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id=owner_module_id,
            scope_id=scope_id,
            publication_revision=publication_revision,
            scope_revision=scope_revision,
        ),
        integrity=integrity,
        binding=ScopeBinding(contract_digest=contract_digest),
    )


def effect_profile(
    *,
    owner_module_id: str,
    operation_id: str,
    profile_id: str,
    risk: Risk,
    autonomy: Autonomy,
    publication_revision: str = "1",
    operation_revision: str = "1",
) -> EffectProfile:
    return EffectProfile.issue(
        id=profile_id,
        operation=OperationReference(
            module_id=owner_module_id,
            operation_id=operation_id,
            publication_revision=publication_revision,
            operation_revision=operation_revision,
        ),
        risk=risk,
        autonomy=autonomy,
    )


def compose_disclosure(
    *sources: SecurityObject,
    observers: tuple[SecurityObject, ...],
    direct_user_action: DirectUserAction | None = None,
) -> CompositionResult[DisclosureNormalForm]:
    return DisclosureNormalForm.compose(
        sources=sources,
        observers=observers,
        direct_user_action=direct_user_action,
    )


def compose_control(
    profile: EffectProfile, *controllers: SecurityObject
) -> CompositionResult[ControlNormalForm]:
    return ControlNormalForm.compose(profile=profile, controllers=controllers)


def compose_effect_execution(
    profile: EffectProfile, *executors: SecurityObject
) -> CompositionResult[EffectExecutionNormalForm]:
    return EffectExecutionNormalForm.compose(profile=profile, executors=executors)
