"""Small discriminating cases for the scoped MADRE algebra."""

import pytest
from pydantic import ValidationError

from madre.security import (
    Disclosure,
    SecurityDerivation,
    SecurityHistory,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
    SecurityValues,
    UserRelease,
)
from madre_sdk import (
    Agent,
    Artifact,
    ContextBundle,
    InvocationContext,
    Module,
    Operation,
    disclosure,
    effect_profile,
    effect_transition,
    feasibility,
    participant_security,
)


def scope(name, *, sources=(), **values):
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id="m", subject_kind="module", publication_revision="1", local_id=name
        ),
        values=SecurityValues(**values),
        sensitivity_sources=sources,
    )


def test_narrow_module_surface_and_reachable_secret():
    secret = scope("private", sensitivity=5)
    arithmetic = scope("arithmetic", sensitivity=1)
    observer = scope("local", privacy=3)
    history = SecurityHistory(objects=(secret, arithmetic, observer))
    assert feasibility(history, disclosure(arithmetic, observer)).admissible
    false = scope("false-arithmetic", sensitivity=1, sources=(secret.security_id,))
    decision = feasibility(history.extend(objects=(false,)), disclosure(false, observer))
    assert "invalid_sensitivity_closure" in decision.failure_codes


def test_public_and_unknown_are_distinct_and_release_is_exact():
    material = scope("state", sensitivity=2)
    public = scope("public", privacy=1)
    unknown = scope("cloud", privacy=2)
    history = SecurityHistory(objects=(material, public, unknown))
    public_t = disclosure(material, public)
    assert not feasibility(history, public_t).admissible
    assert feasibility(history, disclosure(material, unknown)).admissible
    release = UserRelease(interaction=material.subject_ref, disclosure=public_t.disclosures[0])
    carried = history.extend(releases=(release,))
    assert feasibility(carried, public_t).admissible
    changed = scope("state-revision", sensitivity=3)
    assert not feasibility(
        carried.extend(objects=(changed,)), disclosure(changed, public)
    ).admissible
    telemetry = scope("telemetry", privacy=1)
    assert not feasibility(
        carried.extend(objects=(telemetry,)), disclosure(material, public, telemetry)
    ).admissible
    assert SecurityHistory.model_validate_json(carried.model_dump_json()) == carried


def test_profile_atomicity_and_executor_independence():
    controller = scope("controller", integrity=1)
    strong = scope("executor", integrity=5)
    weak = scope("weak", integrity=1)
    profiles = [
        effect_profile(
            owner_module_id="m", operation_id="op", profile_id=str(a), risk=r, autonomy=a
        )
        for r, a in ((5, 1), (1, 5), (5, 5))
    ]
    history = SecurityHistory(objects=(controller, strong, weak, *(e.security for e in profiles)))
    decisions = [
        feasibility(
            history, effect_transition(profile=e, controllers=(controller,), executors=(strong,))
        )
        for e in profiles
    ]
    assert [d.admissible for d in decisions] == [True, True, False]
    assert max(d.effect.control_demand for d in decisions[:2]) == 1
    assert min(max(5, 1), max(1, 5)) == 5  # The fictional global pair is not a demand.
    direct = effect_transition(profile=profiles[0], controllers=(), executors=(weak,))
    assert "effect_integrity_below_risk" in feasibility(history, direct).failure_codes
    empty = effect_transition(profile=profiles[0], controllers=(), executors=())
    assert not feasibility(history, empty).admissible


def test_history_does_not_replay_unrelated_numeric_branches():
    secret = scope("secret", sensitivity=5)
    public = scope("public", privacy=1)
    arithmetic = scope("math", sensitivity=1)
    old = disclosure(secret, public)
    history = SecurityHistory(objects=(secret, public, arithmetic), transitions=(old,))
    assert feasibility(history, disclosure(arithmetic, public)).admissible


def test_ordinary_closure_and_explicit_transform_binding():
    source = scope("source", sensitivity=5)
    output = scope("output", sensitivity=2)
    history = SecurityHistory(objects=(source, output))
    ordinary = SecurityDerivation.issue(
        kind="ordinary",
        output_security_id=output.security_id,
        source_security_ids=(source.security_id,),
    )
    assert not feasibility(
        history.extend(derivations=(ordinary,)), SecurityTransition.issue()
    ).admissible
    transform = SecurityDerivation.issue(
        kind="transform",
        output_security_id=output.security_id,
        source_security_ids=(source.security_id,),
        procedure=source.subject_ref,
    )
    assert feasibility(
        history.extend(derivations=(transform,)), SecurityTransition.issue()
    ).admissible
    altered = transform.model_copy(update={"output_security_id": source.security_id})
    assert not feasibility(
        history.extend(derivations=(altered,)), SecurityTransition.issue()
    ).admissible


def test_structured_selection_has_no_generic_integrity():
    public = Artifact.create(
        owner_module_id="m", artifact_id="public", payload="hello", sensitivity=1
    )
    secret = Artifact.create(
        owner_module_id="m", artifact_id="secret", payload="private", sensitivity=5
    )
    public = public.model_copy(
        update={"security_history": public.security_history.merge(secret.security_history)}
    )
    bundle = ContextBundle.select(
        owner_module_id="m", bundle_id="selected", purpose="display", members=(public,)
    )
    assert bundle.payload == {"public": "hello"}
    assert bundle.security.values.sensitivity == 1
    assert bundle.security.values.integrity is None


def test_levels_and_paired_profile_values():
    with pytest.raises(ValidationError):
        SecurityValues(sensitivity=0)
    with pytest.raises(ValidationError):
        scope("not-a-profile", risk=5, autonomy=1)


def test_remote_capability_cannot_claim_vendor_privacy():
    from madre.capabilities import CapabilityDescriptor, CapabilityRegistry

    class Adapter:
        def __init__(self, privacy):
            security = SecurityObject.issue(
                subject_ref=SecuritySubjectRef(
                    owner_module_id="provider",
                    subject_kind="capability",
                    publication_revision="1",
                    local_id="cloud",
                ),
                values=SecurityValues(privacy=privacy),
            )
            self.descriptor = CapabilityDescriptor(
                id="cloud",
                specialization="chat",
                modality="text",
                execution_boundary="remote",
                security=security,
            )

    CapabilityRegistry().register(Adapter(2))
    with pytest.raises(ValueError, match="UNKNOWN"):
        CapabilityRegistry().register(Adapter(5))


def test_release_is_not_reused_for_a_changed_effect_profile_route():
    material = scope("released-material", sensitivity=5)
    observer = scope("public-observer", privacy=1)
    executor = scope("executor", integrity=5)
    first_profile = effect_profile(
        owner_module_id="m", operation_id="publish", profile_id="first", risk=1, autonomy=1
    )
    changed_profile = effect_profile(
        owner_module_id="m", operation_id="publish", profile_id="changed", risk=1, autonomy=1
    )
    crossing = effect_transition(
        profile=first_profile,
        controllers=(),
        executors=(executor,),
        disclosures=(
            Disclosure(
                material_security_id=material.security_id,
                path_security_ids=(observer.security_id,),
            ),
        ),
    )
    release = UserRelease(
        interaction=material.subject_ref,
        disclosure=crossing.disclosures[0],
        effect_execution=crossing.effect_execution,
    )
    history = SecurityHistory(
        objects=(material, observer, executor, first_profile.security, changed_profile.security),
        releases=(release,),
    )
    assert feasibility(history, crossing).admissible

    changed_route = effect_transition(
        profile=changed_profile,
        controllers=(),
        executors=(executor,),
        disclosures=crossing.disclosures,
    )
    assert (
        "confidentiality_capacity_below_sensitivity"
        in feasibility(history, changed_route).failure_codes
    )


def test_nested_sensitivity_closure_survives_serialization_without_poisoning_isolated_scope():
    secret = scope("nested-secret", sensitivity=5)
    inner = scope("inner-surface", sensitivity=5, sources=(secret.security_id,))
    falsely_low = scope("outer-surface", sensitivity=1, sources=(inner.security_id,))
    isolated = scope("isolated-arithmetic", sensitivity=1)
    restored = SecurityHistory.model_validate_json(
        SecurityHistory(objects=(secret, inner, falsely_low, isolated)).model_dump_json()
    )

    assert (
        "invalid_sensitivity_closure"
        in feasibility(restored, SecurityTransition.issue()).failure_codes
    )
    local = scope("local", privacy=3)
    assert feasibility(
        SecurityHistory(objects=(secret, isolated, local)), disclosure(isolated, local)
    ).admissible


def test_module_owned_transform_may_raise_correlated_sensitivity():
    left = scope("left", sensitivity=2)
    right = scope("right", sensitivity=2)
    correlated = scope("correlated", sensitivity=4)
    procedure = SecuritySubjectRef(
        owner_module_id="m",
        subject_kind="transform",
        publication_revision="1",
        local_id="correlation",
    )
    transform = SecurityDerivation.issue(
        kind="transform",
        output_security_id=correlated.security_id,
        source_security_ids=(left.security_id, right.security_id),
        procedure=procedure,
    )
    assert feasibility(
        SecurityHistory(objects=(left, right, correlated), derivations=(transform,)),
        SecurityTransition.issue(),
    ).admissible


def test_independent_disclosures_are_not_globally_reduced():
    secret = scope("secret-source", sensitivity=5)
    secret_observer = scope("secret-observer", privacy=5)
    public = scope("public-source", sensitivity=1)
    public_observer = scope("public-observer", privacy=1)
    history = SecurityHistory(objects=(secret, secret_observer, public, public_observer))

    assert feasibility(history, disclosure(secret, secret_observer)).admissible
    assert feasibility(history, disclosure(public, public_observer)).admissible


def test_public_sdk_constructs_complete_security_topology_without_core_bypass():
    source = Artifact.create(
        owner_module_id="sdk-module",
        artifact_id="reachable-state",
        payload={"value": 1},
        sensitivity=3,
    )
    module_security = participant_security(
        owner_module_id="sdk-module",
        subject_id="sdk-module",
        subject_kind="module",
        privacy=5,
        integrity=5,
        sensitivity=3,
        sensitivity_sources=(source.security.security_id,),
    )
    agent_security = participant_security(
        owner_module_id="sdk-module",
        subject_id="worker",
        subject_kind="agent",
        privacy=5,
        integrity=5,
        sensitivity=3,
        sensitivity_sources=(source.security.security_id,),
    )
    executor = participant_security(
        owner_module_id="sdk-module",
        subject_id="executor",
        subject_kind="endpoint",
        privacy=5,
        integrity=5,
    )
    profile = effect_profile(
        owner_module_id="sdk-module",
        operation_id="publish",
        profile_id="direct",
        risk=5,
        autonomy=1,
    )
    agent = Agent.from_instructions(
        agent_id="worker",
        purpose="work",
        instructions="perform bounded work",
        security=agent_security,
        behavior=object(),
    )
    operation = Operation(
        operation_id="publish",
        purpose="publish bounded material",
        input_contract="json:any",
        output_contract="json:any",
        effect="publish",
        repeatability="never-retry-unknown-outcome",
        effect_profiles=(profile,),
        behavior=object(),
    )
    module = Module(
        module_id="sdk-module",
        version="1",
        description="SDK topology smoke module",
        security=module_security,
        agents=(agent,),
        operations=(operation,),
        endpoint_security=executor,
    )
    invocation = InvocationContext(module=module.security, agent=agent.security, endpoint=executor)
    narrowed = ContextBundle.select(
        owner_module_id=module.module_id, bundle_id="narrow", purpose="bounded", members=(source,)
    )
    transformed = Artifact.derive_from(
        invocation=invocation,
        source=source,
        owner_module_id=module.module_id,
        artifact_id="correlated",
        payload={"correlated": True},
        producer_security_ids=(),
        sensitivity=4,
        procedure=SecuritySubjectRef(
            owner_module_id=module.module_id,
            subject_kind="transform",
            publication_revision="1",
            local_id="correlate",
        ),
    )
    transition = effect_transition(
        profile=profile,
        controllers=(agent.security,),
        executors=(executor,),
        disclosures=(
            Disclosure(
                material_security_id=source.security.security_id,
                path_security_ids=(module.security.security_id,),
            ),
        ),
    )
    release = UserRelease(
        interaction=module.security.subject_ref,
        disclosure=transition.disclosures[0],
        effect_execution=transition.effect_execution,
    )
    history = source.security_history.merge(transformed.security_history).extend(
        objects=(module.security, agent.security, executor, profile.security), releases=(release,)
    )

    assert narrowed.security.values.sensitivity == 3
    assert transformed.security.values.sensitivity == 4
    assert feasibility(history, transition).admissible
