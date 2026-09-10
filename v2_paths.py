from pathlib import Path
R=Path(__file__).parent
def read(p):return (R/p).read_text()
def write(p,s):(R/p).write_text(s)
for p in (R/'src').rglob('*.py'):
    s=p.read_text().replace('path_security_ids','boundary_security_ids').replace('path_privacy','boundary_privacy')
    s=s.replace('"validation"','"transform"').replace('unverified_validation_participation','unverified_transform_execution')
    p.write_text(s)
p='src/madre/security.py';s=read(p)
s=s.replace('        else:\n            return [\n                StructuralFailure(code="invalid_subject_values", security_ids=(obj.security_id,))', '        elif isinstance(values, BoundarySecurityValues):\n            fields = (("privacy_capacity", values.privacy_capacity),)\n        elif isinstance(values, TransformSecurityValues):\n            fields = ()\n        else:\n            return [\n                StructuralFailure(code="invalid_subject_values", security_ids=(obj.security_id,))')
write(p,s)
p='src/madre_sdk/security.py';s=read(p).replace('    BindingEvidence,','    BindingEvidence,\n    BoundarySecurityValues,\n    RiskEnvelope,')
s=s.replace('    privacy: OrdinarySecurityLevel,\n','').replace('ParticipantSecurityValues(privacy=privacy, assurance=assurance)','ParticipantSecurityValues(assurance=assurance)')
s=s.replace('    risk: OrdinarySecurityLevel,','    control_risk: OrdinarySecurityLevel,\n    effect_risk: OrdinarySecurityLevel,')
s=s.replace('    privacy: OrdinarySecurityLevel | None = None,','    disclosure_boundaries: tuple[SecurityObject, ...] = (),\n    controllers: tuple[SecurityObject, ...] = (),\n    executors: tuple[SecurityObject, ...] = (),\n    input_controls: bool = True,\n    caller_controls: bool = True,')
s=s.replace('    discloses_material: bool = False,\n','')
s=s.replace('risk=risk,','risk=RiskEnvelope(control_risk=control_risk, effect_risk=effect_risk),')
s=s.replace('            privacy=privacy,','            disclosure_boundary_ids=tuple(x.security_id for x in disclosure_boundaries),\n            controller_security_ids=tuple(x.security_id for x in controllers),\n            executor_security_ids=tuple(x.security_id for x in executors),\n            input_controls=input_controls, caller_controls=caller_controls,')
s=s.replace('        discloses_material=discloses_material,','        participants=(*disclosure_boundaries, *controllers, *executors),')
s+='''

def disclosure_boundary(*, owner_module_id: str, boundary_id: str,
    privacy_capacity: OrdinarySecurityLevel, publication_revision: str = "1",
    binding_evidence: tuple[BindingEvidence, ...] = ()) -> SecurityObject:
    return SecurityObject.issue(subject_ref=SecuritySubjectRef(
        owner_module_id=owner_module_id, subject_kind="disclosure_boundary",
        publication_revision=publication_revision, local_id=boundary_id),
        values=BoundarySecurityValues(privacy_capacity=privacy_capacity), binding_evidence=binding_evidence)
'''
write(p,s)
p='src/madre_sdk/semantic.py';s=read(p).replace('    InvocationContext,','    InvocationContext,\n    EndpointBinding,\n    SecuritySubjectRef,')
s=s.replace('def security(self) -> SecurityObject:', 'def binding(self) -> EndpointBinding:')
s=s.replace('endpoint_security','endpoint_binding')
s=s.replace('        endpoint_binding: SecurityObject | None = None,','        disclosure_boundaries: tuple[SecurityObject, ...],\n        endpoint_binding: EndpointBinding | None = None,')
start=s.index('        if participant_values.privacy is None')
end=s.index('        self.materials = ',start)
s=s[:start]+'''        self.endpoint_binding = endpoint_binding or EndpointBinding(
            subject_ref=SecuritySubjectRef(owner_module_id=module_id, subject_kind="endpoint",
                publication_revision=version, local_id=f"{module_id}:endpoint"),
            disclosure_boundaries=disclosure_boundaries)
        if self.endpoint_binding.disclosure_boundaries != disclosure_boundaries:
            raise ValueError("Module boundaries must match attachment")
        InvocationContext(module=self.security, endpoint=self.endpoint_binding)
''' +s[end:]
s=s.replace('objects=(self.security, agent.security, self.endpoint_binding)', 'objects=(self.security, agent.security, *self.endpoint_binding.disclosure_boundaries)')
s=s.replace('objects=(self.security, self.endpoint_binding, profile.security)', 'objects=(self.security, *self.endpoint_binding.disclosure_boundaries, profile.security, *profile.participants)')
s=s.replace('endpoint=self.endpoint_binding, operation=profile.operation','endpoint=self.endpoint_binding, operation=profile.operation, behavior=profile.security')
write(p,s)
p='src/madre/interfaces.py';s=read(p).replace('    InvocationContext,','    InvocationContext,\n    EndpointBinding,')
# import may be single-line
if '    EndpointBinding,' not in s:s=s.replace('from madre.security import ', 'from madre.security import EndpointBinding, ')
s=s.replace('def security(self) -> SecurityObject:', 'def binding(self) -> EndpointBinding:')
write(p,s)
p='src/madre/broker.py';s=read(p).replace('    InvocationContext,','    InvocationContext,\n    EndpointBinding,\n    EffectProfileSecurityValues,')
s=s.replace('ModuleManifest, SecurityObject,','ModuleManifest, EndpointBinding,')
s=s.replace('endpoint.security','endpoint.binding').replace('endpoint_security','endpoint_binding')
s=s.replace('module_id: str, security: SecurityObject','module_id: str, security: EndpointBinding')
s=s.replace('tuple[AgentEndpoint, SecurityObject]','tuple[AgentEndpoint, EndpointBinding]').replace('tuple[OperationEndpoint, SecurityObject]','tuple[OperationEndpoint, EndpointBinding]')
s=s.replace('endpoint=endpoint_binding, operation=profile.operation','endpoint=endpoint_binding, operation=profile.operation, behavior=profile.security')
s=s.replace('boundary_security_ids=tuple(obj.security_id for obj in invocation.objects)','boundary_security_ids=invocation.boundary_security_ids')
s=s.replace('path = [obj.security_id for obj in invocation.objects]\n        if profile.discloses_material:\n            path.append(profile.security.security_id)', 'values = profile.security.values\n        assert isinstance(values, EffectProfileSecurityValues)\n        prospective = prospective.extend(objects=profile.participants)\n        path = [*invocation.boundary_security_ids, *values.disclosure_boundary_ids]')
s=s.replace('                    material.security.security_id,\n                    requester.selector_security_id,', '                    *((material.security.security_id,) if values.input_controls else ()),\n                    *((requester.selector_security_id,) if values.caller_controls else ()),\n                    *values.controller_security_ids,')
s=s.replace('executor_security_ids=tuple(obj.security_id for obj in invocation.objects)', 'executor_security_ids=tuple(x.security_id for x in invocation.endpoint.executors) + values.executor_security_ids')
s=s.replace('requester.recipient_security_ids','requester.boundary_security_ids')
s=s.replace('if not on_output_path or set(relation.validator_security_ids) != actual:', 'if (not on_output_path or invocation.behavior is None\n                    or relation.transform_security_id != invocation.behavior.security_id\n                    or invocation.behavior.subject_ref.subject_kind != "transform"):' )
write(p,s)
# No ordinary helper may assert a transformation contract completed.
p='src/madre_sdk/material.py';s=read(p);start=s.index('    def validated(');start=s.rfind('\n',0,start);end=s.index('    def transient(',start)
s=s[:start]+s[end:];write(p,s)
# Boundary placement at physical inference routes.
p='src/madre/capabilities.py';s=read(p).replace('from pydantic import JsonValue','from pydantic import JsonValue, Field').replace('    CapabilitySecurityValues,','    CapabilitySecurityValues,\n    BoundarySecurityValues,')
s=s.replace('    security: SecurityObject','    disclosure_boundaries: tuple[SecurityObject, ...] = Field(min_length=1)\n    security: SecurityObject',1)
s=s.replace('if values.privacy is None or values.assurance is None:', 'if values.assurance is None:')
s=s.replace('        self._adapters[descriptor.id] = adapter','        for boundary in descriptor.disclosure_boundaries:\n            if not boundary.verify_binding() or not isinstance(boundary.values, BoundarySecurityValues):\n                raise ValueError("Capability requires bound disclosure boundaries")\n        self._adapters[descriptor.id] = adapter')
write(p,s)
p='src/madre/runtime.py';s=read(p).replace('objects=(descriptor.security,)','objects=(descriptor.security, *descriptor.disclosure_boundaries)')
s=s.replace('boundary_security_ids=(descriptor.security.security_id,)','boundary_security_ids=tuple(x.security_id for x in descriptor.disclosure_boundaries)');write(p,s)
p='src/madre/service.py';s=read(p).replace('    BindingEvidence,','    BindingEvidence,\n    BoundarySecurityValues,').replace('                privacy=config.privacy,\n','')
s=s.replace('            security=security,','''            security=security,
            disclosure_boundaries=(SecurityObject.issue(
                subject_ref=SecuritySubjectRef(owner_module_id="madre.platform",
                    subject_kind="disclosure_boundary", publication_revision="1", local_id=f"{capability_id}:boundary"),
                values=BoundarySecurityValues(privacy_capacity=config.privacy_capacity),
                binding_evidence=security.binding_evidence),),''');write(p,s)
p='src/madre/adapters/openai.py';write(p,read(p).replace('    privacy:', '    privacy_capacity:'))
p='src/madre_core/core.py';s=read(p).replace('    ModuleServices,','    ModuleServices,\n    disclosure_boundary,').replace('            privacy=SecurityLevel.LEVEL_5,\n','')
s=s.replace('            services=ModuleServices(','''            disclosure_boundaries=(disclosure_boundary(owner_module_id=module_id,
                boundary_id="core-local", privacy_capacity=SecurityLevel.LEVEL_5),),
            services=ModuleServices(''');write(p,s)
p='src/madre_sdk/__init__.py';s=read(p).replace('    CapabilitySecurityValues,','    CapabilitySecurityValues,\n    BoundarySecurityValues,\n    EndpointBinding,\n    RiskEnvelope,\n    TransformSecurityValues,')
s=s.replace('    participant_security,','    participant_security,\n    disclosure_boundary,')
s=s.replace('    "CapabilitySecurityValues",','    "CapabilitySecurityValues",\n    "BoundarySecurityValues",\n    "EndpointBinding",\n    "RiskEnvelope",\n    "TransformSecurityValues",\n    "disclosure_boundary",');write(p,s)
