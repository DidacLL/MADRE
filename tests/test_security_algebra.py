from __future__ import annotations

from dataclasses import replace

import pytest

from madre_sdk import (
    Autonomy,
    DisplayName,
    EffectProfile,
    EffectProfileId,
    InputSurface,
    InputSurfaces,
    Integrity,
    Material,
    MaterialId,
    MaterialSet,
    MaterialType,
    MaterialTypeId,
    ModuleId,
    OperationCall,
    OperationDefinition,
    OperationId,
    OutputSurface,
    OutputSurfaces,
    Privacy,
    Purpose,
    ResponsibilitySurface,
    ResponsibilitySurfaces,
    Risk,
    Sensitivity,
    SurfaceId,
)


def test_role_carriers_accumulate_without_becoming_interchangeable() -> None:
    assert Sensitivity.maximum(Sensitivity.S2, Sensitivity.S5) is Sensitivity.S5
    assert Privacy.minimum(Privacy.P5, Privacy.P3) is Privacy.P3
    assert Integrity.minimum(Integrity.I5, Integrity.I3) is Integrity.I3
    assert Privacy.UNKNOWN.rank == Privacy.P2.rank == 2
    assert Privacy.PUBLIC.rank == Privacy.P1.rank == 1
    differently_typed: tuple[object, object] = (Sensitivity.S2, Privacy.P2)
    assert type(differently_typed[0]) is not type(differently_typed[1])

    with pytest.raises(TypeError):
        Sensitivity.maximum(Sensitivity.S2, Privacy.P2)  # type: ignore[arg-type]


def test_information_composition_is_immutable_and_role_specific() -> None:
    module = ModuleId("composition")
    text_type = MaterialType[str](MaterialTypeId(module, "text"), "text/plain")
    initial = MaterialSet.of(
        Material(MaterialId(module, "minimized"), text_type, "safe", Sensitivity.S2)
    )
    expanded = initial.including(
        Material(MaterialId(module, "secret"), text_type, "secret", Sensitivity.S5)
    )
    private_input = InputSurface(
        SurfaceId(module, "private-input"),
        text_type.identity,
        Privacy.P5,
    )
    constrained_input = InputSurface(
        SurfaceId(module, "constrained-input"),
        text_type.identity,
        Privacy.P3,
    )

    assert private_input.compose(expanded) is expanded
    assert initial.sensitivity is Sensitivity.S2
    with pytest.raises(ValueError):
        constrained_input.compose(expanded)
    assert initial.sensitivity is Sensitivity.S2
    assert expanded.sensitivity is Sensitivity.S5
    assert not hasattr(private_input, "sensitivity")
    assert not hasattr(expanded.materials[0], "privacy")


def test_privacy_is_explicit_and_independent_of_physical_location() -> None:
    module = ModuleId("explicit-privacy")
    content = MaterialType[str](MaterialTypeId(module, "content"), "text/plain")
    remote_owner_controlled = InputSurface(
        SurfaceId(module, "remote-owner-controlled"),
        content.identity,
        Privacy.P4,
    )
    local_public_relay = InputSurface(
        SurfaceId(module, "local-public-relay"),
        content.identity,
        Privacy.PUBLIC,
    )

    assert remote_owner_controlled.privacy is Privacy.P4
    assert local_public_relay.privacy is Privacy.P1
    assert not hasattr(remote_owner_controlled, "location")


def test_operation_call_uses_only_its_profile_and_actual_responsibilities() -> None:
    module = ModuleId("bounded-operation")
    content = MaterialType[str](MaterialTypeId(module, "content"), "text/plain")
    operation_id = OperationId(module, "write")
    owner_profile = EffectProfile(
        EffectProfileId(operation_id, "owner-action"),
        Risk.R4,
        Autonomy.A1,
    )
    automated_profile = EffectProfile(
        EffectProfileId(operation_id, "automated"),
        Risk.R2,
        Autonomy.A4,
    )
    operation = OperationDefinition(
        operation_id,
        DisplayName("Write"),
        Purpose("Write one bounded output."),
        InputSurfaces.of(
            InputSurface(SurfaceId(module, "write-input"), content.identity, Privacy.P5)
        ),
        OutputSurfaces.of(
            OutputSurface(
                SurfaceId(module, "write-output"),
                content.identity,
                Sensitivity.S2,
            )
        ),
        (owner_profile, automated_profile),
    )
    materials = MaterialSet.of(
        Material(MaterialId(module, "input"), content, "value", Sensitivity.S2)
    )
    strong_realizer = ResponsibilitySurfaces.of(
        ResponsibilitySurface(SurfaceId(module, "strong-realizer"), Integrity.I5)
    )
    weak_realizer = ResponsibilitySurfaces.of(
        ResponsibilitySurface(SurfaceId(module, "weak-realizer"), Integrity.I1)
    )
    weak_cause = ResponsibilitySurfaces.of(
        ResponsibilitySurface(SurfaceId(module, "weak-cause"), Integrity.I1)
    )

    call = OperationCall(operation, owner_profile, materials, None, strong_realizer)
    assert call.profile.causal_demand == 1
    with pytest.raises(ValueError):
        replace(call, physical_realizers=weak_realizer)
    with pytest.raises(ValueError):
        OperationCall(operation, automated_profile, materials, weak_cause, strong_realizer)

    independent = OperationCall(operation, automated_profile, materials, None, strong_realizer)
    assert independent.profile.causal_demand == 2


def test_live_user_profile_does_not_change_information_composition() -> None:
    module = ModuleId("user-action")
    content = MaterialType[str](MaterialTypeId(module, "content"), "text/plain")
    secret = MaterialSet.of(
        Material(MaterialId(module, "secret"), content, "secret", Sensitivity.S5)
    )
    third_party = InputSurface(
        SurfaceId(module, "third-party"),
        content.identity,
        Privacy.UNKNOWN,
    )
    operation = OperationId(module, "owner-action")
    profile = EffectProfile(EffectProfileId(operation, "live"), Risk.R1, Autonomy.A1)

    assert profile.autonomy is Autonomy.A1
    with pytest.raises(ValueError):
        third_party.compose(secret)


def test_adapted_materials_are_independent_values() -> None:
    module = ModuleId("adaptation")
    content = MaterialType[str](MaterialTypeId(module, "content"), "text/plain")
    original = Material(MaterialId(module, "source"), content, "secret", Sensitivity.S5)
    tokenized = Material(MaterialId(module, "tokenized"), content, "token", Sensitivity.S4)
    anonymized = Material(MaterialId(module, "anonymized"), content, "group", Sensitivity.S3)
    minimized = Material(MaterialId(module, "minimized"), content, "fact", Sensitivity.S2)

    assert len({item.identity for item in (original, tokenized, anonymized, minimized)}) == 4
    assert original.sensitivity is Sensitivity.S5
    assert [item.sensitivity for item in (tokenized, anonymized, minimized)] == [
        Sensitivity.S4,
        Sensitivity.S3,
        Sensitivity.S2,
    ]
    assert not hasattr(minimized, "parent")
