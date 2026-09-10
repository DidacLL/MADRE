from pathlib import Path
R=Path(__file__).parent
def rd(p):return (R/p).read_text()
def wr(p,s):(R/p).write_text(s)
p='src/madre/storage_work_records.py';wr(p,rd(p).replace('sqlite3.AssuranceError','sqlite3.IntegrityError'))
p='src/madre/storage_work_base.py';wr(p,rd(p).replace('*relation.validator_security_ids,','*((relation.transform_security_id,) if relation.transform_security_id else ()),') )
p='src/madre_core/core.py';wr(p,rd(p).replace('disclosure_boundary(owner_module_id=module_id,','disclosure_boundary(owner_module_id=CORE_MODULE_ID,'))
p='src/madre/registry.py';s=rd(p).replace('    SecurityTransition,','    SecurityTransition,\n    TransformSecurityValues,')
at=s.index('\n\nclass SkillDescriptor')
s=s[:at]+'''

class TransformContract(FrozenModel):
    id: Identifier
    module_id: Identifier
    security: SecurityObject

    @model_validator(mode="after")
    def bound(self) -> TransformContract:
        ref = self.security.subject_ref
        if (not self.security.verify_binding() or not isinstance(self.security.values, TransformSecurityValues)
            or ref.subject_kind != "transform" or ref.local_id != self.id
            or ref.owner_module_id != self.module_id):
            raise ValueError("invalid TransformContract binding")
        return self
''' +s[at:]
s=s.replace('    operations: tuple[OperationDescriptor, ...] = ()','    operations: tuple[OperationDescriptor, ...] = ()\n    transforms: tuple[TransformContract, ...] = ()')
s=s.replace('        for descriptor in self.operations:', '''        transform_ids = [item.id for item in self.transforms]
        if len(transform_ids) != len(set(transform_ids)):
            raise ValueError("duplicate transform identity")
        for transform in self.transforms:
            if transform.module_id != self.module_id or transform.security.subject_ref.publication_revision != self.version:
                raise ValueError("transform must match Module publication")
        for descriptor in self.operations:''')
wr(p,s)
p='src/madre/interfaces.py';s=rd(p);s+='''

class TransformEndpoint(Protocol):
    @property
    def boundary(self) -> ExecutionBoundary: ...
    @property
    def binding(self) -> EndpointBinding: ...
    async def invoke_transform(self, transform_id: str, invocation: InvocationContext,
        security: SecurityHistory, material: TransientMaterial) -> TransientMaterial: ...


class TransformEndpointRegistration(Protocol):
    def attach_transform_endpoint(self, module_id: str, endpoint: TransformEndpoint) -> None: ...


class TransformBrokering(Protocol):
    async def invoke_transform(self, requester: InvocationContext, security: SecurityHistory,
        target_module_id: str, transform_id: str, material: TransientMaterial) -> TransientMaterial: ...
''';wr(p,s)
p='src/madre/broker.py';s=rd(p).replace('from madre.interfaces import AgentEndpoint, OperationEndpoint','from madre.interfaces import AgentEndpoint, OperationEndpoint, TransformEndpoint')
s=s.replace('    OperationDescriptor,','    OperationDescriptor,\n    TransformContract,')
s=s.replace('        self._agent_endpoints:', '        self._transform_endpoints: dict[str, tuple[ModuleManifest, EndpointBinding, TransformEndpoint]] = {}\n        self._agent_endpoints:')
at=s.index('    async def invoke_agent(')
s=s[:at]+'''    def attach_transform_endpoint(self, module_id: str, endpoint: TransformEndpoint) -> None:
        binding = endpoint.binding
        self._transform_endpoints[module_id] = (self._attachment(module_id, binding), binding, endpoint)

    async def invoke_transform(self, requester: InvocationContext, security: SecurityHistory,
        target_module_id: str, transform_id: str, material: TransientMaterial) -> TransientMaterial:
        manifest = self._registry.get_module(target_module_id)
        contract = next((x for x in manifest.transforms if x.id == transform_id), None) if manifest else None
        attachment = self._transform_endpoints.get(target_module_id)
        if contract is None or manifest is None:
            raise PublishedTargetNotFound(transform_id)
        if attachment is None:
            raise ModuleEndpointUnavailable(target_module_id)
        publication, binding, endpoint = attachment
        if publication != manifest or endpoint.binding != binding:
            raise ModuleEndpointUnavailable("stale transform attachment")
        invocation = InvocationContext(module=manifest.security, endpoint=binding, behavior=contract.security)
        invocation_id, history = self._prepare_agent_input(requester=requester, invocation=invocation,
            security=security, descriptor=contract, endpoint=endpoint, material=material,
            crossing_kind="transform")
        output = await endpoint.invoke_transform(transform_id, invocation, history, material)
        return self._finish_output(invocation_id=invocation_id, crossing_kind="transform",
            requester=requester, invocation=invocation, target_module_id=target_module_id,
            target_id=transform_id, boundary=endpoint.boundary, input_history=history,
            input_material_security_id=material.security.security_id, output=output)

''' +s[at:]
# Only preparation is shared, without calling transforms Agents.
start=s.index('    def _prepare_agent_input(');end=s.index('    def _prepare_operation_input',start)
part=s[start:end].replace('descriptor: AgentDescriptor','descriptor: AgentDescriptor | TransformContract').replace('endpoint: AgentEndpoint','endpoint: AgentEndpoint | TransformEndpoint')
part=part.replace('        material: TransientMaterial,','        material: TransientMaterial,\n        crossing_kind: str = "agent",')
part=part.replace('            "agent",','            crossing_kind,').replace('crossing_kind="agent-input"','crossing_kind=f"{crossing_kind}-input"')
s=s[:start]+part+s[end:];wr(p,s)
p='src/madre_sdk/services.py';s=rd(p).replace('    AgentBrokering,','    AgentBrokering,\n    TransformBrokering,')
at=s.index('\n\nclass CoreSelection')
s=s[:at]+'''

class TransformBrokerClient(_ExecutionClient):
    def __init__(self, *, broker: TransformBrokering) -> None:
        self._broker = broker

    async def invoke(self, module_id: str, transform_id: str, material: Material) -> TransientMaterial:
        invocation = self._active_invocation()
        return await self._broker.invoke_transform(invocation,
            material.security_history.extend(objects=invocation.objects), module_id, transform_id,
            material.transient())
''' +s[at:]
s=s.replace('    operations: OperationBrokerClient | None = None','    operations: OperationBrokerClient | None = None\n    transforms: TransformBrokerClient | None = None')
s=s.replace('                inference=self.inference._bind(binding)', '                transforms=self.transforms._bind(binding) if self.transforms is not None else None,\n                inference=self.inference._bind(binding)')
wr(p,s)
p='src/madre_sdk/semantic.py';s=rd(p)
s=s.replace('    ModuleRegistration,','    ModuleRegistration,\n    TransformEndpointRegistration,\n    TransformEndpoint,')
s=s.replace('    AgentDescriptor,','    AgentDescriptor,\n    TransformContract,')
s=s.replace('    SecuritySubjectRef,','    SecuritySubjectRef,\n    SecurityDerivation,\n    OrdinarySecurityLevel,\n    FrozenModel,')
s=s.replace('from madre_sdk.material import Material, MaterialRepository','from madre_sdk.material import Material, MaterialRepository, Artifact')
s=s.replace('        operations: Sequence[Operation] = (),','        operations: Sequence[Operation] = (),\n        transforms: Sequence[Transform] = (),')
s=s.replace('        self.operations = tuple(operations)','        self.operations = tuple(operations)\n        self.transforms = tuple(transforms)')
s=s.replace('            operations=tuple(operation.descriptor(self.module_id) for operation in self.operations),','            operations=tuple(operation.descriptor(self.module_id) for operation in self.operations),\n            transforms=tuple(transform.contract for transform in self.transforms),')
s=s.replace('        self._agent_endpoint = _AgentEndpoint(self)','        self._transform_endpoint = _TransformEndpoint(self)\n        self._agent_endpoint = _AgentEndpoint(self)')
at=s.index('    def register_agent_endpoint(')
s=s[:at]+'''    def register_transform_endpoint(self, registration: TransformEndpointRegistration) -> None:
        if self.transforms:
            registration.attach_transform_endpoint(self.module_id, self._transform_endpoint)

    async def execute_transform(self, transform_id: str, material: TransientMaterial, *,
        invocation: InvocationContext, security: SecurityHistory) -> Material:
        transform = next((t for t in self.transforms if t.contract.id == transform_id), None)
        if transform is None:
            raise KeyError(transform_id)
        expected = InvocationContext(module=self.security, endpoint=self.endpoint_binding,
            behavior=transform.contract.security)
        if invocation != expected:
            raise ValueError("transform invocation does not match publication")
        with self._services._execution(expected) as services:
            result = await transform.behavior.execute(material=material, services=services)
        output = Artifact.create(id=result.representation_id, owner_module_id=self.module_id,
            payload=result.payload, sensitivity=result.sensitivity, assurance=result.assurance,
            publication_revision=self.version)
        relation = SecurityDerivation.issue(kind="transform", output_security_id=output.security.security_id,
            source_security_ids=(material.security.security_id,),
            producer_security_ids=expected.producer_security_ids,
            transform_security_id=transform.contract.security.security_id)
        history = security.merge(material.history).extend(objects=(*expected.objects, output.security),
            derivations=(relation,))
        return output.model_copy(update={"security_history": history})

''' +s[at:]
at=s.index('\n\nclass _AgentEndpoint')
s=s[:at]+'''

class TransformOutput(FrozenModel):
    representation_id: str
    payload: JsonValue
    sensitivity: OrdinarySecurityLevel
    assurance: OrdinarySecurityLevel


class TransformBehavior(Protocol):
    async def execute(self, *, material: TransientMaterial, services: ExecutionServices) -> TransformOutput: ...


class Transform:
    def __init__(self, contract: TransformContract, behavior: TransformBehavior) -> None:
        self.contract = contract
        self.behavior = behavior


class _TransformEndpoint(TransformEndpoint):
    def __init__(self, module: Module) -> None:
        self._module = module
    @property
    def boundary(self) -> ExecutionBoundary:
        return self._module.endpoint_boundary
    @property
    def binding(self) -> EndpointBinding:
        return self._module.endpoint_binding
    async def invoke_transform(self, transform_id: str, invocation: InvocationContext,
        security: SecurityHistory, material: TransientMaterial) -> TransientMaterial:
        return (await self._module.execute_transform(transform_id, material,
            invocation=invocation, security=security)).transient()
''' +s[at:]
if 'from pydantic import' in s:
    s=s.replace('from pydantic import ', 'from pydantic import JsonValue, ')
else:s=s.replace('from typing import ', 'from pydantic import JsonValue\n\nfrom typing import ')
wr(p,s)
p='src/madre_sdk/__init__.py';s=rd(p).replace('    AgentDescriptor,','    AgentDescriptor,\n    TransformContract,')
s=s.replace('    AgentBehavior,','    AgentBehavior,\n    Transform,\n    TransformOutput,\n    TransformBehavior,')
s=s.replace('    AgentBrokerClient,','    AgentBrokerClient,\n    TransformBrokerClient,')
s=s.replace('    "AgentBrokerClient",','    "AgentBrokerClient",\n    "TransformBrokerClient",\n    "TransformContract",\n    "Transform",\n    "TransformOutput",\n    "TransformBehavior",')
wr(p,s)
