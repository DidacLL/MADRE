from pathlib import Path
R=Path(__file__).parent
def rd(p):return (R/p).read_text()
def wr(p,s):(R/p).write_text(s)
p='src/madre_sdk/semantic.py';wr(p,rd(p).replace('Artifact.create(id=result.representation_id','Artifact.create(artifact_id=result.representation_id'))
p='src/madre/security.py';s=rd(p);at=s.index('\n\nclass SecurityEvaluator')
s=s[:at]+'''

class ProfileDecision(FrozenModel):
    profile_id: Identifier
    profile_security_id: SecurityID
    autonomy: OrdinarySecurityLevel
    decision: SecurityDecision


class ProfileFeasibility(FrozenModel):
    profiles: tuple[ProfileDecision, ...]

    @property
    def feasible_profile_ids(self) -> tuple[str, ...]:
        return tuple(x.profile_id for x in self.profiles if x.decision.admissible)

    @property
    def maximum_feasible_autonomy(self) -> OrdinarySecurityLevel | None:
        return max((x.autonomy for x in self.profiles if x.decision.admissible), default=None)

    @property
    def highest_profile_ids(self) -> tuple[str, ...]:
        maximum = self.maximum_feasible_autonomy
        return tuple(x.profile_id for x in self.profiles if x.decision.admissible and x.autonomy == maximum)
''' +s[at:];wr(p,s)
p='src/madre/broker.py';s=rd(p).replace('    EffectProfileSecurityValues,','    EffectProfileSecurityValues,\n    ProfileDecision,\n    ProfileFeasibility,')
start=s.index('        prospective = security.merge(material.history).extend(',s.index('    def _prepare_operation_input'))
end=s.index('        self._require_admissible(',start)
build=s[start:end]
header='''    def _operation_transition(self, requester: InvocationContext, invocation: InvocationContext,
        security: SecurityHistory, profile: EffectProfile, material: TransientMaterial,
        controller_security_ids: tuple[str, ...]) -> tuple[SecurityHistory, SecurityTransition]:
'''
s=s[:start]+'''        prospective, transition = self._operation_transition(requester, invocation, security,
            profile, material, controller_security_ids)
''' +s[end:]
at=s.index('    def _prepare_operation_input')
s=s[:at]+header+build+'        return prospective, transition\n\n'+s[at:]
at=s.index('    async def invoke_operation')
s=s[:at]+'''    def evaluate_operation_profiles(self, requester: InvocationContext, security: SecurityHistory,
        target_module_id: str, operation_id: str, material: TransientMaterial,
        controller_security_ids: tuple[str, ...] = ()) -> ProfileFeasibility:
        manifest = self._registry.get_module(target_module_id)
        descriptor = next((x for x in manifest.operations if x.id == operation_id), None) if manifest else None
        if manifest is None or descriptor is None:
            raise PublishedTargetNotFound(operation_id)
        _, binding = self._operation_endpoint(manifest)
        completed = set(self._evidence.completed_derivation_ids())
        if any(x.kind == "transform" and x.derivation_id not in completed for x in security.merge(material.history).derivations):
            raise SecurityDenied("unverified_transform_execution")
        results = []
        for profile in sorted(descriptor.effect_profiles, key=lambda p: p.id):
            invocation = InvocationContext(module=manifest.security, endpoint=binding,
                operation=profile.operation, behavior=profile.security)
            history, transition = self._operation_transition(requester, invocation, security,
                profile, material, controller_security_ids)
            values = profile.security.values
            assert isinstance(values, EffectProfileSecurityValues)
            results.append(ProfileDecision(profile_id=profile.id, profile_security_id=profile.security.security_id,
                autonomy=values.autonomy, decision=self._security_evaluator.evaluate(history, transition)))
        return ProfileFeasibility(profiles=tuple(results))

''' +s[at:];wr(p,s)
p='src/madre/interfaces.py';s=rd(p).replace('from madre.security import EndpointBinding, ', 'from madre.security import ProfileFeasibility, EndpointBinding, ')
if 'import ProfileFeasibility' not in s:s=s.replace('    EndpointBinding,','    EndpointBinding,\n    ProfileFeasibility,')
s=s.replace('class OperationBrokering(Protocol):','''class OperationBrokering(Protocol):
    def evaluate_operation_profiles(self, requester: InvocationContext, security: SecurityHistory,
        target_module_id: str, operation_id: str, material: TransientMaterial,
        controller_security_ids: tuple[str, ...] = ()) -> ProfileFeasibility: ...
''');wr(p,s)
p='src/madre_sdk/services.py';s=rd(p).replace('from madre.security import InvocationContext, SecurityHistory, SecurityObject','from madre.security import InvocationContext, SecurityHistory, SecurityObject, ProfileFeasibility')
at=s.index('    async def invoke(',s.index('class OperationBrokerClient'))
s=s[:at]+'''    def evaluate_profiles(self, module_id: str, operation_id: str, material: Material, *,
        controllers: tuple[SecurityObject, ...] = (), security: SecurityHistory | None = None) -> ProfileFeasibility:
        invocation = self._active_invocation()
        carried = (security or self._security).extend(objects=(*controllers, *invocation.objects))
        return self._broker.evaluate_operation_profiles(invocation, carried, module_id, operation_id,
            material.transient(), tuple(x.security_id for x in controllers))

''' +s[at:];wr(p,s)
