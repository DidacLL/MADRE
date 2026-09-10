from pathlib import Path
import re

ROOT = Path(__file__).parent
def read(p): return (ROOT / p).read_text(encoding='utf-8')
def write(p, s): (ROOT / p).write_text(s, encoding='utf-8')
def change(p, a, b):
    s = read(p)
    if a not in s: raise ValueError((p, a[:100]))
    write(p, s.replace(a,b))

# Vocabulary replacement, including consumers and fixtures. No V1 aliases.
for folder in ('src', 'tests'):
    for p in (ROOT / folder).rglob('*.py'):
        s = p.read_text(encoding='utf-8')
        s = s.replace('integrities','assurances').replace('integrity','assurance').replace('Integrity','Assurance')
        p.write_text(s, encoding='utf-8')

p='src/madre/security.py'
s=read(p).replace('"effect_profile",\n]', '"effect_profile",\n    "disclosure_boundary",\n    "transform",\n]')
s=s.replace('    privacy: OrdinarySecurityLevel | None = None\n','')
start=s.index('class EffectProfileSecurityValues')
end=s.index('\n\nSecurityValues',start)
s=s[:start]+'''class BoundarySecurityValues(FrozenModel):
    kind: Literal["boundary"] = "boundary"
    privacy_capacity: OrdinarySecurityLevel


class TransformSecurityValues(FrozenModel):
    kind: Literal["transform"] = "transform"
    contract_id: Identifier
    evidence_schema: Identifier


class RiskEnvelope(FrozenModel):
    control_risk: OrdinarySecurityLevel
    effect_risk: OrdinarySecurityLevel

    @model_validator(mode="after")
    def ordered(self) -> RiskEnvelope:
        if self.control_risk > self.effect_risk:
            raise ValueError("control_risk must not exceed effect_risk")
        return self


class EffectProfileSecurityValues(FrozenModel):
    kind: Literal["effect_profile"] = "effect_profile"
    risk: RiskEnvelope
    autonomy: OrdinarySecurityLevel
    assurance: OrdinarySecurityLevel
    # These describe this immutable profile's public machine-control contract.
    input_controls: bool = True
    caller_controls: bool = True
    controller_security_ids: tuple[SecurityID, ...] = ()
    executor_security_ids: tuple[SecurityID, ...] = ()
    disclosure_boundary_ids: tuple[SecurityID, ...] = ()

    @field_validator("controller_security_ids", "executor_security_ids")
    @classmethod
    def canonical_ids(cls, value: tuple[str, ...]) -> tuple[str, ...]:
        return tuple(sorted(set(value)))
''' +s[end:]
s=s.replace('    | EffectProfileSecurityValues,','    | EffectProfileSecurityValues\n    | BoundarySecurityValues\n    | TransformSecurityValues,')
s=s.replace('            "effect_profile": EffectProfileSecurityValues,','            "effect_profile": EffectProfileSecurityValues,\n            "disclosure_boundary": BoundarySecurityValues,\n            "transform": TransformSecurityValues,')
s=s.replace('security:v1:', 'security:v2:').replace('transition:v1:', 'transition:v2:').replace('derivation:v1:', 'derivation:v2:')
start=s.index('class InvocationContext')
end=s.index('\n\nclass EffectProfile(',start)
s=s[:start]+'''class EndpointBinding(FrozenModel):
    """Routing attachment; only explicitly declared behavior has security operands."""
    subject_ref: SecuritySubjectRef
    disclosure_boundaries: tuple[SecurityObject, ...] = Field(min_length=1)
    producers: tuple[SecurityObject, ...] = ()
    executors: tuple[SecurityObject, ...] = ()

    @model_validator(mode="after")
    def valid(self) -> EndpointBinding:
        if self.subject_ref.subject_kind != "endpoint":
            raise ValueError("attachment must identify an endpoint")
        for boundary in self.disclosure_boundaries:
            if not boundary.verify_binding() or not isinstance(boundary.values, BoundarySecurityValues):
                raise ValueError("attachment requires bound disclosure boundaries")
        for participant in (*self.producers, *self.executors):
            if not participant.verify_binding() or _assurance_value(participant) is None:
                raise ValueError("attachment behavior requires bound Assurance")
        return self


class InvocationContext(FrozenModel):
    """Established execution identity and boundary, independent of carried history."""
    module: SecurityObject
    agent: SecurityObject | None = None
    endpoint: EndpointBinding
    operation: OperationReference | None = None
    behavior: SecurityObject | None = None

    @model_validator(mode="after")
    def validate_participants(self) -> InvocationContext:
        ref = self.module.subject_ref
        if ref.subject_kind != "module" or ref.local_id != ref.owner_module_id:
            raise ValueError("invalid invocation Module")
        if self.agent is not None and self.operation is not None:
            raise ValueError("invocation cannot be both Agent and Operation")
        for obj in (self.module, self.agent, self.behavior):
            if obj is not None and (not obj.verify_binding()
                or obj.subject_ref.owner_module_id != ref.owner_module_id
                or obj.subject_ref.publication_revision != ref.publication_revision):
                raise ValueError("invocation must match exact publication")
        endpoint_ref = self.endpoint.subject_ref
        if (endpoint_ref.owner_module_id != ref.owner_module_id
            or endpoint_ref.publication_revision != ref.publication_revision):
            raise ValueError("endpoint must match exact publication")
        if self.agent is not None and self.agent.subject_ref.subject_kind != "agent":
            raise ValueError("invalid active Agent")
        if self.operation is not None and (self.operation.module_id != ref.owner_module_id
            or self.operation.publication_revision != ref.publication_revision):
            raise ValueError("invalid active Operation")
        return self

    @property
    def module_id(self) -> str:
        return self.module.subject_ref.owner_module_id

    @property
    def actor(self) -> SecurityObject:
        return self.agent or self.behavior or self.module

    @property
    def objects(self) -> tuple[SecurityObject, ...]:
        return (self.module, self.actor, *self.endpoint.disclosure_boundaries,
                *self.endpoint.producers, *self.endpoint.executors)

    @property
    def selector_security_id(self) -> SecurityID:
        return self.actor.security_id

    @property
    def boundary_security_ids(self) -> tuple[SecurityID, ...]:
        return tuple(obj.security_id for obj in self.endpoint.disclosure_boundaries)

    @property
    def producer_security_ids(self) -> tuple[SecurityID, ...]:
        return tuple(sorted({self.actor.security_id, *(obj.security_id for obj in self.endpoint.producers)}))
''' +s[end:]
s=s.replace('    discloses_material: bool = False','    participants: tuple[SecurityObject, ...] = ()')
start=s.index('        if self.discloses_material')
end=s.index('        return self',start)
s=s[:start]+'''        supplied = {item.security_id: item for item in self.participants}
        for sid in (*values.controller_security_ids, *values.executor_security_ids,
                    *values.disclosure_boundary_ids):
            if sid not in supplied or not supplied[sid].verify_binding():
                raise ValueError("profile topology requires bound participants")
''' +s[end:]
s=s.replace('path_security_ids','boundary_security_ids').replace('path_privacy','boundary_privacy')
s=s.replace('DerivationKind = Literal["ordinary", "validation"]','DerivationKind = Literal["ordinary", "transform"]')
s=s.replace('    validator_security_ids: tuple[SecurityID, ...] = ()','    transform_security_id: SecurityID | None = None')
s=s.replace(', "validator_security_ids"','')
s=s.replace('"validator_security_ids": self.validator_security_ids','"transform_security_id": self.transform_security_id')
s=s.replace('        validator_security_ids: tuple[SecurityID, ...] = (),','        transform_security_id: SecurityID | None = None,')
s=s.replace('        validator_security_ids = cls.canonical_participants(validator_security_ids)\n','')
s=s.replace('"validator_security_ids": validator_security_ids','"transform_security_id": transform_security_id')
s=s.replace('validator_security_ids=validator_security_ids','transform_security_id=transform_security_id')
s=s.replace('    risk: OrdinarySecurityLevel | None\n','    risk: RiskEnvelope | None\n')
s=s.replace('Literal["1"] = "1"','Literal["2"] = "2"')
s=s.replace('    objects: tuple[SecurityObject, ...] = ()','    version: Literal["2"] = "2"\n    objects: tuple[SecurityObject, ...] = ()',1)
start=s.index('def _privacy_value')
end=s.index('\n\n',start)
s=s[:start]+'''def _privacy_value(obj: SecurityObject) -> OrdinarySecurityLevel | None:
    if isinstance(obj.values, BoundarySecurityValues):
        return obj.values.privacy_capacity
    return None
''' +s[end:]
s=s.replace('fields = (("privacy", values.privacy), ("assurance", values.assurance))','fields = (("assurance", values.assurance),)')
s=s.replace('("risk", values.risk),','("control_risk", values.risk.control_risk),\n                ("effect_risk", values.risk.effect_risk),')
s=s.replace('                ("privacy", values.privacy),\n','')
s=s.replace('            if participant.subject_ref.subject_kind not in {\n                "module",\n                "agent",\n                "endpoint",\n                "capability",\n                "effect_profile",\n            }:', '            if participant.subject_ref.subject_kind != "disclosure_boundary":')
s=s.replace('risk: OrdinarySecurityLevel | None = None','risk: RiskEnvelope | None = None')
s=s.replace('risk = _ordinary(profile.values.risk)','risk = profile.values.risk')
s=s.replace('("risk", profile.values.risk, risk),','("control_risk", risk.control_risk, _ordinary(risk.control_risk)),\n                ("effect_risk", risk.effect_risk, _ordinary(risk.effect_risk)),')
s=s.replace('SecurityLevel(min(int(risk), int(autonomy)))','SecurityLevel(risk.control_risk)')
s=s.replace('int(risk) > int(effect_assurance)','int(risk.effect_risk) > int(effect_assurance)')
s=s.replace('"capability",\n            },','"capability", "effect_profile",\n            },')
s=s.replace('allowed_kinds={"module", "agent", "endpoint", "capability"}', 'allowed_kinds={"module", "agent", "endpoint", "capability", "effect_profile"}')
# Derivation structure keeps ordinary constraints; contract execution is checked at execution boundaries.
s=s.replace('            validators = [index.get(item) for item in derivation.validator_security_ids]','            transforms = [index.get(derivation.transform_security_id)] if derivation.transform_security_id else []')
s=s.replace('                *derivation.validator_security_ids,','                *((derivation.transform_security_id,) if derivation.transform_security_id else ()),')
s=s.replace('(output, *sources, *producers, *validators)','(output, *sources, *producers, *transforms)')
s=s.replace('            concrete_validators = cast(list[SecurityObject], validators)','')
s=s.replace('not in {"module", "agent", "endpoint", "capability"}', 'not in {"module", "agent", "endpoint", "capability", "effect_profile", "transform"}')
s=s.replace('(*concrete_producers, *concrete_validators)','concrete_producers')
s=s.replace('if derivation.validator_security_ids:', 'if derivation.transform_security_id:').replace('ordinary-has-validators','ordinary-has-transform')
start=s.index('            else:\n                if not concrete_validators:')
end=s.index('        return failures',start)
s=s[:start]+'''            else:
                transform = index.get(derivation.transform_security_id or "")
                if transform is None or not isinstance(transform.values, TransformSecurityValues):
                    failures.append(StructuralFailure(code="invalid_derivation", detail="missing-transform-contract"))
                elif transform.security_id not in derivation.producer_security_ids:
                    failures.append(StructuralFailure(code="invalid_derivation", detail="missing-transform-producer"))
''' +s[end:]
write(p,s)
