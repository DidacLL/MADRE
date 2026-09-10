from __future__ import annotations

from itertools import product

import pytest
from pydantic import ValidationError

from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    BindingEvidence,
    CapabilitySecurityValues,
    Disclosure,
    MaterialSecurityValues,
    ParticipantSecurityValues,
    SecurityDerivation,
    SecurityHistory,
    SecurityLevel,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
)
from madre_sdk.security import effect_profile, effect_transition

LEVELS = tuple(SecurityLevel(value) for value in range(1, 6))


def material(
    local_id: str,
    *,
    sensitivity: SecurityLevel,
    integrity: SecurityLevel,
    owner: str = "module.source",
) -> SecurityObject:
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id=owner,
            subject_kind="artifact",
            publication_revision="1",
            local_id=local_id,
        ),
        values=MaterialSecurityValues(sensitivity=sensitivity, integrity=integrity),
    )


def participant(
    local_id: str,
    *,
    privacy: SecurityLevel,
    integrity: SecurityLevel,
    owner: str = "module.target",
    kind: str = "module",
) -> SecurityObject:
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id=owner,
            subject_kind=kind,  # type: ignore[arg-type]
            publication_revision="1",
            local_id=local_id,
        ),
        values=ParticipantSecurityValues(privacy=privacy, integrity=integrity),
    )


def capability(
    local_id: str,
    *,
    privacy: SecurityLevel,
    integrity: SecurityLevel,
) -> SecurityObject:
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id="madre.platform",
            subject_kind="capability",
            publication_revision="1",
            local_id=local_id,
        ),
        values=CapabilitySecurityValues(privacy=privacy, integrity=integrity),
    )


@pytest.mark.parametrize(("sensitivity", "privacy"), product(LEVELS, LEVELS))
def test_disclosure_predicate_is_exact(
    sensitivity: SecurityLevel,
    privacy: SecurityLevel,
) -> None:
    secret = material("input", sensitivity=sensitivity, integrity=SecurityLevel.LEVEL_5)
    path = participant("receiver", privacy=privacy, integrity=SecurityLevel.LEVEL_5)
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=secret.security_id,
                path_security_ids=(path.security_id,),
            ),
        )
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(secret, path)), transition
    )
    assert decision.admissible is (int(sensitivity) <= int(privacy))


@pytest.mark.parametrize(
    ("risk", "autonomy", "controller_integrity"), product(LEVELS, LEVELS, LEVELS)
)
def test_control_predicate_is_exact(
    risk: SecurityLevel,
    autonomy: SecurityLevel,
    controller_integrity: SecurityLevel,
) -> None:
    profile = effect_profile(
        owner_module_id="module.effect",
        operation_id="delete.resource",
        profile_id="autonomous",
        risk=risk,
        autonomy=autonomy,
        integrity=SecurityLevel.LEVEL_5,
    )
    controller = material(
        "control",
        sensitivity=SecurityLevel.LEVEL_1,
        integrity=controller_integrity,
    )
    executor = participant(
        "module.effect",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
        owner="module.effect",
    )
    transition = effect_transition(
        profile=profile,
        controllers=(controller,),
        executors=(executor,),
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(profile.security, controller, executor)), transition
    )
    assert decision.admissible is (min(int(risk), int(autonomy)) <= int(controller_integrity))


@pytest.mark.parametrize(("risk", "executor_integrity"), product(LEVELS, LEVELS))
def test_effect_execution_predicate_is_exact(
    risk: SecurityLevel,
    executor_integrity: SecurityLevel,
) -> None:
    profile = effect_profile(
        owner_module_id="module.effect",
        operation_id="write.resource",
        profile_id="direct",
        risk=risk,
        autonomy=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_5,
    )
    executor = participant(
        "module.effect",
        privacy=SecurityLevel.LEVEL_5,
        integrity=executor_integrity,
        owner="module.effect",
    )
    transition = effect_transition(profile=profile, controllers=(), executors=(executor,))
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(profile.security, executor)), transition
    )
    assert decision.admissible is (int(risk) <= int(executor_integrity))


def test_direct_user_control_does_not_compensate_weak_executor() -> None:
    profile = effect_profile(
        owner_module_id="module.effect",
        operation_id="delete.resource",
        profile_id="direct",
        risk=SecurityLevel.LEVEL_5,
        autonomy=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_5,
    )
    controller = material(
        "exact-user-command",
        sensitivity=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_1,
    )
    executor = participant(
        "module.effect",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_2,
        owner="module.effect",
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(profile.security, controller, executor)),
        effect_transition(
            profile=profile,
            controllers=(controller,),
            executors=(executor,),
        ),
    )
    assert "control_integrity_below_demand" not in decision.failure_codes
    assert "effect_integrity_below_risk" in decision.failure_codes


def test_low_risk_autonomous_effect_can_use_low_integrity_control_and_executor() -> None:
    profile = effect_profile(
        owner_module_id="module.effect",
        operation_id="harmless.effect",
        profile_id="autonomous",
        risk=SecurityLevel.LEVEL_1,
        autonomy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_1,
    )
    controller = material(
        "generated-control",
        sensitivity=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_1,
    )
    executor = participant(
        "module.effect",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_1,
        owner="module.effect",
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(profile.security, controller, executor)),
        effect_transition(
            profile=profile,
            controllers=(controller,),
            executors=(executor,),
        ),
    )
    assert decision.admissible


def test_publishing_profile_adds_independent_confidentiality_obligation() -> None:
    secret = material(
        "secret",
        sensitivity=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    profile = effect_profile(
        owner_module_id="module.publisher",
        operation_id="publish",
        profile_id="direct-public",
        risk=SecurityLevel.LEVEL_5,
        autonomy=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_5,
        privacy=SecurityLevel.LEVEL_1,
        discloses_material=True,
    )
    executor = participant(
        "module.publisher",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
        owner="module.publisher",
    )
    transition = effect_transition(
        profile=profile,
        controllers=(secret,),
        executors=(executor,),
        disclosures=(
            Disclosure(
                material_security_id=secret.security_id,
                path_security_ids=(profile.security.security_id,),
            ),
        ),
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(secret, profile.security, executor)), transition
    )
    assert "confidentiality_capacity_below_sensitivity" in decision.failure_codes
    assert "control_integrity_below_demand" not in decision.failure_codes
    assert "effect_integrity_below_risk" not in decision.failure_codes


def test_unrelated_low_privacy_history_does_not_poison_transition() -> None:
    secret = material(
        "secret",
        sensitivity=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    actual = participant(
        "actual",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    unrelated = participant(
        "unrelated",
        privacy=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_1,
    )
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=secret.security_id,
                path_security_ids=(actual.security_id,),
            ),
        )
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(secret, actual, unrelated)), transition
    )
    assert decision.admissible


def test_ordinary_derivation_cannot_raise_integrity() -> None:
    source = material(
        "source",
        sensitivity=SecurityLevel.LEVEL_3,
        integrity=SecurityLevel.LEVEL_1,
    )
    producer = participant(
        "producer",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    output = material(
        "output",
        sensitivity=SecurityLevel.LEVEL_2,
        integrity=SecurityLevel.LEVEL_2,
    )
    relation = SecurityDerivation.issue(
        kind="ordinary",
        output_security_id=output.security_id,
        source_security_ids=(source.security_id,),
        producer_security_ids=(producer.security_id,),
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(source, producer, output), derivations=(relation,)),
        SecurityTransition.issue(),
    )
    assert "invalid_derivation" in decision.failure_codes


def test_validation_derivation_can_raise_integrity_on_validator_assurance() -> None:
    source = material(
        "source",
        sensitivity=SecurityLevel.LEVEL_3,
        integrity=SecurityLevel.LEVEL_1,
    )
    validator = participant(
        "validator",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    output = material(
        "validated",
        sensitivity=SecurityLevel.LEVEL_3,
        integrity=SecurityLevel.LEVEL_5,
    )
    relation = SecurityDerivation.issue(
        kind="validation",
        output_security_id=output.security_id,
        source_security_ids=(source.security_id,),
        validator_security_ids=(validator.security_id,),
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(source, validator, output), derivations=(relation,)),
        SecurityTransition.issue(),
    )
    assert decision.admissible


def test_minimized_representation_uses_its_own_sensitivity() -> None:
    source = material(
        "source-secret",
        sensitivity=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    producer = participant(
        "minimizer",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    minimized = material(
        "minimized",
        sensitivity=SecurityLevel.LEVEL_2,
        integrity=SecurityLevel.LEVEL_5,
    )
    receiver = participant(
        "receiver",
        privacy=SecurityLevel.LEVEL_3,
        integrity=SecurityLevel.LEVEL_5,
    )
    relation = SecurityDerivation.issue(
        kind="ordinary",
        output_security_id=minimized.security_id,
        source_security_ids=(source.security_id,),
        producer_security_ids=(producer.security_id,),
    )
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=minimized.security_id,
                path_security_ids=(receiver.security_id,),
            ),
        )
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(
            objects=(source, producer, minimized, receiver),
            derivations=(relation,),
        ),
        transition,
    )
    assert decision.admissible


def test_security_history_is_idempotent_for_identical_evidence() -> None:
    subject = participant(
        "module.target",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    transition = SecurityTransition.issue()
    history = SecurityHistory(
        objects=(subject, subject),
        transitions=(transition, transition),
    )
    assert history.objects == (subject,)
    assert history.transitions == (transition,)


def test_conflicting_same_security_id_is_structural_failure() -> None:
    subject = participant(
        "module.target",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    conflicting = subject.model_copy(
        update={
            "values": ParticipantSecurityValues(
                privacy=SecurityLevel.LEVEL_1, integrity=SecurityLevel.LEVEL_1
            )
        }
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(subject, conflicting)), SecurityTransition.issue()
    )
    assert "security_id_conflict" in decision.failure_codes
    assert "invalid_security_binding" in decision.failure_codes


def test_same_local_identity_in_different_modules_has_distinct_security_ids() -> None:
    left = participant(
        "shared.agent",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
        owner="module.left",
        kind="agent",
    )
    right = participant(
        "shared.agent",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
        owner="module.right",
        kind="agent",
    )
    assert left.security_id != right.security_id


def test_missing_role_value_is_structural_failure_not_numeric_default() -> None:
    secret = material(
        "secret",
        sensitivity=SecurityLevel.LEVEL_3,
        integrity=SecurityLevel.LEVEL_5,
    )
    incomplete = SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id="module.target",
            subject_kind="module",
            publication_revision="1",
            local_id="module.target",
        ),
        values=ParticipantSecurityValues(privacy=None, integrity=SecurityLevel.LEVEL_5),
    )
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=secret.security_id,
                path_security_ids=(incomplete.security_id,),
            ),
        )
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(secret, incomplete)), transition
    )
    assert "missing_security_value" in decision.failure_codes


def test_system_reserved_cannot_be_used_as_ordinary_value() -> None:
    with pytest.raises(ValidationError):
        MaterialSecurityValues(
            sensitivity=SecurityLevel.SYSTEM_RESERVED,
            integrity=SecurityLevel.LEVEL_1,
        )


def test_binding_evidence_is_order_independent_and_duplicate_keys_are_rejected() -> None:
    subject_ref = SecuritySubjectRef(
        owner_module_id="module.target",
        subject_kind="module",
        publication_revision="1",
        local_id="module.target",
    )
    values = ParticipantSecurityValues(
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    a = BindingEvidence(key="a", value="1")
    b = BindingEvidence(key="b", value="2")
    left = SecurityObject.issue(
        subject_ref=subject_ref,
        values=values,
        binding_evidence=(a, b),
    )
    right = SecurityObject.issue(
        subject_ref=subject_ref,
        values=values,
        binding_evidence=(b, a),
    )
    assert left.security_id == right.security_id
    with pytest.raises(ValueError):
        SecurityObject.issue(
            subject_ref=subject_ref,
            values=values,
            binding_evidence=(a, BindingEvidence(key="a", value="different")),
        )


def test_security_id_possession_does_not_grant_authority() -> None:
    subject = participant(
        "module.target",
        privacy=SecurityLevel.LEVEL_1,
        integrity=SecurityLevel.LEVEL_1,
    )
    assert subject.security_id
    secret = material(
        "secret",
        sensitivity=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=secret.security_id,
                path_security_ids=(subject.security_id,),
            ),
        )
    )
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(secret, subject)), transition
    )
    assert not decision.admissible
