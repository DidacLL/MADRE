from pathlib import Path
import re
R=Path(__file__).parent
def rd(p):return (R/p).read_text()
def wr(p,s):(R/p).write_text(s)
p='tests/test_execution_binding.py';s=rd(p).replace('from tests.test_causal_topology import','from tests.v2_helpers import').replace('    Transform,','    Repackage,').replace('Transform()','Repackage()')
s=s.replace('InvocationContext(module=invocation.module)','InvocationContext(module=invocation.module, endpoint=invocation.endpoint)')
s=s.replace('InvocationContext(module=requester_security())','InvocationContext(module=requester_security(), endpoint=attachment("module.requester"))')
s=s.replace('from tests.test_registry_broker import TARGET, make_operation_module, profile','from tests.test_registry_broker import TARGET, make_operation_module, profile, attachment, boundary')
s=s.replace('reader.agents[0].security.security_id in decision.disclosures[0].boundary_security_ids','reader.endpoint_binding.disclosure_boundaries[0].security_id in decision.disclosures[0].boundary_security_ids')
s=s.replace('test_executing_agent_cannot_narrow_return_recipient','test_executing_agent_cannot_narrow_return_boundary')
s=s.replace('risk=L1,','control_risk=L1, effect_risk=L1,')
s=s.replace('        operations=(operation,),','        operations=(operation,),\n        disclosure_boundaries=(boundary("root"),),')
s=s.replace('module.security.security_id in decision.effect.controller_security_ids','operation.effect_profiles[0].security.security_id in decision.effect.controller_security_ids')
wr(p,s)
p='tests/test_registry_broker.py';s=rd(p).replace('    risk: SecurityLevel,','    risk: SecurityLevel,\n    control_risk: SecurityLevel | None = None,')
s=s.replace('control_risk=risk, effect_risk=risk,','control_risk=control_risk or risk, effect_risk=risk,')
s=s.replace('            "direct",\n            risk=SecurityLevel.LEVEL_5,','            "direct",\n            control_risk=SecurityLevel.LEVEL_1,\n            risk=SecurityLevel.LEVEL_5,')
s=s.replace('producer_security_ids=(self.producer_security_id,)','producer_security_ids=()');wr(p,s)
p='src/madre_core/core.py';s=rd(p).replace('producer_security_ids=(self._producer_security_id,)','producer_security_ids=()')
s=s.replace('        producer_security_id: str,\n','').replace('        self._producer_security_id = producer_security_id\n','').replace('            producer_security_id=module_security.security_id,\n','');wr(p,s)
p='tests/test_architecture_boundaries.py';s=rd(p).replace('{"privacy", "assurance"}','{"privacy_capacity", "assurance"}')
s=s.replace('        privacy=SecurityLevel.LEVEL_5,\n','')
s=s.replace('        endpoint="http://127.0.0.1:11434/v1",','        endpoint="http://127.0.0.1:11434/v1",\n        privacy_capacity=SecurityLevel.LEVEL_5,');wr(p,s)
# Adapter/config tests and configured examples receive the explicit boundary capacity name.
for name in ('test_service.py','test_openai_adapter.py','test_config.py'):
    p=R/'tests'/name
    if p.exists():
        s=p.read_text().replace('privacy=', 'privacy_capacity=').replace('"privacy":','"privacy_capacity":')
        p.write_text(s)
for p in (R/'src').rglob('*.py'):
    s=p.read_text().replace('"Frozen MADRE Security Algebra:', '"MADRE Security Algebra V2:').replace('the frozen MADRE Security Algebra','MADRE Security Algebra V2')
    p.write_text(s)
