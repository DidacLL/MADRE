from __future__ import annotations

import pytest
from pydantic import ValidationError

from madre.security import (
    Autonomy,
    ControlNormalForm,
    DerivationEvidence,
    DirectUserAction,
    DirectUserInteraction,
    DisclosureNormalForm,
    EffectExecutionNormalForm,
    EffectProfile,
    Integrity,
    InvocationContext,
    OperationReference,
    OperationUse,
    Privacy,
    Risk,
    ScopeBinding,
    SecurityEvidence,
    SecurityObject,
    SecurityScopeRef,
    Sensitivity,
    audit_security_evidence,
)
from madre_sdk import Artifact, ContextBundle


def scope(
    name: str,
    *,
    sensitivity: Sensitivity | None = None,
    privacy: Privacy | None = None,
    integrity: Integrity | None = None,
    digest: str | None = None,
) -> SecurityObject:
    return SecurityObject.issue(
        scope=SecurityScopeRef(owner_module_id="owner", scope_id=name, publication_revision="1"),
        sensitivity=sensitivity,
        privacy=privacy,
        integrity=integrity,
        binding=ScopeBinding(content_digest=digest),
    )


def profile(name: str, risk: Risk, autonomy: Autonomy) -> EffectProfile:
    return EffectProfile.issue(
        id=name,
        operation=OperationReference(
            module_id="owner",
            operation_id="write",
            publication_revision="1",
        ),
        risk=risk,
        autonomy=autonomy,
    )


@pytest.mark.parametrize(
    ("sensitivity", "privacy", "accepted"),
    [
        (Sensitivity.S2, Privacy.UNKNOWN, True),
        (Sensitivity.S5, Privacy.UNKNOWN, False),
        (Sensitivity.S5, Privacy.SECRET, True),
    ],
)
def test_disclosure_law(sensitivity: Sensitivity, privacy: Privacy, accepted: bool) -> None:
    result = DisclosureNormalForm.compose(
        crossing_id="crossing",
        sources=(scope("material", sensitivity=sensitivity),),
        observers=(scope("observer", privacy=privacy),),
    )
    assert result.admissible is accepted


def test_rejected_candidate_does_not_poison_later_path() -> None:
    material = scope("material", sensitivity=Sensitivity.S5)
    denied = DisclosureNormalForm.compose(
        crossing_id="one",
        sources=(material,),
        observers=(scope("public", privacy=Privacy.PUBLIC),),
    )
    accepted = DisclosureNormalForm.compose(
        crossing_id="two",
        sources=(material,),
        observers=(scope("private", privacy=Privacy.SECRET),),
    )
    assert denied.accepted is None
    assert accepted.accepted is not None


def test_transform_creates_genuinely_new_lower_sensitivity_representation() -> None:
    source = Artifact.create(
        owner_module_id="owner",
        artifact_id="raw",
        payload={"secret": True},
        sensitivity=Sensitivity.S5,
    )
    module = scope("owner", privacy=Privacy.SECRET, integrity=Integrity.I5)
    invocation = InvocationContext(module=module)
    transformed = source.derive(
        invocation=invocation,
        artifact_id="aggregate",
        payload={"count": 1},
        sensitivity=Sensitivity.S2,
        transform=SecurityScopeRef(
            owner_module_id="owner",
            scope_id="aggregate-procedure",
            publication_revision="1",
        ),
    )
    result = DisclosureNormalForm.compose(
        crossing_id="transformed",
        sources=(transformed.security,),
        observers=(scope("unknown", privacy=Privacy.UNKNOWN),),
    )
    assert result.accepted is not None
    assert not audit_security_evidence(transformed.evidence)


@pytest.mark.parametrize("target", [Sensitivity.S2, Sensitivity.S3, Sensitivity.S5])
def test_module_transform_may_lower_preserve_or_raise(target: Sensitivity) -> None:
    source = Artifact.create(
        owner_module_id="owner", artifact_id="source", payload="x", sensitivity=Sensitivity.S3
    )
    invocation = InvocationContext(
        module=scope("owner", privacy=Privacy.SECRET, integrity=Integrity.I5)
    )
    transformed = source.derive(
        invocation=invocation,
        artifact_id=f"target-{target.value}",
        payload={"rank": target.value},
        sensitivity=target,
        transform=SecurityScopeRef(
            owner_module_id="owner", scope_id="transform", publication_revision="1"
        ),
    )
    assert transformed.security.sensitivity is target
    assert not audit_security_evidence(transformed.evidence)


def test_ordinary_lowering_is_impossible_and_selection_uses_retained_maximum() -> None:
    high = Artifact.create(
        owner_module_id="owner", artifact_id="high", payload="h", sensitivity=Sensitivity.S5
    )
    low = Artifact.create(
        owner_module_id="owner", artifact_id="low", payload="l", sensitivity=Sensitivity.S2
    )
    with pytest.raises(ValueError, match="below a reachable source"):
        SecurityObject.issue(
            scope=SecurityScopeRef(
                owner_module_id="owner", scope_id="bad", publication_revision="1"
            ),
            sensitivity=Sensitivity.S2,
            sensitivity_sources=(high.security,),
        )
    selected = ContextBundle.select(
        owner_module_id="owner",
        bundle_id="selected",
        purpose="test",
        members=(low, high),
    )
    assert selected.security.sensitivity is Sensitivity.S5


@pytest.mark.parametrize(
    ("risk", "autonomy", "demand"),
    [
        (Risk.R5, Autonomy.A1, 1),
        (Risk.R1, Autonomy.A5, 1),
        (Risk.R5, Autonomy.A5, 5),
    ],
)
def test_control_demand_is_minimum_of_risk_and_autonomy(
    risk: Risk, autonomy: Autonomy, demand: int
) -> None:
    result = ControlNormalForm.compose(
        profile=profile(f"p-{risk.value}-{autonomy.value}", risk, autonomy),
        controllers=(scope("controller", integrity=Integrity(demand)),),
    )
    assert result.accepted is not None
    assert result.accepted.demand_rank == demand


def test_profiles_do_not_combine_into_a_fictional_control_demand() -> None:
    first = ControlNormalForm.compose(
        profile=profile("risk-only", Risk.R5, Autonomy.A1), controllers=()
    ).accepted
    second = ControlNormalForm.compose(
        profile=profile("autonomy-only", Risk.R1, Autonomy.A5), controllers=()
    ).accepted
    assert first is not None and second is not None
    assert first.demand_rank == second.demand_rank == 1
    assert first.profile.effect_profile_id != second.profile.effect_profile_id


def test_effect_execution_requires_real_sufficient_executors() -> None:
    powerful = profile("powerful", Risk.R5, Autonomy.A1)
    passed = EffectExecutionNormalForm.compose(
        profile=powerful, executors=(scope("executor-5", integrity=Integrity.I5),)
    )
    denied = EffectExecutionNormalForm.compose(
        profile=powerful, executors=(scope("executor-1", integrity=Integrity.I1),)
    )
    empty = EffectExecutionNormalForm.compose(profile=powerful, executors=())
    assert passed.accepted is not None
    assert denied.decision().failure_codes == ("executor_integrity_below_risk",)
    assert empty.decision().failure_codes == ("missing_effect_executor",)


def test_effect_profile_contains_only_operation_owned_bounded_variant_facets() -> None:
    fields = set(EffectProfile.model_fields)
    assert fields == {"id", "effect_profile_id", "operation", "risk", "autonomy"}
    assert set(OperationUse.model_fields) == {
        "profile_id",
        "disclosure_observers",
        "controllers",
        "direct_interaction",
    }


def test_direct_user_action_is_exact_and_non_reusable() -> None:
    digest = "a" * 64
    material = scope("material", sensitivity=Sensitivity.S5, digest=digest)
    observer = scope("observer", privacy=Privacy.PUBLIC)
    operation = profile("effect", Risk.R3, Autonomy.A2)
    interaction = DirectUserInteraction(
        interaction_scope=SecurityScopeRef(
            owner_module_id="owner", scope_id="ui", publication_revision="1"
        ),
        interaction_revision="9",
        execution_id="live-1",
    )
    action = DirectUserAction(
        crossing_id="crossing-1",
        interaction=interaction,
        source_security_id=material.security_id,
        source_scope_revision=material.scope.scope_revision,
        source_digest=digest,
        observer_security_ids=(observer.security_id,),
        operation=operation.operation,
        effect_profile_id=operation.effect_profile_id,
    )

    def compose(**changes: object):
        arguments = {
            "crossing_id": "crossing-1",
            "sources": (material,),
            "observers": (observer,),
            "direct_user_action": action,
            "active_interaction": interaction,
            "operation": operation.operation,
            "effect_profile_id": operation.effect_profile_id,
            **changes,
        }
        return DisclosureNormalForm.compose(**arguments)  # type: ignore[arg-type]

    assert (
        DisclosureNormalForm.compose(
            crossing_id="crossing-1", sources=(material,), observers=(observer,)
        ).accepted
        is None
    )
    assert compose().accepted is not None
    assert compose(crossing_id="crossing-2").accepted is None
    assert compose(active_interaction=None).accepted is None
    assert compose(observers=(scope("other", privacy=Privacy.PUBLIC),)).accepted is None
    assert compose(effect_profile_id="changed").accepted is None
    other_operation = operation.operation.model_copy(update={"operation_revision": "2"})
    assert compose(operation=other_operation).accepted is None
    assert (
        compose(direct_user_action=action.model_copy(update={"source_digest": "b" * 64})).accepted
        is None
    )
    changed = material.model_copy(
        update={
            "scope": material.scope.model_copy(update={"scope_revision": "2"}),
        }
    )
    assert compose(sources=(changed,)).accepted is None
    assert (
        DisclosureNormalForm.compose(
            crossing_id="background",
            sources=(material,),
            observers=(observer,),
            direct_user_action=action,
            active_interaction=None,
            operation=operation.operation,
            effect_profile_id=operation.effect_profile_id,
        ).accepted
        is None
    )


def test_incremental_forms_match_reconstruction_and_history_is_non_authoritative() -> None:
    first = scope("s1", sensitivity=Sensitivity.S2)
    second = scope("s2", sensitivity=Sensitivity.S3)
    observer = scope("o", privacy=Privacy.SECRET)
    initial = DisclosureNormalForm.compose(
        crossing_id="initial", sources=(first,), observers=(observer,)
    ).accepted
    assert initial is not None
    incremental = initial.with_source(second).accepted
    canonical = DisclosureNormalForm.compose(
        crossing_id="canonical", sources=(first, second), observers=(observer,)
    ).accepted
    assert incremental == canonical
    controller_one = scope("controller-1", integrity=Integrity.I5)
    controller_two = scope("controller-2", integrity=Integrity.I4)
    bounded = profile("incremental", Risk.R4, Autonomy.A5)
    control_initial = ControlNormalForm.compose(
        profile=bounded, controllers=(controller_one,)
    ).accepted
    assert control_initial is not None
    control = control_initial.with_controller(controller_two).accepted
    assert (
        control
        == ControlNormalForm.compose(
            profile=bounded, controllers=(controller_one, controller_two)
        ).accepted
    )
    executor_one = scope("executor-1", integrity=Integrity.I5)
    executor_two = scope("executor-2", integrity=Integrity.I4)
    execution_initial = EffectExecutionNormalForm.compose(
        profile=bounded, executors=(executor_one,)
    ).accepted
    assert execution_initial is not None
    execution = execution_initial.with_executor(executor_two).accepted
    assert (
        execution
        == EffectExecutionNormalForm.compose(
            profile=bounded, executors=(executor_one, executor_two)
        ).accepted
    )
    unrelated = scope("unrelated", sensitivity=Sensitivity.S5, privacy=Privacy.PUBLIC)
    assert canonical is not None and control is not None and execution is not None
    evidence = SecurityEvidence(
        objects=(
            first,
            second,
            observer,
            unrelated,
            controller_one,
            controller_two,
            executor_one,
            executor_two,
        ),
        relations=(canonical, control, execution),
    )
    assert not audit_security_evidence(evidence)
    restored = SecurityEvidence.model_validate_json(evidence.model_dump_json())
    assert not audit_security_evidence(restored)


def test_validation_is_exact_bounded_and_fabricated_evidence_fails_audit() -> None:
    source = Artifact.create(
        owner_module_id="owner", artifact_id="raw", payload="x", sensitivity=Sensitivity.S3
    )
    validator = scope("validator", integrity=Integrity.I4)
    procedure = SecurityScopeRef(
        owner_module_id="owner", scope_id="validate", publication_revision="1"
    )
    validated = source.validated(
        procedure=procedure,
        validators=(validator,),
        artifact_id="validated",
        payload="x",
        integrity=Integrity.I4,
    )
    assert validated.security.integrity is Integrity.I4
    assert not audit_security_evidence(validated.evidence)
    fabricated = DerivationEvidence.issue(
        kind="validation",
        procedure=procedure,
        output_security_id=validated.security.security_id,
        source_security_ids=(source.security.security_id,),
        validator_security_ids=("missing",),
    )
    failures = audit_security_evidence(
        SecurityEvidence(objects=(source.security, validated.security), derivations=(fabricated,))
    )
    assert "unresolved_derivation_scope" in {item.code for item in failures}


@pytest.mark.parametrize(
    ("field", "wrong"),
    [
        ("sensitivity", Privacy.PUBLIC),
        ("privacy", Integrity.I1),
        ("integrity", Risk.R1),
    ],
)
def test_runtime_validation_rejects_cross_facet_enums(field: str, wrong: object) -> None:
    payload = {
        "security_id": "invalid",
        "scope": {
            "owner_module_id": "owner",
            "scope_id": "scope",
            "publication_revision": "1",
            "scope_revision": "1",
        },
        field: wrong,
        "binding_digest": "0" * 64,
    }
    with pytest.raises(ValidationError):
        SecurityObject.model_validate(payload)


def test_python_constructor_rejects_equal_rank_from_another_facet() -> None:
    with pytest.raises(ValidationError):
        SecurityObject.issue(
            scope=SecurityScopeRef(
                owner_module_id="owner", scope_id="wrong", publication_revision="1"
            ),
            sensitivity=Privacy.PUBLIC,  # type: ignore[arg-type]
        )


def test_integer_serialization_round_trips_to_nominal_facets() -> None:
    original = scope(
        "all", sensitivity=Sensitivity.S2, privacy=Privacy.MODULE_PRIVATE, integrity=Integrity.I5
    )
    encoded = original.model_dump_json()
    assert '"sensitivity":2' in encoded
    restored = SecurityObject.model_validate_json(encoded)
    assert restored.sensitivity is Sensitivity.S2
    assert restored.privacy is Privacy.MODULE_PRIVATE
    assert restored.integrity is Integrity.I5
