"""Frozen MADRE Security Algebra: immutable subjects, transitions, history, and decisions."""

from __future__ import annotations

import hashlib
import json
from enum import IntEnum
from typing import Annotated, Literal, Protocol, cast

from pydantic import BaseModel, ConfigDict, Field, model_validator

Identifier = Annotated[str, Field(min_length=1)]
SecurityID = Identifier
ExecutionBoundary = Literal["local", "isolated", "remote"]


class FrozenModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class SecurityLevel(IntEnum):
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5


OrdinarySecurityLevel = Literal[
    SecurityLevel.LEVEL_1,
    SecurityLevel.LEVEL_2,
    SecurityLevel.LEVEL_3,
    SecurityLevel.LEVEL_4,
    SecurityLevel.LEVEL_5,
]

SecuritySubjectKind = Literal[
    "artifact",
    "context_bundle",
    "module",
    "agent",
    "capability",
    "endpoint",
    "effect_profile",
]


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), default=int).encode()


def _digest(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


class MaterialSecurityValues(FrozenModel):
    kind: Literal["material"] = "material"
    sensitivity: OrdinarySecurityLevel | None = None
    integrity: OrdinarySecurityLevel | None = None


class ParticipantSecurityValues(FrozenModel):
    kind: Literal["participant"] = "participant"
    privacy: OrdinarySecurityLevel | None = None
    integrity: OrdinarySecurityLevel | None = None


class CapabilitySecurityValues(FrozenModel):
    kind: Literal["capability"] = "capability"
    privacy: OrdinarySecurityLevel | None = None
    integrity: OrdinarySecurityLevel | None = None


class EffectProfileSecurityValues(FrozenModel):
    kind: Literal["effect_profile"] = "effect_profile"
    risk: OrdinarySecurityLevel | None = None
    autonomy: OrdinarySecurityLevel | None = None
    integrity: OrdinarySecurityLevel | None = None
    privacy: OrdinarySecurityLevel | None = None


SecurityValues = Annotated[
    MaterialSecurityValues
    | ParticipantSecurityValues
    | CapabilitySecurityValues
    | EffectProfileSecurityValues,
    Field(discriminator="kind"),
]


class SecuritySubjectRef(FrozenModel):
    """Globally scoped structural identity of an immutable security-relevant subject."""

    owner_module_id: Identifier
    subject_kind: SecuritySubjectKind
    publication_revision: Identifier
    local_id: Identifier
    subject_revision: Identifier = "1"
    parent_local_id: Identifier | None = None


class BindingEvidence(FrozenModel):
    key: Identifier
    value: Identifier


class SecurityObject(FrozenModel):
    """Immutable normalized facts bound to one concrete subject representation."""

    security_id: SecurityID
    subject_ref: SecuritySubjectRef
    values: SecurityValues
    binding_evidence: tuple[BindingEvidence, ...] = ()
    binding_digest: str = Field(pattern=r"^[0-9a-f]{64}$")

    @model_validator(mode="after")
    def values_match_subject_kind(self) -> SecurityObject:
        expected: dict[str, type[BaseModel]] = {
            "artifact": MaterialSecurityValues,
            "context_bundle": MaterialSecurityValues,
            "module": ParticipantSecurityValues,
            "agent": ParticipantSecurityValues,
            "capability": CapabilitySecurityValues,
            "endpoint": ParticipantSecurityValues,
            "effect_profile": EffectProfileSecurityValues,
        }
        if not isinstance(self.values, expected[self.subject_ref.subject_kind]):
            raise ValueError(f"{self.subject_ref.subject_kind} has incompatible security values")
        evidence = tuple(sorted(self.binding_evidence, key=lambda item: (item.key, item.value)))
        if evidence != self.binding_evidence:
            raise ValueError("binding evidence must use canonical ordering")
        keys = [item.key for item in evidence]
        if len(keys) != len(set(keys)):
            raise ValueError("binding evidence keys must be unique")
        return self

    def identity_payload(self) -> dict[str, object]:
        return {
            "subject_ref": self.subject_ref.model_dump(mode="json"),
            "values": self.values.model_dump(mode="json"),
            "binding_evidence": [item.model_dump(mode="json") for item in self.binding_evidence],
        }

    def expected_security_id(self) -> SecurityID:
        return cast(SecurityID, f"security:v1:{_digest(self.identity_payload())}")

    def expected_binding_digest(self) -> str:
        return _digest(
            {"security_id": self.expected_security_id(), "identity": self.identity_payload()}
        )

    def verify_binding(self) -> bool:
        return (
            self.security_id == self.expected_security_id()
            and self.binding_digest == self.expected_binding_digest()
        )

    def evidence_value(self, key: str) -> str | None:
        return next((item.value for item in self.binding_evidence if item.key == key), None)

    @classmethod
    def issue(
        cls,
        *,
        subject_ref: SecuritySubjectRef,
        values: SecurityValues,
        binding_evidence: tuple[BindingEvidence, ...] = (),
    ) -> SecurityObject:
        binding_evidence = tuple(sorted(binding_evidence, key=lambda item: (item.key, item.value)))
        keys = [item.key for item in binding_evidence]
        if len(keys) != len(set(keys)):
            raise ValueError("binding evidence keys must be unique")
        identity_payload = {
            "subject_ref": subject_ref.model_dump(mode="json"),
            "values": values.model_dump(mode="json"),
            "binding_evidence": [item.model_dump(mode="json") for item in binding_evidence],
        }
        security_id = cast(SecurityID, f"security:v1:{_digest(identity_payload)}")
        binding_digest = _digest({"security_id": security_id, "identity": identity_payload})
        return cls(
            security_id=security_id,
            subject_ref=subject_ref,
            values=values,
            binding_evidence=binding_evidence,
            binding_digest=binding_digest,
        )


class OperationReference(FrozenModel):
    module_id: Identifier
    operation_id: Identifier
    publication_revision: Identifier
    operation_revision: Identifier = "1"


class EffectProfile(FrozenModel):
    """Immutable Operation-owned security-relevant execution shape."""

    id: Identifier
    operation: OperationReference
    security: SecurityObject
    discloses_material: bool = False

    @model_validator(mode="after")
    def binding_matches_operation(self) -> EffectProfile:
        ref = self.security.subject_ref
        expected_local_id = f"{self.operation.operation_id}/{self.id}"
        if (
            ref.subject_kind != "effect_profile"
            or ref.owner_module_id != self.operation.module_id
            or ref.publication_revision != self.operation.publication_revision
            or ref.parent_local_id != self.operation.operation_id
            or ref.local_id != expected_local_id
            or ref.subject_revision != self.operation.operation_revision
        ):
            raise ValueError("EffectProfile security binding does not match its Operation")
        if not self.security.verify_binding():
            raise ValueError("EffectProfile SecurityObject binding is invalid")
        values = cast(EffectProfileSecurityValues, self.security.values)
        if self.discloses_material and values.privacy is None:
            raise ValueError("material-disclosing EffectProfile requires Privacy")
        if not self.discloses_material and values.privacy is not None:
            raise ValueError("non-disclosing EffectProfile must not declare Privacy")
        return self


class Disclosure(FrozenModel):
    material_security_id: SecurityID
    path_security_ids: tuple[SecurityID, ...] = Field(min_length=1)


class Control(FrozenModel):
    effect_profile_security_id: SecurityID
    controller_security_ids: tuple[SecurityID, ...] = ()


class EffectExecution(FrozenModel):
    operation: OperationReference
    effect_profile_security_id: SecurityID
    executor_security_ids: tuple[SecurityID, ...] = ()


class SecurityTransition(FrozenModel):
    """One prospective security crossing/effect expressed through explicit topology."""

    transition_id: Identifier
    disclosures: tuple[Disclosure, ...] = ()
    control: Control | None = None
    effect_execution: EffectExecution | None = None

    def identity_payload(self) -> dict[str, object]:
        return {
            "disclosures": [item.model_dump(mode="json") for item in self.disclosures],
            "control": self.control.model_dump(mode="json") if self.control else None,
            "effect_execution": (
                self.effect_execution.model_dump(mode="json") if self.effect_execution else None
            ),
        }

    def verify_identity(self) -> bool:
        return self.transition_id == f"transition:v1:{_digest(self.identity_payload())}"

    @classmethod
    def issue(
        cls,
        *,
        disclosures: tuple[Disclosure, ...] = (),
        control: Control | None = None,
        effect_execution: EffectExecution | None = None,
    ) -> SecurityTransition:
        payload = {
            "disclosures": [item.model_dump(mode="json") for item in disclosures],
            "control": control.model_dump(mode="json") if control else None,
            "effect_execution": effect_execution.model_dump(mode="json")
            if effect_execution
            else None,
        }
        return cls(
            transition_id=f"transition:v1:{_digest(payload)}",
            disclosures=disclosures,
            control=control,
            effect_execution=effect_execution,
        )


DerivationKind = Literal["ordinary", "validation"]


class SecurityDerivation(FrozenModel):
    """Immutable representation/security transformation relationship."""

    derivation_id: Identifier
    kind: DerivationKind
    output_security_id: SecurityID
    source_security_ids: tuple[SecurityID, ...] = Field(min_length=1)
    producer_security_ids: tuple[SecurityID, ...] = ()
    validator_security_ids: tuple[SecurityID, ...] = ()

    def identity_payload(self) -> dict[str, object]:
        return {
            "kind": self.kind,
            "output_security_id": self.output_security_id,
            "source_security_ids": self.source_security_ids,
            "producer_security_ids": self.producer_security_ids,
            "validator_security_ids": self.validator_security_ids,
        }

    def verify_identity(self) -> bool:
        return self.derivation_id == f"derivation:v1:{_digest(self.identity_payload())}"

    @classmethod
    def issue(
        cls,
        *,
        kind: DerivationKind,
        output_security_id: SecurityID,
        source_security_ids: tuple[SecurityID, ...],
        producer_security_ids: tuple[SecurityID, ...] = (),
        validator_security_ids: tuple[SecurityID, ...] = (),
    ) -> SecurityDerivation:
        payload = {
            "kind": kind,
            "output_security_id": output_security_id,
            "source_security_ids": source_security_ids,
            "producer_security_ids": producer_security_ids,
            "validator_security_ids": validator_security_ids,
        }
        return cls(
            derivation_id=f"derivation:v1:{_digest(payload)}",
            kind=kind,
            output_security_id=output_security_id,
            source_security_ids=source_security_ids,
            producer_security_ids=producer_security_ids,
            validator_security_ids=validator_security_ids,
        )


class SecurityHistory(FrozenModel):
    """Immutable idempotent security facts and accepted causal relationships."""

    objects: tuple[SecurityObject, ...] = ()
    transitions: tuple[SecurityTransition, ...] = ()
    derivations: tuple[SecurityDerivation, ...] = ()

    @model_validator(mode="after")
    def canonicalize_sets(self) -> SecurityHistory:
        def dedupe(items: tuple[object, ...], key_name: str) -> tuple[object, ...]:
            unique: list[object] = []
            seen: dict[str, object] = {}
            conflicts: list[object] = []
            for item in items:
                key = cast(str, getattr(item, key_name))
                previous = seen.get(key)
                if previous is None:
                    seen[key] = item
                    unique.append(item)
                elif previous != item:
                    conflicts.append(item)
            combined = [*unique, *conflicts]
            return tuple(
                sorted(
                    combined,
                    key=lambda item: (
                        cast(str, getattr(item, key_name)),
                        _digest(cast(BaseModel, item).model_dump(mode="json")),
                    ),
                )
            )

        object.__setattr__(self, "objects", dedupe(self.objects, "security_id"))
        object.__setattr__(self, "transitions", dedupe(self.transitions, "transition_id"))
        object.__setattr__(self, "derivations", dedupe(self.derivations, "derivation_id"))
        return self

    @property
    def security_ids(self) -> tuple[SecurityID, ...]:
        return tuple(item.security_id for item in self.objects)

    def resolve(self, security_id: SecurityID) -> SecurityObject | None:
        matches = [item for item in self.objects if item.security_id == security_id]
        return matches[0] if len(matches) == 1 else None

    def extend(
        self,
        *,
        objects: tuple[SecurityObject, ...] = (),
        transitions: tuple[SecurityTransition, ...] = (),
        derivations: tuple[SecurityDerivation, ...] = (),
    ) -> SecurityHistory:
        return SecurityHistory(
            objects=(*self.objects, *objects),
            transitions=(*self.transitions, *transitions),
            derivations=(*self.derivations, *derivations),
        )

    def merge(self, *histories: SecurityHistory) -> SecurityHistory:
        result = self
        for history in histories:
            result = result.extend(
                objects=history.objects,
                transitions=history.transitions,
                derivations=history.derivations,
            )
        return result


class StructuralFailure(FrozenModel):
    code: Identifier
    security_ids: tuple[SecurityID, ...] = ()
    detail: Identifier | None = None


class DisclosureEvidence(FrozenModel):
    material_security_id: SecurityID
    sensitivity: OrdinarySecurityLevel | None
    path_security_ids: tuple[SecurityID, ...]
    path_privacy: OrdinarySecurityLevel | None
    limiting_security_ids: tuple[SecurityID, ...] = ()


class EffectEvidence(FrozenModel):
    effect_profile_security_id: SecurityID
    risk: OrdinarySecurityLevel | None
    autonomy: OrdinarySecurityLevel | None
    control_demand: OrdinarySecurityLevel | None
    controller_security_ids: tuple[SecurityID, ...]
    controller_integrity: OrdinarySecurityLevel | None
    executor_security_ids: tuple[SecurityID, ...]
    effect_integrity: OrdinarySecurityLevel | None


class SecurityDecision(FrozenModel):
    admissible: bool
    algebra_version: Literal["1"] = "1"
    transition_id: Identifier
    failures: tuple[StructuralFailure, ...] = ()
    disclosures: tuple[DisclosureEvidence, ...] = ()
    effect: EffectEvidence | None = None

    @property
    def failure_codes(self) -> tuple[str, ...]:
        return tuple(item.code for item in self.failures)


class SecurityEvaluator(Protocol):
    def evaluate(
        self, history: SecurityHistory, transition: SecurityTransition
    ) -> SecurityDecision: ...


def _ordinary(value: object) -> OrdinarySecurityLevel | None:
    try:
        integer = int(cast(int, value))
    except (TypeError, ValueError):
        return None
    if integer not in {1, 2, 3, 4, 5}:
        return None
    return cast(OrdinarySecurityLevel, SecurityLevel(integer))


def _integrity_value(obj: SecurityObject) -> OrdinarySecurityLevel | None:
    values = obj.values
    if isinstance(
        values,
        (
            MaterialSecurityValues,
            ParticipantSecurityValues,
            CapabilitySecurityValues,
            EffectProfileSecurityValues,
        ),
    ):
        return _ordinary(values.integrity)
    return None


def _privacy_value(obj: SecurityObject) -> OrdinarySecurityLevel | None:
    values = obj.values
    if isinstance(values, (ParticipantSecurityValues, CapabilitySecurityValues)):
        return _ordinary(values.privacy)
    if isinstance(values, EffectProfileSecurityValues):
        return _ordinary(values.privacy)
    return None


class SecurityAlgebra:
    """Deterministic implementation of the frozen transition-local MADRE predicates."""

    name = "madre-security-algebra-v1"

    def evaluate(
        self, history: SecurityHistory, transition: SecurityTransition
    ) -> SecurityDecision:
        failures: list[StructuralFailure] = []
        disclosures: list[DisclosureEvidence] = []
        effect_evidence: EffectEvidence | None = None
        index, object_conflicts = self._index_objects(history)
        failures.extend(object_conflicts)
        failures.extend(self._validate_historical_relations(history, index))

        for obj in history.objects:
            if not obj.verify_binding():
                failures.append(
                    StructuralFailure(
                        code="invalid_security_binding", security_ids=(obj.security_id,)
                    )
                )
            failures.extend(self._validate_object_schema(obj))

        if not transition.verify_identity():
            failures.append(StructuralFailure(code="invalid_security_binding", detail="transition"))

        failures.extend(self._validate_derivations(history, index))

        for disclosure in transition.disclosures:
            evidence, disclosure_failures = self._evaluate_disclosure(disclosure, index)
            disclosures.append(evidence)
            failures.extend(disclosure_failures)

        if (transition.control is None) != (transition.effect_execution is None):
            failures.append(
                StructuralFailure(code="invalid_effect_profile", detail="incomplete-effect")
            )
        elif transition.control is not None and transition.effect_execution is not None:
            effect_evidence, effect_failures = self._evaluate_effect(
                transition.control,
                transition.effect_execution,
                index,
            )
            failures.extend(effect_failures)

        return SecurityDecision(
            admissible=not failures,
            transition_id=transition.transition_id,
            failures=tuple(self._dedupe_failures(failures)),
            disclosures=tuple(disclosures),
            effect=effect_evidence,
        )

    @staticmethod
    def _index_objects(
        history: SecurityHistory,
    ) -> tuple[dict[SecurityID, SecurityObject], list[StructuralFailure]]:
        index: dict[SecurityID, SecurityObject] = {}
        failures: list[StructuralFailure] = []
        for obj in history.objects:
            previous = index.get(obj.security_id)
            if previous is None:
                index[obj.security_id] = obj
            elif previous != obj:
                failures.append(
                    StructuralFailure(code="security_id_conflict", security_ids=(obj.security_id,))
                )
        return index, failures

    @staticmethod
    def _validate_object_schema(obj: SecurityObject) -> list[StructuralFailure]:
        failures: list[StructuralFailure] = []
        values = obj.values
        fields: tuple[tuple[str, object], ...]
        if isinstance(values, MaterialSecurityValues):
            fields = (("sensitivity", values.sensitivity), ("integrity", values.integrity))
        elif isinstance(values, ParticipantSecurityValues):
            fields = (("privacy", values.privacy), ("integrity", values.integrity))
        elif isinstance(values, CapabilitySecurityValues):
            fields = (("privacy", values.privacy), ("integrity", values.integrity))
        elif isinstance(values, EffectProfileSecurityValues):
            fields = (
                ("risk", values.risk),
                ("autonomy", values.autonomy),
                ("integrity", values.integrity),
                ("privacy", values.privacy),
            )
        else:
            return [
                StructuralFailure(code="invalid_subject_values", security_ids=(obj.security_id,))
            ]
        for field, value in fields:
            if value is not None and _ordinary(value) is None:
                failures.append(
                    StructuralFailure(
                        code="invalid_subject_values",
                        security_ids=(obj.security_id,),
                        detail=field,
                    )
                )
        return failures

    def _validate_historical_relations(
        self,
        history: SecurityHistory,
        index: dict[SecurityID, SecurityObject],
    ) -> list[StructuralFailure]:
        failures: list[StructuralFailure] = []
        seen_transitions: dict[str, SecurityTransition] = {}
        numeric_failure_codes = {
            "confidentiality_capacity_below_sensitivity",
            "control_integrity_below_demand",
            "effect_integrity_below_risk",
        }
        for item in history.transitions:
            prior = seen_transitions.get(item.transition_id)
            if prior is not None and prior != item:
                failures.append(
                    StructuralFailure(
                        code="invalid_security_binding",
                        detail="transition-conflict",
                    )
                )
            else:
                seen_transitions[item.transition_id] = item
            if not item.verify_identity():
                failures.append(
                    StructuralFailure(
                        code="invalid_security_binding",
                        detail="historical-transition",
                    )
                )
                continue

            for disclosure in item.disclosures:
                _, relation_failures = self._evaluate_disclosure(disclosure, index)
                failures.extend(
                    failure
                    for failure in relation_failures
                    if failure.code not in numeric_failure_codes
                )

            if (item.control is None) != (item.effect_execution is None):
                failures.append(
                    StructuralFailure(
                        code="invalid_effect_profile",
                        detail="historical-incomplete-effect",
                    )
                )
            elif item.control is not None and item.effect_execution is not None:
                _, relation_failures = self._evaluate_effect(
                    item.control,
                    item.effect_execution,
                    index,
                )
                failures.extend(
                    failure
                    for failure in relation_failures
                    if failure.code not in numeric_failure_codes
                )
        return failures

    def _evaluate_disclosure(
        self,
        disclosure: Disclosure,
        index: dict[SecurityID, SecurityObject],
    ) -> tuple[DisclosureEvidence, list[StructuralFailure]]:
        failures: list[StructuralFailure] = []
        material = index.get(disclosure.material_security_id)
        sensitivity: OrdinarySecurityLevel | None = None
        if material is None:
            failures.append(
                StructuralFailure(
                    code="invalid_security_binding",
                    security_ids=(disclosure.material_security_id,),
                    detail="unresolved_security_id",
                )
            )
        elif not isinstance(material.values, MaterialSecurityValues):
            failures.append(
                StructuralFailure(
                    code="invalid_subject_values",
                    security_ids=(material.security_id,),
                    detail="disclosure_material",
                )
            )
        else:
            sensitivity = _ordinary(material.values.sensitivity)
            if material.values.sensitivity is None:
                failures.append(
                    StructuralFailure(
                        code="missing_security_value",
                        security_ids=(material.security_id,),
                        detail="sensitivity",
                    )
                )
            elif sensitivity is None:
                failures.append(
                    StructuralFailure(
                        code="invalid_subject_values",
                        security_ids=(material.security_id,),
                        detail="sensitivity",
                    )
                )

        privacy_pairs: list[tuple[SecurityID, OrdinarySecurityLevel]] = []
        for security_id in disclosure.path_security_ids:
            participant = index.get(security_id)
            if participant is None:
                failures.append(
                    StructuralFailure(
                        code="invalid_security_binding",
                        security_ids=(security_id,),
                        detail="unresolved_security_id",
                    )
                )
                continue
            privacy = _privacy_value(participant)
            if privacy is None:
                raw = getattr(participant.values, "privacy", None)
                failures.append(
                    StructuralFailure(
                        code="missing_security_value" if raw is None else "invalid_subject_values",
                        security_ids=(security_id,),
                        detail="privacy",
                    )
                )
                continue
            if participant.subject_ref.subject_kind not in {
                "module",
                "agent",
                "endpoint",
                "capability",
                "effect_profile",
            }:
                failures.append(
                    StructuralFailure(
                        code="invalid_subject_values",
                        security_ids=(security_id,),
                        detail="disclosure_path_role",
                    )
                )
                continue
            privacy_pairs.append((security_id, privacy))

        path_privacy: OrdinarySecurityLevel | None = None
        limiting: tuple[SecurityID, ...] = ()
        if len(privacy_pairs) == len(disclosure.path_security_ids):
            minimum = min(int(value) for _, value in privacy_pairs)
            path_privacy = cast(OrdinarySecurityLevel, SecurityLevel(minimum))
            limiting = tuple(
                security_id for security_id, value in privacy_pairs if int(value) == minimum
            )
            if sensitivity is not None and int(sensitivity) > minimum:
                failures.append(
                    StructuralFailure(
                        code="confidentiality_capacity_below_sensitivity",
                        security_ids=(disclosure.material_security_id, *limiting),
                    )
                )

        return (
            DisclosureEvidence(
                material_security_id=disclosure.material_security_id,
                sensitivity=sensitivity,
                path_security_ids=disclosure.path_security_ids,
                path_privacy=path_privacy,
                limiting_security_ids=limiting,
            ),
            failures,
        )

    def _evaluate_effect(
        self,
        control: Control,
        execution: EffectExecution,
        index: dict[SecurityID, SecurityObject],
    ) -> tuple[EffectEvidence, list[StructuralFailure]]:
        failures: list[StructuralFailure] = []
        if control.effect_profile_security_id != execution.effect_profile_security_id:
            failures.append(
                StructuralFailure(code="invalid_effect_profile", detail="profile-mismatch")
            )

        profile_id = execution.effect_profile_security_id
        profile = index.get(profile_id)
        risk: OrdinarySecurityLevel | None = None
        autonomy: OrdinarySecurityLevel | None = None
        profile_integrity: OrdinarySecurityLevel | None = None
        if profile is None:
            failures.append(
                StructuralFailure(
                    code="invalid_effect_profile",
                    security_ids=(profile_id,),
                    detail="unresolved_security_id",
                )
            )
        elif not isinstance(profile.values, EffectProfileSecurityValues):
            failures.append(
                StructuralFailure(
                    code="invalid_effect_profile",
                    security_ids=(profile_id,),
                    detail="subject_kind",
                )
            )
        else:
            risk = _ordinary(profile.values.risk)
            autonomy = _ordinary(profile.values.autonomy)
            profile_integrity = _ordinary(profile.values.integrity)
            for field, raw, normalized in (
                ("risk", profile.values.risk, risk),
                ("autonomy", profile.values.autonomy, autonomy),
                ("integrity", profile.values.integrity, profile_integrity),
            ):
                if raw is None:
                    failures.append(
                        StructuralFailure(
                            code="missing_security_value",
                            security_ids=(profile_id,),
                            detail=field,
                        )
                    )
                elif normalized is None:
                    failures.append(
                        StructuralFailure(
                            code="invalid_subject_values",
                            security_ids=(profile_id,),
                            detail=field,
                        )
                    )
            ref = profile.subject_ref
            if (
                ref.subject_kind != "effect_profile"
                or ref.owner_module_id != execution.operation.module_id
                or ref.publication_revision != execution.operation.publication_revision
                or ref.parent_local_id != execution.operation.operation_id
                or not ref.local_id.startswith(f"{execution.operation.operation_id}/")
                or ref.subject_revision != execution.operation.operation_revision
            ):
                failures.append(
                    StructuralFailure(
                        code="invalid_effect_profile",
                        security_ids=(profile_id,),
                        detail="operation-binding",
                    )
                )

        controller_pairs, controller_failures = self._integrities(
            control.controller_security_ids,
            index,
            allowed_kinds={
                "artifact",
                "context_bundle",
                "module",
                "agent",
                "endpoint",
                "capability",
            },
            role="controller_role",
        )
        failures.extend(controller_failures)
        controller_integrity = cast(OrdinarySecurityLevel, SecurityLevel.LEVEL_5)
        if controller_pairs:
            controller_integrity = cast(
                OrdinarySecurityLevel,
                SecurityLevel(min(int(value) for _, value in controller_pairs)),
            )

        executor_pairs, executor_failures = self._integrities(
            execution.executor_security_ids,
            index,
            allowed_kinds={"module", "agent", "endpoint", "capability"},
            role="executor_role",
        )
        failures.extend(executor_failures)
        effect_values = [value for _, value in executor_pairs]
        if profile_integrity is not None:
            effect_values.append(profile_integrity)
        effect_integrity: OrdinarySecurityLevel | None = None
        if effect_values and len(executor_pairs) == len(execution.executor_security_ids):
            effect_integrity = cast(
                OrdinarySecurityLevel,
                SecurityLevel(min(int(value) for value in effect_values)),
            )

        control_demand: OrdinarySecurityLevel | None = None
        if risk is not None and autonomy is not None:
            control_demand = cast(
                OrdinarySecurityLevel,
                SecurityLevel(min(int(risk), int(autonomy))),
            )
            if len(controller_pairs) == len(control.controller_security_ids) and int(
                control_demand
            ) > int(controller_integrity):
                limiting = tuple(
                    security_id
                    for security_id, value in controller_pairs
                    if int(value) == int(controller_integrity)
                )
                failures.append(
                    StructuralFailure(code="control_integrity_below_demand", security_ids=limiting)
                )

        if risk is not None and effect_integrity is not None and int(risk) > int(effect_integrity):
            limiting_ids = [
                security_id
                for security_id, value in executor_pairs
                if int(value) == int(effect_integrity)
            ]
            if profile_integrity is not None and int(profile_integrity) == int(effect_integrity):
                limiting_ids.append(profile_id)
            failures.append(
                StructuralFailure(
                    code="effect_integrity_below_risk",
                    security_ids=tuple(limiting_ids),
                )
            )

        return (
            EffectEvidence(
                effect_profile_security_id=profile_id,
                risk=risk,
                autonomy=autonomy,
                control_demand=control_demand,
                controller_security_ids=control.controller_security_ids,
                controller_integrity=(
                    controller_integrity
                    if len(controller_pairs) == len(control.controller_security_ids)
                    else None
                ),
                executor_security_ids=execution.executor_security_ids,
                effect_integrity=effect_integrity,
            ),
            failures,
        )

    @staticmethod
    def _integrities(
        security_ids: tuple[SecurityID, ...],
        index: dict[SecurityID, SecurityObject],
        *,
        allowed_kinds: set[str],
        role: str,
    ) -> tuple[list[tuple[SecurityID, OrdinarySecurityLevel]], list[StructuralFailure]]:
        pairs: list[tuple[SecurityID, OrdinarySecurityLevel]] = []
        failures: list[StructuralFailure] = []
        for security_id in security_ids:
            obj = index.get(security_id)
            if obj is None:
                failures.append(
                    StructuralFailure(
                        code="invalid_security_binding",
                        security_ids=(security_id,),
                        detail="unresolved_security_id",
                    )
                )
                continue
            if obj.subject_ref.subject_kind not in allowed_kinds:
                failures.append(
                    StructuralFailure(
                        code="invalid_subject_values",
                        security_ids=(security_id,),
                        detail=role,
                    )
                )
                continue
            value = _integrity_value(obj)
            if value is None:
                raw = getattr(obj.values, "integrity", None)
                failures.append(
                    StructuralFailure(
                        code="missing_security_value" if raw is None else "invalid_subject_values",
                        security_ids=(security_id,),
                        detail="integrity",
                    )
                )
                continue
            pairs.append((security_id, value))
        return pairs, failures

    def _validate_derivations(
        self,
        history: SecurityHistory,
        index: dict[SecurityID, SecurityObject],
    ) -> list[StructuralFailure]:
        failures: list[StructuralFailure] = []
        seen: dict[str, SecurityDerivation] = {}
        for derivation in history.derivations:
            prior = seen.get(derivation.derivation_id)
            if prior is not None and prior != derivation:
                failures.append(
                    StructuralFailure(code="invalid_derivation", detail="derivation-conflict")
                )
            else:
                seen[derivation.derivation_id] = derivation
            if not derivation.verify_identity():
                failures.append(
                    StructuralFailure(code="invalid_derivation", detail="derivation-binding")
                )
                continue
            output = index.get(derivation.output_security_id)
            sources = [index.get(item) for item in derivation.source_security_ids]
            producers = [index.get(item) for item in derivation.producer_security_ids]
            validators = [index.get(item) for item in derivation.validator_security_ids]
            referenced = (
                derivation.output_security_id,
                *derivation.source_security_ids,
                *derivation.producer_security_ids,
                *derivation.validator_security_ids,
            )
            missing = tuple(
                security_id
                for security_id, item in zip(
                    referenced,
                    (output, *sources, *producers, *validators),
                    strict=True,
                )
                if item is None
            )
            if missing:
                failures.append(
                    StructuralFailure(
                        code="invalid_derivation",
                        security_ids=missing,
                        detail="unresolved_security_id",
                    )
                )
                continue
            assert output is not None
            concrete_sources = cast(list[SecurityObject], sources)
            concrete_producers = cast(list[SecurityObject], producers)
            concrete_validators = cast(list[SecurityObject], validators)
            if not isinstance(output.values, MaterialSecurityValues):
                failures.append(
                    StructuralFailure(
                        code="invalid_derivation",
                        security_ids=(output.security_id,),
                        detail="output-not-material",
                    )
                )
                continue
            if output.security_id in derivation.source_security_ids:
                failures.append(
                    StructuralFailure(
                        code="invalid_derivation",
                        security_ids=(output.security_id,),
                        detail="output-not-distinct",
                    )
                )
                continue
            output_integrity = _integrity_value(output)
            if output_integrity is None:
                failures.append(
                    StructuralFailure(
                        code="missing_security_value",
                        security_ids=(output.security_id,),
                        detail="integrity",
                    )
                )
                continue
            if derivation.kind == "ordinary":
                if derivation.validator_security_ids:
                    failures.append(
                        StructuralFailure(
                            code="invalid_derivation", detail="ordinary-has-validators"
                        )
                    )
                    continue
                participants = (*concrete_sources, *concrete_producers)
                assurance = [_integrity_value(item) for item in participants]
                if not assurance or any(value is None for value in assurance):
                    failures.append(
                        StructuralFailure(
                            code="missing_security_value",
                            security_ids=tuple(
                                item.security_id
                                for item, value in zip(participants, assurance, strict=True)
                                if value is None
                            ),
                            detail="integrity",
                        )
                    )
                    continue
                bound = min(int(cast(OrdinarySecurityLevel, value)) for value in assurance)
                if int(output_integrity) > bound:
                    failures.append(
                        StructuralFailure(
                            code="invalid_derivation",
                            security_ids=(output.security_id,),
                            detail="ordinary-integrity-increase",
                        )
                    )
            else:
                if not concrete_validators:
                    failures.append(
                        StructuralFailure(
                            code="invalid_derivation", detail="validation-without-validator"
                        )
                    )
                    continue
                assurance = [_integrity_value(item) for item in concrete_validators]
                if any(value is None for value in assurance):
                    failures.append(
                        StructuralFailure(
                            code="missing_security_value",
                            security_ids=tuple(
                                item.security_id
                                for item, value in zip(concrete_validators, assurance, strict=True)
                                if value is None
                            ),
                            detail="integrity",
                        )
                    )
                    continue
                bound = min(int(cast(OrdinarySecurityLevel, value)) for value in assurance)
                if int(output_integrity) > bound:
                    failures.append(
                        StructuralFailure(
                            code="invalid_derivation",
                            security_ids=(output.security_id,),
                            detail="validation-integrity-above-assurance",
                        )
                    )
        return failures

    @staticmethod
    def _dedupe_failures(failures: list[StructuralFailure]) -> list[StructuralFailure]:
        seen: set[tuple[object, ...]] = set()
        result: list[StructuralFailure] = []
        for failure in failures:
            key = (failure.code, failure.security_ids, failure.detail)
            if key not in seen:
                seen.add(key)
                result.append(failure)
        return result


DEFAULT_SECURITY_EVALUATOR: SecurityEvaluator = SecurityAlgebra()
