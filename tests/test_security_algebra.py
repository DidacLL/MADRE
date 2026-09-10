from itertools import product

import pytest
from pydantic import ValidationError

from madre.security import (
    Control,
    Disclosure,
    EffectExecution,
    RiskEnvelope,
    SecurityAlgebra,
    SecurityDerivation,
    SecurityHistory,
    SecurityLevel,
    SecurityTransition,
)
from madre_sdk import Artifact, effect_profile
from tests.test_registry_broker import boundary
from tests.v2_helpers import L1, L5, participant


def material(name="source", sensitivity=L5, assurance=L5):
    return Artifact.create(
        owner_module_id="test",
        artifact_id=name,
        payload={name: True},
        sensitivity=sensitivity,
        assurance=assurance,
    )


def evaluate(*objects, transition=None, derivations=(), transitions=()):
    return SecurityAlgebra().evaluate(
        SecurityHistory(objects=objects, derivations=derivations, transitions=transitions),
        transition or SecurityTransition.issue(),
    )


def test_disclosure_all_levels():
    for sensitivity, privacy in product(range(1, 6), repeat=2):
        source = material(sensitivity=SecurityLevel(sensitivity))
        target = boundary("target", SecurityLevel(privacy))
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=source.security.security_id,
                    boundary_security_ids=(target.security_id,),
                ),
            )
        )
        assert evaluate(source.security, target, transition=transition).admissible == (
            sensitivity <= privacy
        )


def test_control_effect_and_autonomy_independence_all_levels():
    for cr, er, q, executor, a in product(range(1, 6), repeat=5):
        if cr > er:
            continue
        profile = effect_profile(
            owner_module_id="test",
            operation_id="op",
            profile_id="p",
            control_risk=SecurityLevel(cr),
            effect_risk=SecurityLevel(er),
            autonomy=SecurityLevel(a),
            assurance=SecurityLevel(executor),
        )
        actor = participant("test", "actor", "agent", assurance=SecurityLevel(q))
        transition = SecurityTransition.issue(
            control=Control(
                effect_profile_security_id=profile.security.security_id,
                controller_security_ids=(actor.security_id,),
            ),
            effect_execution=EffectExecution(
                operation=profile.operation, effect_profile_security_id=profile.security.security_id
            ),
        )
        result = evaluate(profile.security, actor, transition=transition)
        assert result.admissible == (cr <= q and er <= executor)
        assert result.effect.control_demand == cr


def test_risk_envelope_order_and_v1_rejection():
    with pytest.raises(ValidationError):
        RiskEnvelope(control_risk=L5, effect_risk=L1)
    from madre.security import ParticipantSecurityValues

    with pytest.raises(ValidationError):
        ParticipantSecurityValues(privacy=L5, assurance=L5)
    with pytest.raises(ValidationError):
        SecurityHistory(version="1")
    obj = participant("test", "test")
    forged = obj.model_copy(update={"security_id": obj.security_id.replace(":v2:", ":v1:")})
    assert not evaluate(forged).admissible


def test_actor_is_not_a_disclosure_boundary():
    source = material()
    actor = participant("test", "actor", "agent")
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=source.security.security_id,
                boundary_security_ids=(actor.security_id,),
            ),
        )
    )
    assert not evaluate(source.security, actor, transition=transition).admissible


def test_role_sets_are_canonical_and_serial_boundary_order_is_preserved():
    assert Control(
        effect_profile_security_id="p", controller_security_ids=("b", "a", "a")
    ) == Control(effect_profile_security_id="p", controller_security_ids=("a", "b"))
    a = Disclosure(material_security_id="m", boundary_security_ids=("a", "b"))
    b = Disclosure(material_security_id="n", boundary_security_ids=("b",))
    assert SecurityTransition.issue(disclosures=(b, a, a)) == SecurityTransition.issue(
        disclosures=(a, b)
    )
    assert a.boundary_security_ids == ("a", "b")


def test_ordinary_derivation_cannot_raise_assurance():
    source = material(assurance=L1)
    output = material("output")
    relation = SecurityDerivation.issue(
        kind="ordinary",
        output_security_id=output.security.security_id,
        source_security_ids=(source.security.security_id,),
    )
    result = evaluate(source.security, output.security, derivations=(relation,))
    assert "invalid_derivation" in result.failure_codes


def test_derivation_conflicts_cycles_and_idempotence():
    a, b, c = (material(x) for x in "abc")

    def edge(output, source):
        return SecurityDerivation.issue(
            kind="ordinary",
            output_security_id=output.security.security_id,
            source_security_ids=(source.security.security_id,),
        )

    ab, bc, ca = edge(a, b), edge(b, c), edge(c, a)
    objects = (a.security, b.security, c.security)
    assert evaluate(*objects, derivations=(ab, ab, bc)).admissible
    assert not evaluate(*objects, derivations=(ab, bc, ca)).admissible
    assert not evaluate(*objects, derivations=(ab, edge(a, c))).admissible


def test_denied_transition_cannot_be_realized_history():
    source = material()
    low = boundary("outside", L1)
    transition = SecurityTransition.issue(
        disclosures=(
            Disclosure(
                material_security_id=source.security.security_id,
                boundary_security_ids=(low.security_id,),
            ),
        )
    )
    assert not evaluate(source.security, low, transitions=(transition,)).admissible


def test_declared_profile_roles_cannot_be_omitted_from_a_supplied_transition():
    weak = participant("test", "executor", "agent", assurance=L1)
    profile = effect_profile(
        owner_module_id="test",
        operation_id="op",
        profile_id="p",
        control_risk=L1,
        effect_risk=L5,
        autonomy=L5,
        assurance=L5,
        executors=(weak,),
    )
    transition = SecurityTransition.issue(
        control=Control(effect_profile_security_id=profile.security.security_id),
        effect_execution=EffectExecution(
            operation=profile.operation, effect_profile_security_id=profile.security.security_id
        ),
    )
    result = evaluate(profile.security, weak, transition=transition)
    assert "invalid_effect_profile" in result.failure_codes
    assert not evaluate(profile.security, weak, transitions=(transition,)).admissible
