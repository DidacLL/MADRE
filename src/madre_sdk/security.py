"""MADRE's standalone, intrinsically composable Security Algebra."""

from __future__ import annotations

from enum import Enum
from typing import Annotated, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator

Identifier = Annotated[str, Field(min_length=1)]


class FrozenValue(BaseModel):
    """Serializable immutable value used by the language-neutral public SDK."""

    model_config = ConfigDict(extra="forbid", frozen=True)


class _OrderedCarrier(Enum):
    @property
    def rank(self) -> int:
        return int(self.value)


class Sensitivity(_OrderedCarrier):
    S1 = 1
    S2 = 2
    S3 = 3
    S4 = 4
    S5 = 5


class Privacy(_OrderedCarrier):
    PUBLIC = 1
    UNKNOWN = 2
    LOCAL_PRIVATE = 3
    MODULE_PRIVATE = 4
    SECRET = 5


class Integrity(_OrderedCarrier):
    I1 = 1
    I2 = 2
    I3 = 3
    I4 = 4
    I5 = 5


class Risk(_OrderedCarrier):
    R1 = 1
    R2 = 2
    R3 = 3
    R4 = 4
    R5 = 5


class Autonomy(_OrderedCarrier):
    A1 = 1
    A2 = 2
    A3 = 3
    A4 = 4
    A5 = 5


class ScopeIdentity(FrozenValue):
    """Identity of one exact Module-owned or physical scope."""

    owner: Identifier
    name: Identifier
    revision: Identifier = "1"


class SecurityScope(FrozenValue):
    """Applicable security facts for exactly one identified scope."""

    identity: ScopeIdentity
    sensitivity: Sensitivity | None = None
    privacy: Privacy | None = None
    integrity: Integrity | None = None

    @model_validator(mode="after")
    def has_an_applicable_facet(self) -> Self:
        if all(value is None for value in (self.sensitivity, self.privacy, self.integrity)):
            raise ValueError("a SecurityScope must carry at least one applicable facet")
        return self


class SecuritySurface(FrozenValue):
    """Structural composition of the exact scopes exposed or reached together."""

    scopes: tuple[SecurityScope, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def has_unambiguous_scopes(self) -> Self:
        identities: dict[ScopeIdentity, SecurityScope] = {}
        for scope in self.scopes:
            previous = identities.setdefault(scope.identity, scope)
            if previous != scope:
                raise ValueError(f"conflicting facts for scope {scope.identity.name}")
        if len(identities) != len(self.scopes):
            raise ValueError("a SecuritySurface cannot repeat a scope")
        canonical = tuple(
            sorted(
                self.scopes,
                key=lambda scope: (
                    scope.identity.owner,
                    scope.identity.name,
                    scope.identity.revision,
                ),
            )
        )
        object.__setattr__(self, "scopes", canonical)
        return self

    @classmethod
    def from_scope(cls, scope: SecurityScope) -> Self:
        return cls(scopes=(scope,))

    @classmethod
    def compose(cls, *members: SecurityScope | SecuritySurface) -> Self:
        scopes: list[SecurityScope] = []
        by_identity: dict[ScopeIdentity, SecurityScope] = {}
        for member in members:
            additions = member.scopes if isinstance(member, SecuritySurface) else (member,)
            for scope in additions:
                previous = by_identity.get(scope.identity)
                if previous is not None:
                    if previous != scope:
                        raise ValueError(f"conflicting facts for scope {scope.identity.name}")
                    continue
                by_identity[scope.identity] = scope
                scopes.append(scope)
        return cls(scopes=tuple(scopes))

    def including(self, *members: SecurityScope | SecuritySurface) -> Self:
        return type(self).compose(self, *members)

    @property
    def sensitivity(self) -> Sensitivity | None:
        values = tuple(scope.sensitivity for scope in self.scopes if scope.sensitivity is not None)
        return max(values, key=lambda value: value.rank) if values else None

    @property
    def privacy(self) -> Privacy | None:
        values = tuple(scope.privacy for scope in self.scopes if scope.privacy is not None)
        return min(values, key=lambda value: value.rank) if values else None

    @property
    def integrity(self) -> Integrity | None:
        values = tuple(scope.integrity for scope in self.scopes if scope.integrity is not None)
        return min(values, key=lambda value: value.rank) if values else None


class EffectProfile(FrozenValue):
    """One bounded execution variant of one exact Module-owned Operation."""

    identity: ScopeIdentity
    operation: ScopeIdentity
    risk: Risk
    autonomy: Autonomy

    @model_validator(mode="after")
    def belongs_to_operation(self) -> Self:
        if self.identity.owner != self.operation.owner:
            raise ValueError("EffectProfile and Operation must have the same owner")
        return self


class SecurityMismatch(RuntimeError):
    """An attempted addition could not form a valid algebraic composite."""

    def __init__(self, relation: str, reason: str) -> None:
        self.relation = relation
        self.reason = reason
        super().__init__(f"{relation}: {reason}")


class Disclosure(FrozenValue):
    """The exact source/observer composition S_D <= P_D."""

    sources: SecuritySurface
    observers: SecuritySurface

    @model_validator(mode="after")
    def is_matchable(self) -> Self:
        sensitivity = self.sources.sensitivity
        privacy = self.observers.privacy
        if sensitivity is None:
            raise SecurityMismatch("disclosure", "an exposed source must carry Sensitivity")
        if privacy is None:
            raise SecurityMismatch("disclosure", "an observer must carry Privacy")
        if sensitivity.rank > privacy.rank:
            raise SecurityMismatch("disclosure", f"{sensitivity.name} exceeds {privacy.name}")
        return self

    @property
    def sensitivity(self) -> Sensitivity:
        value = self.sources.sensitivity
        assert value is not None
        return value

    @property
    def privacy(self) -> Privacy:
        value = self.observers.privacy
        assert value is not None
        return value

    def including_source(self, source: SecurityScope | SecuritySurface) -> Self:
        return type(self)(sources=self.sources.including(source), observers=self.observers)

    def including_observer(self, observer: SecurityScope | SecuritySurface) -> Self:
        return type(self)(sources=self.sources, observers=self.observers.including(observer))


class Control(FrozenValue):
    """The exact effect-control composition min(R, A) <= I_C."""

    profile: EffectProfile
    controllers: SecuritySurface | None = None

    @property
    def demand(self) -> int:
        return min(self.profile.risk.rank, self.profile.autonomy.rank)

    @property
    def controller_integrity(self) -> Integrity:
        if self.controllers is None:
            return Integrity.I5
        value = self.controllers.integrity
        if value is None:
            raise SecurityMismatch("control", "a non-user controller must carry Integrity")
        return value

    @model_validator(mode="after")
    def is_matchable(self) -> Self:
        integrity = self.controller_integrity
        if self.demand > integrity.rank:
            raise SecurityMismatch("control", f"demand {self.demand} exceeds {integrity.name}")
        return self

    def including_controller(self, controller: SecurityScope | SecuritySurface) -> Self:
        surface = (
            SecuritySurface.compose(controller)
            if self.controllers is None
            else self.controllers.including(controller)
        )
        return type(self)(profile=self.profile, controllers=surface)


class EffectExecution(FrozenValue):
    """The exact effect execution composition R <= I_E."""

    profile: EffectProfile
    executors: SecuritySurface

    @model_validator(mode="after")
    def is_matchable(self) -> Self:
        integrity = self.executors.integrity
        if integrity is None:
            raise SecurityMismatch("effect execution", "an executor must carry Integrity")
        if self.profile.risk.rank > integrity.rank:
            raise SecurityMismatch(
                "effect execution", f"{self.profile.risk.name} exceeds {integrity.name}"
            )
        return self

    @property
    def executor_integrity(self) -> Integrity:
        value = self.executors.integrity
        assert value is not None
        return value

    def including_executor(self, executor: SecurityScope | SecuritySurface) -> Self:
        return type(self)(profile=self.profile, executors=self.executors.including(executor))
