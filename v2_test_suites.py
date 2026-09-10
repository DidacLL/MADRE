from pathlib import Path
R=Path(__file__).parent
def wr(p,s):(R/p).write_text(s)
wr('tests/test_security_algebra.py', '''from itertools import product

import pytest
from pydantic import ValidationError

from madre.security import (SecurityAlgebra, SecurityHistory, SecurityTransition, SecurityDerivation,
    Disclosure, Control, EffectExecution, RiskEnvelope, SecurityLevel, SecurityObject)
from madre_sdk import Artifact, effect_profile
from tests.v2_helpers import L1, L5, participant
from tests.test_registry_broker import boundary

def material(name="source", sensitivity=L5, assurance=L5):
    return Artifact.create(owner_module_id="test", artifact_id=name, payload={name:True},
        sensitivity=sensitivity, assurance=assurance)

def evaluate(*objects, transition=None, derivations=(), transitions=()):
    return SecurityAlgebra().evaluate(SecurityHistory(objects=objects, derivations=derivations,
        transitions=transitions), transition or SecurityTransition.issue())

def test_disclosure_all_levels():
    for sensitivity, privacy in product(range(1,6),repeat=2):
        source=material(sensitivity=SecurityLevel(sensitivity))
        target=boundary("target", SecurityLevel(privacy))
        transition=SecurityTransition.issue(disclosures=(Disclosure(material_security_id=source.security.security_id,
            boundary_security_ids=(target.security_id,)),))
        assert evaluate(source.security,target,transition=transition).admissible == (sensitivity <= privacy)

def test_control_effect_and_autonomy_independence_all_levels():
    for cr,er,q,executor,a in product(range(1,6),repeat=5):
        if cr>er: continue
        profile=effect_profile(owner_module_id="test",operation_id="op",profile_id="p",
            control_risk=SecurityLevel(cr),effect_risk=SecurityLevel(er),autonomy=SecurityLevel(a),
            assurance=SecurityLevel(executor))
        actor=participant("test","actor","agent",assurance=SecurityLevel(q))
        transition=SecurityTransition.issue(control=Control(effect_profile_security_id=profile.security.security_id,
            controller_security_ids=(actor.security_id,)), effect_execution=EffectExecution(operation=profile.operation,
            effect_profile_security_id=profile.security.security_id))
        result=evaluate(profile.security,actor,transition=transition)
        assert result.admissible == (cr<=q and er<=executor)
        assert result.effect.control_demand == cr

def test_risk_envelope_order_and_v1_rejection():
    with pytest.raises(ValidationError): RiskEnvelope(control_risk=L5,effect_risk=L1)
    from madre.security import ParticipantSecurityValues
    with pytest.raises(ValidationError): ParticipantSecurityValues(privacy=L5,assurance=L5)
    with pytest.raises(ValidationError): SecurityHistory(version="1")
    obj=participant("test","test")
    forged=obj.model_copy(update={"security_id":obj.security_id.replace(":v2:",":v1:")})
    assert not evaluate(forged).admissible

def test_actor_is_not_a_disclosure_boundary():
    source=material()
    actor=participant("test","actor","agent")
    transition=SecurityTransition.issue(disclosures=(Disclosure(material_security_id=source.security.security_id,
        boundary_security_ids=(actor.security_id,)),))
    assert not evaluate(source.security,actor,transition=transition).admissible

def test_role_sets_are_canonical_and_serial_boundary_order_is_preserved():
    assert Control(effect_profile_security_id="p",controller_security_ids=("b","a","a")) == Control(
        effect_profile_security_id="p",controller_security_ids=("a","b"))
    a=Disclosure(material_security_id="m",boundary_security_ids=("a","b"))
    b=Disclosure(material_security_id="n",boundary_security_ids=("b",))
    assert SecurityTransition.issue(disclosures=(b,a,a)) == SecurityTransition.issue(disclosures=(a,b))
    assert a.boundary_security_ids == ("a","b")

def test_ordinary_derivation_cannot_raise_assurance():
    source=material(assurance=L1); output=material("output")
    relation=SecurityDerivation.issue(kind="ordinary",output_security_id=output.security.security_id,
        source_security_ids=(source.security.security_id,))
    result=evaluate(source.security,output.security,derivations=(relation,))
    assert "invalid_derivation" in result.failure_codes

def test_derivation_conflicts_cycles_and_idempotence():
    a,b,c=(material(x) for x in "abc")
    def edge(output,source): return SecurityDerivation.issue(kind="ordinary",
        output_security_id=output.security.security_id,source_security_ids=(source.security.security_id,))
    ab,bc,ca=edge(a,b),edge(b,c),edge(c,a)
    objects=(a.security,b.security,c.security)
    assert evaluate(*objects,derivations=(ab,ab,bc)).admissible
    assert not evaluate(*objects,derivations=(ab,bc,ca)).admissible
    assert not evaluate(*objects,derivations=(ab,edge(a,c))).admissible

def test_denied_transition_cannot_be_realized_history():
    source=material(); low=boundary("outside",L1)
    transition=SecurityTransition.issue(disclosures=(Disclosure(material_security_id=source.security.security_id,
        boundary_security_ids=(low.security_id,)),))
    assert not evaluate(source.security,low,transitions=(transition,)).admissible
''')
wr('tests/test_causal_topology.py', '''from __future__ import annotations

import asyncio
import json

import pytest

from madre.broker import InvalidModuleResult, ModuleEndpointUnavailable, SecurityDenied
from madre.security import InvocationContext, SecurityHistory, SecurityDerivation
from madre_sdk import (AgentBrokerClient, ModuleServices, OperationBrokerClient, TransformBrokerClient,
    Artifact, effect_profile)
from tests.v2_helpers import (L1,L5,Repackage,agent_module,attach,invoke,participant,
    transform_module,ClassifiedOutput,as_artifact)
from tests.v2_helpers import platform as platform
from tests.test_registry_broker import (REQUESTER,TARGET,attachment,boundary,input_material,
    requester_security,make_operation_module)

def requester(): return InvocationContext(module=requester_security(),endpoint=attachment(REQUESTER))

def run_transform(broker,module,source):
    return asyncio.run(broker.invoke_transform(requester(),source.security_history,module.module_id,
        module.transforms[0].contract.id,source.transient()))

def test_containment_does_not_revalue_unknown_actor_and_egress_fails(platform):
    registry,broker,_=platform
    inside=agent_module(Repackage(passthrough=True),assurance=L1,privacy=L5)
    attach(registry,broker,inside)
    secret=input_material(requester_security(),sensitivity=L5)
    output=invoke(broker,inside,secret)
    assert output.security == secret.security
    assert inside.agents[0].security.values.assurance == L1
    outside=agent_module(Repackage(passthrough=True),owner="outside",privacy=L1)
    attach(registry,broker,outside)
    with pytest.raises(SecurityDenied): invoke(broker,outside,as_artifact(output))

def test_wrappers_are_not_security_operands(platform):
    registry,broker,store=platform
    module=agent_module(Repackage())
    attach(registry,broker,module)
    output=invoke(broker,module)
    relation=next(x for x in output.history.derivations if x.output_security_id==output.security.security_id)
    assert relation.producer_security_ids == (module.agents[0].security.security_id,)
    assert module.security.security_id not in relation.producer_security_ids
    assert not hasattr(module.endpoint_binding,"security_id")
    for transition in output.history.transitions:
        for disclosure in transition.disclosures:
            assert all(output.history.resolve(sid).subject_ref.subject_kind=="disclosure_boundary"
                for sid in disclosure.boundary_security_ids)

@pytest.mark.parametrize("payload,expected",[({"credential":"test-secret"},L1),({"medical":"private"},L5)])
def test_transform_classification_depends_on_concrete_outcome(platform,payload,expected):
    registry,broker,store=platform
    module=transform_module(ClassifiedOutput()); attach(registry,broker,module)
    source=Artifact.create(owner_module_id=REQUESTER,artifact_id="source",payload=payload,
        sensitivity=L5,assurance=L1)
    output=run_transform(broker,module,source)
    assert output.security.values.sensitivity==expected
    assert output.security.values.assurance==L5
    assert output.security.security_id != source.security.security_id
    relation=next(x for x in output.history.derivations if x.output_security_id==output.security.security_id)
    assert relation.kind=="transform"
    assert relation.transform_security_id==module.transforms[0].contract.security.security_id
    assert relation.derivation_id in store.completed_derivation_ids()

def test_completed_transformed_source_is_provenance_not_control(platform):
    registry,broker,_=platform
    module=transform_module(ClassifiedOutput()); attach(registry,broker,module)
    source=input_material(requester_security(),sensitivity=L5,assurance=L1)
    result=run_transform(broker,module,source)
    profile=effect_profile(owner_module_id=TARGET,operation_id="target.operation",profile_id="high",
        control_risk=L5,effect_risk=L5,autonomy=L5,assurance=L5)
    target,_=make_operation_module((profile,));attach(registry,broker,target)
    evaluated=broker.evaluate_operation_profiles(requester(),result.history,TARGET,"target.operation",result)
    assert evaluated.feasible_profile_ids==("high",)
    effect=evaluated.profiles[0].decision.effect
    assert source.security.security_id not in effect.controller_security_ids
    assert result.security.security_id in effect.controller_security_ids
    explicit=broker.evaluate_operation_profiles(requester(),result.history,TARGET,"target.operation",result,
        (source.security.security_id,))
    assert not explicit.feasible_profile_ids

def test_transform_receipt_cannot_be_borrowed_for_another_output(platform):
    registry,broker,_=platform
    module=transform_module(ClassifiedOutput());attach(registry,broker,module)
    source=input_material(requester_security(),assurance=L1)
    output=run_transform(broker,module,source)
    fake=Artifact.create(owner_module_id=REQUESTER,artifact_id="unexecuted",payload={"fake":True},
        sensitivity=L1,assurance=L5)
    relation=SecurityDerivation.issue(kind="transform",output_security_id=fake.security.security_id,
        source_security_ids=(source.security.security_id,),
        producer_security_ids=(module.transforms[0].contract.security.security_id,),
        transform_security_id=module.transforms[0].contract.security.security_id)
    forged=fake.model_copy(update={"security_history":output.history.extend(objects=(fake.security,),derivations=(relation,))})
    target=agent_module(Repackage());attach(registry,broker,target)
    with pytest.raises(SecurityDenied,match="unverified_transform_execution"): invoke(broker,target,forged)

def test_profile_specific_topologies_ties_and_prospective_locality(platform):
    registry,broker,store=platform
    weak=participant(TARGET,"weak","agent",assurance=L1)
    def profile(name,cr=L1,a=L5,**kwargs):
        return effect_profile(owner_module_id=TARGET,operation_id="target.operation",profile_id=name,
            control_risk=cr,effect_risk=L5,autonomy=a,assurance=L5,**kwargs)
    profiles=(profile("a"),profile("b"),profile("blocked-control",L5),
        profile("blocked-executor",executors=(weak,)),
        profile("published",disclosure_boundaries=(boundary("public",L1),)),
        profile("ack",L5,L1),profile("exact",L1,L1))
    module,behavior=make_operation_module(profiles);attach(registry,broker,module)
    source=input_material(requester_security(),assurance=L1,sensitivity=L5)
    before=store.connection.total_changes
    result=broker.evaluate_operation_profiles(requester(),source.security_history,TARGET,"target.operation",source.transient())
    assert result.feasible_profile_ids==("a","b","exact")
    assert result.highest_profile_ids==("a","b")
    assert result.maximum_feasible_autonomy==L5
    assert store.connection.total_changes==before and not behavior.called
    decisions={x.profile_id:x.decision for x in result.profiles}
    assert "effect_assurance_below_risk" in decisions["blocked-executor"].failure_codes
    assert "confidentiality_capacity_below_sensitivity" in decisions["published"].failure_codes
    assert "control_assurance_below_demand" in decisions["ack"].failure_codes

def test_only_a1_is_returned_when_real_a5_fails(platform):
    registry,broker,_=platform
    profiles=tuple(effect_profile(owner_module_id=TARGET,operation_id="target.operation",profile_id=name,
        control_risk=cr,effect_risk=L5,autonomy=a,assurance=L5) for name,cr,a in (("a1",L1,L1),("a5",L5,L5)))
    module,_=make_operation_module(profiles);attach(registry,broker,module)
    source=input_material(requester_security(),assurance=L1)
    result=broker.evaluate_operation_profiles(requester(),source.security_history,TARGET,"target.operation",source.transient())
    assert result.feasible_profile_ids==result.highest_profile_ids==("a1",)

def test_same_version_replacement_rejects_stale_attachment(platform):
    registry,broker,_=platform
    module=agent_module(Repackage());attach(registry,broker,module)
    replacement=module.manifest().model_copy(update={"description":"changed publication"})
    registry.register(replacement)
    with pytest.raises(ModuleEndpointUnavailable):invoke(broker,module)

def test_transform_contract_replacement_requires_reattachment(platform):
    registry,broker,_=platform
    module=transform_module(ClassifiedOutput());attach(registry,broker,module)
    original=module.transforms[0].contract
    from madre.security import SecurityObject
    changed=SecurityObject.issue(subject_ref=original.security.subject_ref,
        values=original.security.values.model_copy(update={"contract_id":"other"}))
    registry.register(module.manifest().model_copy(update={"transforms":(original.model_copy(update={"security":changed}),)}))
    with pytest.raises(ModuleEndpointUnavailable):run_transform(broker,module,input_material(requester_security()))

def test_publication_change_during_execution_uses_captured_route(platform):
    registry,broker,_=platform
    class Change:
        async def execute(self,*,material,**kwargs):
            registry.register(module.manifest().model_copy(update={"description":"changed"}))
            return as_artifact(material)
    module=agent_module(Change());attach(registry,broker,module)
    assert invoke(broker,module).payload
    with pytest.raises(ModuleEndpointUnavailable):invoke(broker,module)

def test_new_output_requires_actual_production(platform):
    registry,broker,_=platform
    class Fake:
        async def execute(self,*,material,**kwargs):
            return Artifact.create(owner_module_id=TARGET,artifact_id="fake",payload={},sensitivity=L5,assurance=L5)
    module=agent_module(Fake());attach(registry,broker,module)
    with pytest.raises(InvalidModuleResult):invoke(broker,module)

def test_completed_transform_survives_restart(tmp_path):
    from madre.storage import open_database,PlatformStore
    from madre.registry import InteroperabilityRegistry
    from madre.broker import Broker
    from madre.security import SecurityAlgebra,SecurityTransition
    with open_database(tmp_path) as connection:
        store=PlatformStore(connection);registry=InteroperabilityRegistry(store);broker=Broker(registry,store)
        module=transform_module(ClassifiedOutput());attach(registry,broker,module)
        output=run_transform(broker,module,input_material(requester_security(),assurance=L1))
        restored=output.model_dump_json()
    with open_database(tmp_path) as connection:
        from madre.contracts import TransientMaterial
        output=TransientMaterial.model_validate_json(restored)
        store=PlatformStore(connection);registry=InteroperabilityRegistry(store);broker=Broker(registry,store)
        target=agent_module(Repackage(passthrough=True));attach(registry,broker,target)
        assert invoke(broker,target,as_artifact(output)).security==output.security

def test_v1_storage_is_rejected_without_deletion(tmp_path):
    import sqlite3
    from madre.storage import open_database
    file=tmp_path/"runtime.sqlite3"
    with sqlite3.connect(file) as connection:
        connection.execute("PRAGMA user_version=1")
        connection.execute("CREATE TABLE keep(value)")
    with pytest.raises(RuntimeError,match="incompatible persisted security format"):
        with open_database(tmp_path):pass
    with sqlite3.connect(file) as connection:
        assert connection.execute("SELECT name FROM sqlite_master WHERE name='keep'").fetchone()
''')
