"""Small discriminating cases for the scoped MADRE algebra."""

import pytest
from pydantic import ValidationError

from madre.security import (
    SecurityDerivation,
    SecurityHistory,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
    SecurityValues,
    UserRelease,
)
from madre_sdk import (
    Artifact,
    ContextBundle,
    disclosure,
    effect_profile,
    effect_transition,
    feasibility,
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
