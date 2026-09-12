"""Typed, immutable, relation-local MADRE Security Algebra."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from enum import Enum
from typing import Annotated, Generic, Literal, TypeVar, cast

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

Identifier = Annotated[str, Field(min_length=1)]
SecurityID = Identifier
ExecutionBoundary = Literal["local", "isolated", "remote"]


class FrozenModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class Sensitivity(Enum):
    S1 = 1
    S2 = 2
    S3 = 3
    S4 = 4
    S5 = 5


class Privacy(Enum):
    PUBLIC = 1
    UNKNOWN = 2
    LOCAL_PRIVATE = 3
    MODULE_PRIVATE = 4
    SECRET = 5


class Integrity(Enum):
    I1 = 1
    I2 = 2
    I3 = 3
    I4 = 4
    I5 = 5


class Risk(Enum):
    R1 = 1
    R2 = 2
    R3 = 3
    R4 = 4
    R5 = 5


class Autonomy(Enum):
    A1 = 1
    A2 = 2
    A3 = 3
    A4 = 4
    A5 = 5


Facet = Sensitivity | Privacy | Integrity | Risk | Autonomy


def _rank(value: Facet) -> int:
    return cast(int, value.value)


def _json_default(value: object) -> object:
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, BaseModel):
        return value.model_dump(mode="json")
    raise TypeError(f"unsupported canonical value: {type(value).__name__}")


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), default=_json_default).encode()


def _digest(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


class SecurityScopeRef(FrozenModel):
    """Role-neutral identity of one exact immutable security projection."""

    owner_module_id: Identifier
    scope_id: Identifier
    publication_revision: Identifier
    scope_revision: Identifier = "1"


class ScopeBinding(FrozenModel):
    """Generic representation/contract bindings understood by the algebra."""

    content_digest: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")
    contract_digest: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")


class SecurityObject(FrozenModel):
    """Normalized security facts for one exact scope."""

    security_id: SecurityID
    scope: SecurityScopeRef
    sensitivity: Sensitivity | None = None
    privacy: Privacy | None = None
    integrity: Integrity | None = None
    binding: ScopeBinding = Field(default_factory=ScopeBinding)
    binding_digest: str = Field(pattern=r"^[0-9a-f]{64}$")

    def identity_payload(self) -> dict[str, object]:
        return {
            "scope": self.scope.model_dump(mode="json"),
            "sensitivity": self.sensitivity.value if self.sensitivity is not None else None,
            "privacy": self.privacy.value if self.privacy is not None else None,
            "integrity": self.integrity.value if self.integrity is not None else None,
            "binding": self.binding.model_dump(mode="json"),
        }

    def expected_security_id(self) -> SecurityID:
        return f"security:v3:{_digest(self.identity_payload())}"

    def expected_binding_digest(self) -> str:
        return _digest({"security_id": self.expected_security_id(), "identity": self.identity_payload()})

    def verify_binding(self) -> bool:
        return self.security_id == self.expected_security_id() and self.binding_digest == self.expected_binding_digest()

    @classmethod
    def issue(
        cls,
        *,
        scope: SecurityScopeRef,
        sensitivity: Sensitivity | None = None,
        privacy: Privacy | None = None,
        integrity: Integrity | None = None,
        binding: ScopeBinding | None = None,
    ) -> SecurityObject:
        actual_binding = binding or ScopeBinding()
        payload = {
            "scope": scope.model_dump(mode="json"),
            "sensitivity": sensitivity.value if sensitivity is not None else None,
            "privacy": privacy.value if privacy is not None else None,
            "integrity": integrity.value if integrity is not None else None,
            "binding": actual_binding.model_dump(mode="json"),
        }
        security_id = f"security:v3:{_digest(payload)}"
        return cls(
            security_id=security_id,
            scope=scope,
            sensitivity=sensitivity,
            privacy=privacy,
            integrity=integrity,
            binding=actual_binding,
            binding_digest=_digest({"security_id": security_id, "identity": payload}),
        )


class OperationReference(FrozenModel):
    module_id: Identifier
    operation_id: Identifier
    publication_revision: Identifier
    operation_revision: Identifier = "1"


class EffectProfile(FrozenModel):
    """One immutable Operation-owned bounded effect variant."""

    id: Identifier
    effect_profile_id: Identifier
    operation: OperationReference
    risk: Risk
    autonomy: Autonomy

    def identity_payload(self) -> dict[str, object]:
        return {
            "id": self.id,
            "operation": self.operation.model_dump(mode="json"),
            "risk": self.risk.value,
            "autonomy": self.autonomy.value,
        }

    def verify_identity(self) -> bool:
        return self.effect_profile_id == f"effect-profile:v3:{_digest(self.identity_payload())}"

    @classmethod
    def issue(
        cls,
        *,
        id: str,
        operation: OperationReference,
        risk: Risk,
        autonomy: Autonomy,
    ) -> EffectProfile:
        payload = {
            "id": id,
            "operation": operation.model_dump(mode="json"),
            "risk": risk.value,
            "autonomy": autonomy.value,
        }
        return cls(
            id=id,
            effect_profile_id=f"effect-profile:v3:{_digest(payload)}",
            operation=operation,
            risk=risk,
            autonomy=autonomy,
        )


class DirectUserInteraction(FrozenModel):
    """Module-attested live interaction context. It grants no security exception by itself."""

    interaction_scope: SecurityScopeRef
    interaction_revision: Identifier
    execution_id: Identifier


class DirectUserAction(FrozenModel):
    """Exact Module-attested user action for one immediate Operation use."""

    interaction: DirectUserInteraction
    source_security_id: SecurityID
    source_scope_revision: Identifier
    source_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    operation: OperationReference
    effect_profile_id: Identifier
    additional_observer_security_ids: tuple[SecurityID, ...] = ()

    @field_validator("additional_observer_security_ids")
    @classmethod
    def canonical_observers(cls, value: tuple[str, ...]) -> tuple[str, ...]:
        canonical = tuple(sorted(set(value)))
        if canonical != value:
            raise ValueError("additional observers must be unique and canonically ordered")
        return value

    def covers(
        self,
        *,
        source: SecurityObject,
        active_interaction: DirectUserInteraction | None,
        operation: OperationReference | None,
        effect_profile_id: str | None,
        additional_observers: tuple[SecurityObject, ...],
    ) -> bool:
        return (
            active_interaction is not None
            and self.interaction == active_interaction
            and self.source_security_id == source.security_id
            and self.source_scope_revision == source.scope.scope_revision
            and self.source_digest == source.binding.content_digest
            and self.operation == operation
            and self.effect_profile_id == effect_profile_id
            and self.additional_observer_security_ids
            == tuple(sorted(item.security_id for item in additional_observers))
        )


class InvocationContext(FrozenModel):
    """Exact current causal identity explicitly carried by Module execution."""

    module: SecurityObject
    agent: SecurityObject | None = None
    endpoint: SecurityObject | None = None
    operation: OperationReference | None = None
    direct_interaction: DirectUserInteraction | None = None

    @model_validator(mode="after")
    def validate_participants(self) -> InvocationContext:
        module_scope = self.module.scope
        if module_scope.scope_id != module_scope.owner_module_id:
            raise ValueError("invocation Module scope must equal its owner identity")
        if self.agent is not None and self.operation is not None:
            raise ValueError("invocation cannot select both Agent and Operation")
        for participant in (self.module, self.agent, self.endpoint):
            if participant is None:
                continue
            scope = participant.scope
            if (
                scope.owner_module_id != module_scope.owner_module_id
                or scope.publication_revision != module_scope.publication_revision
                or participant.privacy is None
                or not participant.verify_binding()
            ):
                raise ValueError("invocation participants must match the exact publication")
        if self.operation is not None and (
            self.operation.module_id != self.module_id
            or self.operation.publication_revision != module_scope.publication_revision
        ):
            raise ValueError("invocation Operation belongs to another publication")
        if self.direct_interaction is not None:
            interaction = self.direct_interaction.interaction_scope
            if (
                interaction.owner_module_id != module_scope.owner_module_id
                or interaction.publication_revision != module_scope.publication_revision
            ):
                raise ValueError("direct interaction belongs to another Module publication")
        return self

    @property
    def module_id(self) -> str:
        return self.module.scope.owner_module_id

    @property
    def objects(self) -> tuple[SecurityObject, ...]:
        return tuple(item for item in (self.module, self.agent, self.endpoint) if item is not None)

    @property
    def selector(self) -> SecurityObject:
        return self.agent or self.module

    @property
    def recipients(self) -> tuple[SecurityObject, ...]:
        return tuple(item for item in (self.module, self.agent) if item is not None)


class OperationUse(FrozenModel):
    """Concrete facts selected for one Operation invocation."""

    profile_id: Identifier
    disclosure_observers: tuple[SecurityObject, ...] = ()
    controllers: tuple[SecurityObject, ...] = ()
    direct_user_action: DirectUserAction | None = None


class SecurityFailure(FrozenModel):
    code: Identifier
    security_ids: tuple[SecurityID, ...] = ()
    detail: Identifier | None = None


NormalFormT = TypeVar("NormalFormT", bound=FrozenModel)


@dataclass(frozen=True)
class CompositionResult(Generic[NormalFormT]):  # noqa: UP046
    relation_id: str
    relation_kind: Literal["disclosure", "control", "effect_execution"]
    normal_form: NormalFormT | None
    failures: tuple[SecurityFailure, ...] = ()

    @property
    def admissible(self) -> bool:
        return self.normal_form is not None and not self.failures

    @property
    def accepted(self) -> NormalFormT | None:
        return self.normal_form

    def decision(self) -> RelationDecision:
        return RelationDecision(
            relation_id=self.relation_id,
            relation_kind=self.relation_kind,
            admissible=self.admissible,
            failures=self.failures,
            normal_form=cast(RelationNormalForm | None, self.normal_form),
        )


class DisclosureNormalForm(FrozenModel):
    relation_kind: Literal["disclosure"] = "disclosure"
    relation_id: Identifier
    source_security_ids: tuple[SecurityID, ...] = Field(min_length=1)
    observer_security_ids: tuple[SecurityID, ...] = Field(min_length=1)
    sensitivity: Sensitivity
    privacy: Privacy
    direct_user_action: DirectUserAction | None = None

    def identity_payload(self) -> dict[str, object]:
        return {
            "kind": "disclosure",
            "source_security_ids": self.source_security_ids,
            "observer_security_ids": self.observer_security_ids,
            "sensitivity": self.sensitivity.value,
            "privacy": self.privacy.value,
            "direct_user_action": (
                self.direct_user_action.model_dump(mode="json")
                if self.direct_user_action is not None
                else None
            ),
        }

    def verify_identity(self) -> bool:
        return self.relation_id == f"relation:v3:{_digest(self.identity_payload())}"

    @classmethod
    def compose(
        cls,
        *,
        sources: tuple[SecurityObject, ...],
        observers: tuple[SecurityObject, ...],
        direct_user_action: DirectUserAction | None = None,
        active_interaction: DirectUserInteraction | None = None,
        operation: OperationReference | None = None,
        effect_profile_id: str | None = None,
        additional_observers: tuple[SecurityObject, ...] = (),
    ) -> CompositionResult[DisclosureNormalForm]:
        source_ids = tuple(sorted({item.security_id for item in sources}))
        observer_ids = tuple(item.security_id for item in observers)
        prospective = {
            "kind": "disclosure",
            "source_security_ids": source_ids,
            "observer_security_ids": observer_ids,
            "operation": operation.model_dump(mode="json") if operation is not None else None,
            "effect_profile_id": effect_profile_id,
        }
        relation_id = f"candidate:v3:{_digest(prospective)}"
        failures: list[SecurityFailure] = []
        if not sources:
            failures.append(SecurityFailure(code="missing_disclosure_source"))
        if not observers:
            failures.append(SecurityFailure(code="missing_disclosure_observer"))
        for source in sources:
            if not source.verify_binding() or source.sensitivity is None:
                failures.append(
                    SecurityFailure(code="missing_sensitivity", security_ids=(source.security_id,))
                )
        for observer in observers:
            if not observer.verify_binding() or observer.privacy is None:
                failures.append(
                    SecurityFailure(code="missing_privacy", security_ids=(observer.security_id,))
                )
        if failures:
            return CompositionResult(relation_id, "disclosure", None, tuple(failures))
        sensitivity = Sensitivity(
            max(_rank(cast(Sensitivity, item.sensitivity)) for item in sources)
        )
        privacy = Privacy(min(_rank(cast(Privacy, item.privacy)) for item in observers))
        action_covers = False
        if direct_user_action is not None:
            action_covers = len(sources) == 1 and direct_user_action.covers(
                source=sources[0],
                active_interaction=active_interaction,
                operation=operation,
                effect_profile_id=effect_profile_id,
                additional_observers=additional_observers,
            )
            if not action_covers:
                failures.append(
                    SecurityFailure(
                        code="invalid_direct_user_action",
                        security_ids=(*source_ids, *observer_ids),
                    )
                )
        if _rank(sensitivity) > _rank(privacy) and not action_covers:
            failures.append(
                SecurityFailure(
                    code="privacy_below_sensitivity",
                    security_ids=(*source_ids, *observer_ids),
                )
            )
        if failures:
            return CompositionResult(relation_id, "disclosure", None, tuple(failures))
        payload = {
            "kind": "disclosure",
            "source_security_ids": source_ids,
            "observer_security_ids": observer_ids,
            "sensitivity": sensitivity.value,
            "privacy": privacy.value,
            "direct_user_action": (
                direct_user_action.model_dump(mode="json") if action_covers else None
            ),
        }
        normal = cls(
            relation_id=f"relation:v3:{_digest(payload)}",
            source_security_ids=source_ids,
            observer_security_ids=observer_ids,
            sensitivity=sensitivity,
            privacy=privacy,
            direct_user_action=direct_user_action if action_covers else None,
        )
        return CompositionResult(normal.relation_id, "disclosure", normal)

    def with_source(self, source: SecurityObject) -> CompositionResult[DisclosureNormalForm]:
        if source.sensitivity is None or not source.verify_binding():
            return CompositionResult(
                self.relation_id,
                "disclosure",
                None,
                (SecurityFailure(code="missing_sensitivity", security_ids=(source.security_id,)),),
            )
        source_ids = tuple(sorted({*self.source_security_ids, source.security_id}))
        sensitivity = Sensitivity(max(_rank(self.sensitivity), _rank(source.sensitivity)))
        payload = {
            "kind": "disclosure",
            "source_security_ids": source_ids,
            "observer_security_ids": self.observer_security_ids,
            "sensitivity": sensitivity.value,
            "privacy": self.privacy.value,
            "direct_user_action": None,
        }
        relation_id = f"relation:v3:{_digest(payload)}"
        if _rank(sensitivity) > _rank(self.privacy):
            return CompositionResult(
                relation_id,
                "disclosure",
                None,
                (SecurityFailure(code="privacy_below_sensitivity", security_ids=source_ids),),
            )
        return CompositionResult(
            relation_id,
            "disclosure",
            DisclosureNormalForm(
                relation_id=relation_id,
                source_security_ids=source_ids,
                observer_security_ids=self.observer_security_ids,
                sensitivity=sensitivity,
                privacy=self.privacy,
            ),
        )

    def with_observer(self, observer: SecurityObject) -> CompositionResult[DisclosureNormalForm]:
        if observer.privacy is None or not observer.verify_binding():
            return CompositionResult(
                self.relation_id,
                "disclosure",
                None,
                (SecurityFailure(code="missing_privacy", security_ids=(observer.security_id,)),),
            )
        observer_ids = (*self.observer_security_ids, observer.security_id)
        privacy = Privacy(min(_rank(self.privacy), _rank(observer.privacy)))
        payload = {
            "kind": "disclosure",
            "source_security_ids": self.source_security_ids,
            "observer_security_ids": observer_ids,
            "sensitivity": self.sensitivity.value,
            "privacy": privacy.value,
            "direct_user_action": None,
        }
        relation_id = f"relation:v3:{_digest(payload)}"
        if _rank(self.sensitivity) > _rank(privacy):
            return CompositionResult(
                relation_id,
                "disclosure",
                None,
                (SecurityFailure(code="privacy_below_sensitivity", security_ids=observer_ids),),
            )
        return CompositionResult(
            relation_id,
            "disclosure",
            DisclosureNormalForm(
                relation_id=relation_id,
                source_security_ids=self.source_security_ids,
                observer_security_ids=observer_ids,
                sensitivity=self.sensitivity,
                privacy=privacy,
            ),
        )


class ControlNormalForm(FrozenModel):
    relation_kind: Literal["control"] = "control"
    relation_id: Identifier
    profile: EffectProfile
    controller_security_ids: tuple[SecurityID, ...]
    controller_integrity: Integrity

    @property
    def risk(self) -> Risk:
        return self.profile.risk

    @property
    def autonomy(self) -> Autonomy:
        return self.profile.autonomy

    @property
    def demand_rank(self) -> int:
        return min(_rank(self.risk), _rank(self.autonomy))

    def identity_payload(self) -> dict[str, object]:
        return {
            "kind": "control",
            "profile": self.profile.model_dump(mode="json"),
            "controller_security_ids": self.controller_security_ids,
            "risk": self.risk.value,
            "autonomy": self.autonomy.value,
            "controller_integrity": self.controller_integrity.value,
        }

    def verify_identity(self) -> bool:
        return self.relation_id == f"relation:v3:{_digest(self.identity_payload())}"

    def with_controller(self, controller: SecurityObject) -> CompositionResult[ControlNormalForm]:
        if controller.integrity is None or not controller.verify_binding():
            return CompositionResult(
                self.relation_id,
                "control",
                None,
                (SecurityFailure(code="missing_integrity", security_ids=(controller.security_id,)),),
            )
        controller_ids = tuple(sorted({*self.controller_security_ids, controller.security_id}))
        integrity = Integrity(min(_rank(self.controller_integrity), _rank(controller.integrity)))
        candidate = ControlNormalForm(
            relation_id="pending",
            profile=self.profile,
            controller_security_ids=controller_ids,
            controller_integrity=integrity,
        )
        relation_id = f"relation:v3:{_digest(candidate.identity_payload())}"
        if self.demand_rank > _rank(integrity):
            return CompositionResult(
                relation_id,
                "control",
                None,
                (SecurityFailure(code="controller_integrity_below_demand", security_ids=controller_ids),),
            )
        return CompositionResult(
            relation_id,
            "control",
            candidate.model_copy(update={"relation_id": relation_id}),
        )

    @classmethod
    def compose(
        cls, *, profile: EffectProfile, controllers: tuple[SecurityObject, ...]
    ) -> CompositionResult[ControlNormalForm]:
        controller_ids = tuple(sorted({item.security_id for item in controllers}))
        prospective = {
            "kind": "control",
            "effect_profile_id": profile.effect_profile_id,
            "controller_security_ids": controller_ids,
        }
        relation_id = f"candidate:v3:{_digest(prospective)}"
        failures = tuple(
            SecurityFailure(code="missing_integrity", security_ids=(item.security_id,))
            for item in controllers
            if item.integrity is None or not item.verify_binding()
        )
        if not profile.verify_identity():
            failures = (*failures, SecurityFailure(code="invalid_effect_profile"))
        if failures:
            return CompositionResult(relation_id, "control", None, failures)
        integrity = Integrity(
            min((_rank(cast(Integrity, item.integrity)) for item in controllers), default=5)
        )
        payload = {
            "kind": "control",
            "profile": profile.model_dump(mode="json"),
            "controller_security_ids": controller_ids,
            "risk": profile.risk.value,
            "autonomy": profile.autonomy.value,
            "controller_integrity": integrity.value,
        }
        relation_id = f"relation:v3:{_digest(payload)}"
        if min(_rank(profile.risk), _rank(profile.autonomy)) > _rank(integrity):
            return CompositionResult(
                relation_id,
                "control",
                None,
                (SecurityFailure(code="controller_integrity_below_demand", security_ids=controller_ids),),
            )
        return CompositionResult(
            relation_id,
            "control",
            cls(
                relation_id=relation_id,
                profile=profile,
                controller_security_ids=controller_ids,
                controller_integrity=integrity,
            ),
        )


class EffectExecutionNormalForm(FrozenModel):
    relation_kind: Literal["effect_execution"] = "effect_execution"
    relation_id: Identifier
    profile: EffectProfile
    executor_security_ids: tuple[SecurityID, ...] = Field(min_length=1)
    executor_integrity: Integrity

    @property
    def operation(self) -> OperationReference:
        return self.profile.operation

    @property
    def risk(self) -> Risk:
        return self.profile.risk

    def identity_payload(self) -> dict[str, object]:
        return {
            "kind": "effect_execution",
            "profile": self.profile.model_dump(mode="json"),
            "executor_security_ids": self.executor_security_ids,
            "risk": self.risk.value,
            "executor_integrity": self.executor_integrity.value,
        }

    def verify_identity(self) -> bool:
        return self.relation_id == f"relation:v3:{_digest(self.identity_payload())}"

    def with_executor(self, executor: SecurityObject) -> CompositionResult[EffectExecutionNormalForm]:
        if executor.integrity is None or not executor.verify_binding():
            return CompositionResult(
                self.relation_id,
                "effect_execution",
                None,
                (SecurityFailure(code="missing_integrity", security_ids=(executor.security_id,)),),
            )
        executor_ids = tuple(sorted({*self.executor_security_ids, executor.security_id}))
        integrity = Integrity(min(_rank(self.executor_integrity), _rank(executor.integrity)))
        candidate = EffectExecutionNormalForm(
            relation_id="pending",
            profile=self.profile,
            executor_security_ids=executor_ids,
            executor_integrity=integrity,
        )
        relation_id = f"relation:v3:{_digest(candidate.identity_payload())}"
        if _rank(self.risk) > _rank(integrity):
            return CompositionResult(
                relation_id,
                "effect_execution",
                None,
                (SecurityFailure(code="executor_integrity_below_risk", security_ids=executor_ids),),
            )
        return CompositionResult(
            relation_id,
            "effect_execution",
            candidate.model_copy(update={"relation_id": relation_id}),
        )

    @classmethod
    def compose(
        cls, *, profile: EffectProfile, executors: tuple[SecurityObject, ...]
    ) -> CompositionResult[EffectExecutionNormalForm]:
        executor_ids = tuple(sorted({item.security_id for item in executors}))
        prospective = {
            "kind": "effect_execution",
            "effect_profile_id": profile.effect_profile_id,
            "executor_security_ids": executor_ids,
        }
        relation_id = f"candidate:v3:{_digest(prospective)}"
        failures: list[SecurityFailure] = []
        if not executors:
            failures.append(SecurityFailure(code="missing_effect_executor"))
        for executor in executors:
            if executor.integrity is None or not executor.verify_binding():
                failures.append(
                    SecurityFailure(code="missing_integrity", security_ids=(executor.security_id,))
                )
        if not profile.verify_identity():
            failures.append(SecurityFailure(code="invalid_effect_profile"))
        if failures:
            return CompositionResult(relation_id, "effect_execution", None, tuple(failures))
        integrity = Integrity(min(_rank(cast(Integrity, item.integrity)) for item in executors))
        payload = {
            "kind": "effect_execution",
            "profile": profile.model_dump(mode="json"),
            "executor_security_ids": executor_ids,
            "risk": profile.risk.value,
            "executor_integrity": integrity.value,
        }
        relation_id = f"relation:v3:{_digest(payload)}"
        if _rank(profile.risk) > _rank(integrity):
            return CompositionResult(
                relation_id,
                "effect_execution",
                None,
                (SecurityFailure(code="executor_integrity_below_risk", security_ids=executor_ids),),
            )
        return CompositionResult(
            relation_id,
            "effect_execution",
            cls(
                relation_id=relation_id,
                profile=profile,
                executor_security_ids=executor_ids,
                executor_integrity=integrity,
            ),
        )


RelationNormalForm = DisclosureNormalForm | ControlNormalForm | EffectExecutionNormalForm


class RelationDecision(FrozenModel):
    """Ephemeral diagnostic projection of one composition result."""

    relation_id: Identifier
    relation_kind: Literal["disclosure", "control", "effect_execution"]
    admissible: bool
    algebra_version: Literal["3"] = "3"
    failures: tuple[SecurityFailure, ...] = ()
    normal_form: RelationNormalForm | None = None

    @property
    def failure_codes(self) -> tuple[str, ...]:
        return tuple(item.code for item in self.failures)


class SecurityEvidence(FrozenModel):
    """Current immutable security facts supplied to an execution boundary; not history."""

    objects: tuple[SecurityObject, ...] = ()

    @model_validator(mode="after")
    def canonicalize_objects(self) -> SecurityEvidence:
        unique: dict[str, SecurityObject] = {}
        conflicts: list[SecurityObject] = []
        for item in self.objects:
            previous = unique.get(item.security_id)
            if previous is None:
                unique[item.security_id] = item
            elif previous != item:
                conflicts.append(item)
        ordered = sorted(
            (*unique.values(), *conflicts),
            key=lambda item: (item.security_id, _digest(item.model_dump(mode="json"))),
        )
        object.__setattr__(self, "objects", tuple(ordered))
        return self

    def resolve(self, security_id: str) -> SecurityObject | None:
        matches = [item for item in self.objects if item.security_id == security_id]
        return matches[0] if len(matches) == 1 else None

    def extend(self, *, objects: tuple[SecurityObject, ...] = ()) -> SecurityEvidence:
        return SecurityEvidence(objects=(*self.objects, *objects))

    def merge(self, *evidence: SecurityEvidence) -> SecurityEvidence:
        objects = list(self.objects)
        for item in evidence:
            objects.extend(item.objects)
        return SecurityEvidence(objects=tuple(objects))


def audit_security_evidence(evidence: SecurityEvidence) -> tuple[SecurityFailure, ...]:
    """Validate carried scope facts only; it does not reconstruct transactions."""

    failures: list[SecurityFailure] = []
    seen: dict[str, SecurityObject] = {}
    for obj in evidence.objects:
        previous = seen.get(obj.security_id)
        if previous is not None and previous != obj:
            failures.append(
                SecurityFailure(code="security_id_conflict", security_ids=(obj.security_id,))
            )
        else:
            seen[obj.security_id] = obj
        if not obj.verify_binding():
            failures.append(
                SecurityFailure(code="invalid_security_binding", security_ids=(obj.security_id,))
            )
    return tuple(failures)
