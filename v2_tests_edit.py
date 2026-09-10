from pathlib import Path
import re
R=Path(__file__).parent
def rd(p):return (R/p).read_text()
def wr(p,s):(R/p).write_text(s)
p='src/madre/storage_db.py';s=rd(p);start=s.index('def _discard_development_storage');end=s.index('@contextmanager',start);s=s[:start]+s[end:]
s=s.replace('                connection.close()\n                _discard_development_storage(database)\n                connection = _connect(database)\n                format_id = 0','                raise RuntimeError("incompatible persisted security format; use a fresh V2 data directory")')
s=s.replace('hashlib.sha256(_STORAGE_DDL.encode())','hashlib.sha256(("security-v2\\n" + _STORAGE_DDL).encode())');wr(p,s)
# All source-context consumers use V2 boundary relation names.
for p in (R/'tests').rglob('*.py'):
    s=p.read_text().replace('path_security_ids','boundary_security_ids').replace('path_privacy','boundary_privacy')
    p.write_text(s)
p='tests/test_registry_broker.py';s=rd(p).replace('    participant_security,','    participant_security,\n    disclosure_boundary,\n    EndpointBinding,\n    SecuritySubjectRef,')
s=re.sub(r'^        privacy=(?:privacy|SecurityLevel.LEVEL_5),\n','',s,flags=re.M)
s=s.replace('            agents=(agent,),','            agents=(agent,),\n            disclosure_boundaries=(boundary(TARGET, privacy),),')
s=s.replace('            operations=(operation,),','            operations=(operation,),\n            disclosure_boundaries=(boundary(TARGET),),')
s=s.replace('        risk=risk,','        control_risk=risk, effect_risk=risk,')
s=s.replace('        discloses_material=discloses_material,','        disclosure_boundaries=(boundary(TARGET, privacy),) if discloses_material else (),')
# profile factory privacy is still a test argument for a boundary, never an actor fact.
s=s.replace('        privacy=privacy,\n','')
s=s.replace('InvocationContext(module=requester)', 'InvocationContext(module=requester, endpoint=attachment(requester.subject_ref.owner_module_id))')
s=s.replace('InvocationContext(module=requester_security())', 'InvocationContext(module=requester_security(), endpoint=attachment(REQUESTER))')
s=s.replace('module.endpoint_security.security_id','module.endpoint_binding.disclosure_boundaries[0].security_id')
s=s.replace('module.security.security_id in disclosure["boundary_security_ids"]','module.endpoint_binding.disclosure_boundaries[0].security_id in disclosure["boundary_security_ids"]')
at=s.index('\n\ndef requester_security')
s=s[:at]+'''

def boundary(owner, privacy=SecurityLevel.LEVEL_5):
    return disclosure_boundary(owner_module_id=owner, boundary_id=f"{owner}:boundary", privacy_capacity=privacy)

def attachment(owner, privacy=SecurityLevel.LEVEL_5):
    return EndpointBinding(subject_ref=SecuritySubjectRef(owner_module_id=owner, subject_kind="endpoint",
        local_id=f"{owner}:endpoint", publication_revision="1"), disclosure_boundaries=(boundary(owner, privacy),))
''' +s[at:];wr(p,s)
p='tests/test_sdk_core.py';s=rd(p).replace('    participant_security,','    participant_security,\n    disclosure_boundary,')
s=re.sub(r'^\s*privacy=SecurityLevel.LEVEL_5,\n','\n',s,flags=re.M)
s=s.replace('        self.requests = []','        self.requests = []\n        self.boundary = disclosure_boundary(owner_module_id="madre.platform", boundary_id="inference", privacy_capacity=SecurityLevel.LEVEL_5)')
s=s.replace('boundary_security_ids=(self.capability_security.security_id,)','boundary_security_ids=(self.boundary.security_id,)')
s=s.replace('objects=(self.capability_security,)','objects=(self.capability_security, self.boundary)')
s=s.replace('assert module_values.privacy == SecurityLevel.LEVEL_5','assert core.endpoint_binding.disclosure_boundaries[0].values.privacy_capacity == SecurityLevel.LEVEL_5')
s=s.replace('assert agent_values.privacy == SecurityLevel.LEVEL_5','assert not hasattr(agent_values, "privacy")')
wr(p,s)
p='tests/reference_agentless_module.py';s=rd(p).replace('    participant_security,','    participant_security,\n    disclosure_boundary,').replace('            privacy=SecurityLevel.LEVEL_5,\n','')
s=s.replace('            materials=materials,','            materials=materials,\n            disclosure_boundaries=(disclosure_boundary(owner_module_id="reference.notes", boundary_id="notes", privacy_capacity=SecurityLevel.LEVEL_5),),')
s=s.replace('InvocationContext(module=self.security)','InvocationContext(module=self.security, endpoint=self.endpoint_binding)')
s=s.replace('self.security, self.endpoint_security','self.security, *self.endpoint_binding.disclosure_boundaries');wr(p,s)
p='tests/test_runtime_lifecycle.py';s=rd(p).replace('MaterialRepository, participant_security','MaterialRepository, participant_security, disclosure_boundary')
s=s.replace('        privacy=SecurityLevel.LEVEL_5,\n','').replace('CapabilitySecurityValues(privacy=privacy, assurance=assurance)','CapabilitySecurityValues(assurance=assurance)')
s=s.replace('            heavyweight=heavyweight,','            heavyweight=heavyweight,\n            disclosure_boundaries=(disclosure_boundary(owner_module_id="madre.platform", boundary_id=capability_id+":boundary", privacy_capacity=privacy),),')
wr(p,s)
