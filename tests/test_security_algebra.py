from __future__ import annotations

import pytest
from pydantic import ValidationError

from madre_sdk import (
    Autonomy,
    Control,
    Disclosure,
    EffectExecution,
    EffectProfile,
    IdentityKind,
    Integrity,
    Material,
    MaterialContract,
    Privacy,
    Risk,
    ScopeIdentity,
    SecurityMismatch,
    SecurityScope,
    SecuritySurface,
    Sensitivity,
)


def identity(
    name: str,
    kind: IdentityKind = IdentityKind.SURFACE,
) -> ScopeIdentity:
    return ScopeIdentity(kind=kind, owner="module", name=name)


def scope(
    name: str,
    *,
    sensitivity: Sensitivity | None = None,
    privacy: Privacy | None = None,
    integrity: Integrity | None = None,
) -> SecurityScope:
    return SecurityScope(
        identity=identity(name),
        sensitivity=sensitivity,
        privacy=privacy,
        integrity=integrity,
    )


def profile(
    risk: Risk = Risk.R4,
    autonomy: Autonomy = Autonomy.A4,
) -> EffectProfile:
    operation = identity("operation", IdentityKind.OPERATION)
    return EffectProfile(
        identity=identity(f"{operation.name}.profile", IdentityKind.EFFECT_PROFILE),
        operation=operation,
        risk=risk,
        autonomy=autonomy,
    )


def test_surfaces_are_order_independent_composites_of_applicable_facets() -> None:
    low = scope("low", sensitivity=Sensitivity.S2)
    high = scope("high", sensitivity=Sensitivity.S5)
    secret = scope("secret", privacy=Privacy.SECRET)
    local_private = scope("local-private", privacy=Privacy.LOCAL_PRIVATE)
    causal = scope("causal", integrity=Integrity.I4)

    first = SecuritySurface.compose(low, high, secret, local_private, causal)
    second = SecuritySurface.compose(causal, local_private, secret, high, low)

    assert first == second
    assert first.sensitivity is Sensitivity.S5
    assert first.privacy is Privacy.LOCAL_PRIVATE
    assert first.integrity is Integrity.I4
    assert first.including(high) == first


def test_disclosure_addition_cannot_create_or_mutate_an_invalid_composite() -> None:
    initial = Disclosure(
        sources=SecuritySurface.compose(scope("source", sensitivity=Sensitivity.S2)),
        observers=SecuritySurface.compose(scope("observer", privacy=Privacy.SECRET)),
    )
    expanded = initial.including_source(scope("secret-source", sensitivity=Sensitivity.S5))

    with pytest.raises(SecurityMismatch, match="S5 exceeds LOCAL_PRIVATE"):
        expanded.including_observer(scope("narrower-observer", privacy=Privacy.LOCAL_PRIVATE))

    assert initial.sensitivity is Sensitivity.S2
    assert initial.privacy is Privacy.SECRET
    assert expanded.sensitivity is Sensitivity.S5
    assert expanded.privacy is Privacy.SECRET


def test_unknown_privacy_is_a_value_and_none_is_not_applicable() -> None:
    observer = SecuritySurface.compose(
        scope("unknown", privacy=Privacy.UNKNOWN),
        scope("executor-only", integrity=Integrity.I5),
    )
    assert observer.privacy is Privacy.UNKNOWN

    with pytest.raises(SecurityMismatch, match="S3 exceeds UNKNOWN"):
        Disclosure(
            sources=SecuritySurface.compose(scope("source", sensitivity=Sensitivity.S3)),
            observers=observer,
        )


def test_live_user_autonomy_is_not_a_disclosure_exception() -> None:
    live_action = profile(risk=Risk.R5, autonomy=Autonomy.A1)
    assert Control(profile=live_action).controller_integrity is Integrity.I5

    with pytest.raises(SecurityMismatch, match="S5 exceeds PUBLIC"):
        Disclosure(
            sources=SecuritySurface.compose(scope("source", sensitivity=Sensitivity.S5)),
            observers=SecuritySurface.compose(scope("observer", privacy=Privacy.PUBLIC)),
        )


def test_control_and_effect_execution_use_only_their_exact_relations() -> None:
    no_machine_controller = Control(profile=profile(Risk.R5, Autonomy.A1))
    assert no_machine_controller.demand == 1
    assert no_machine_controller.controller_integrity is Integrity.I5

    with pytest.raises(SecurityMismatch, match="demand 4 exceeds I3"):
        Control(
            profile=profile(Risk.R4, Autonomy.A4),
            controllers=SecuritySurface.compose(scope("controller", integrity=Integrity.I3)),
        )

    execution = EffectExecution(
        profile=profile(Risk.R4, Autonomy.A5),
        executors=SecuritySurface.compose(
            scope("executor-a", integrity=Integrity.I5),
            scope("executor-b", integrity=Integrity.I4),
        ),
    )
    assert execution.executor_integrity is Integrity.I4

    with pytest.raises(SecurityMismatch, match="R5 exceeds I4"):
        EffectExecution(
            profile=profile(Risk.R5, Autonomy.A1),
            executors=execution.executors,
        )

    with pytest.raises(ValidationError):
        SecuritySurface(scopes=())


def test_adaptation_creates_independent_material_instead_of_lineage() -> None:
    contract = MaterialContract(
        identity=identity("text-contract", IdentityKind.MATERIAL_CONTRACT),
        media_type="text/plain",
    )
    original_identity = identity("secret", IdentityKind.MATERIAL)
    original = Material[str](
        identity=original_identity,
        contract=contract,
        payload="owner secret",
        security=SecurityScope(
            identity=original_identity,
            sensitivity=Sensitivity.S5,
        ),
    )

    lowered = []
    for name, level in (
        ("tokenized", Sensitivity.S4),
        ("anonymized", Sensitivity.S3),
        ("minimized", Sensitivity.S2),
    ):
        material_identity = identity(name, IdentityKind.MATERIAL)
        lowered.append(
            Material[str](
                identity=material_identity,
                contract=contract,
                payload=name,
                security=SecurityScope(
                    identity=material_identity,
                    sensitivity=level,
                ),
            )
        )

    assert original.security.sensitivity is Sensitivity.S5
    assert {material.identity for material in lowered}.isdisjoint({original.identity})
    assert [material.security.sensitivity for material in lowered] == [
        Sensitivity.S4,
        Sensitivity.S3,
        Sensitivity.S2,
    ]


def test_ordered_carriers_are_nominally_distinct() -> None:
    assert Sensitivity.S1 != Privacy.PUBLIC
    assert Privacy.PUBLIC != Integrity.I1
    assert Integrity.I1 != Risk.R1
    assert Risk.R1 != Autonomy.A1
