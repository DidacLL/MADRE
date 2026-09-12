from __future__ import annotations

from madre_sdk import (
    AgentDefinition,
    Autonomy,
    EffectProfile,
    Integrity,
    MaterialContract,
    ModuleDefinition,
    OperationDefinition,
    Privacy,
    Repeatability,
    Risk,
    ScopeIdentity,
    SecurityScope,
    Sensitivity,
    SkillDefinition,
    WorkflowDefinition,
)


def identity(name: str) -> ScopeIdentity:
    return ScopeIdentity(owner="module", name=name, revision="7")


def contract(name: str) -> MaterialContract:
    return MaterialContract(identity=identity(name), media_type="application/json")


def operation(name: str, privacy: Privacy) -> OperationDefinition:
    operation_identity = identity(name)
    return OperationDefinition(
        identity=operation_identity,
        purpose=f"bounded {name}",
        input_contract=contract("input"),
        output_contract=contract("output"),
        effect=name,
        repeatability=Repeatability.IDEMPOTENT,
        security=SecurityScope(
            identity=operation_identity,
            privacy=privacy,
            integrity=Integrity.I4,
        ),
        effect_profiles=(
            EffectProfile(
                identity=identity(f"{name}.bounded"),
                operation=operation_identity,
                risk=Risk.R3,
                autonomy=Autonomy.A2,
            ),
        ),
    )


def test_module_definitions_are_serializable_structural_aggregates() -> None:
    private_operation = operation("private-operation", Privacy.SECRET)
    narrower_operation = operation("narrower-operation", Privacy.LOCAL_PRIVATE)
    skill = SkillDefinition(
        identity=identity("skill"),
        purpose="Reusable knowledge",
        instructions=("Preserve Module meaning.",),
        input_contract=contract("input"),
        output_contract=contract("output"),
    )
    workflow = WorkflowDefinition(
        identity=identity("workflow"),
        purpose="Reusable semantic recipe",
        instructions=("Interpret inside the Module.",),
        input_contract=contract("input"),
        output_contract=contract("output"),
    )
    agent_identity = identity("agent")
    agent = AgentDefinition(
        identity=agent_identity,
        purpose="Module-owned actor",
        input_contract=contract("input"),
        output_contract=contract("output"),
        security=SecurityScope(identity=agent_identity, privacy=Privacy.SECRET),
        skills=(skill,),
        workflows=(workflow,),
        operations=(private_operation, narrower_operation),
    )
    module_identity = identity("module")
    secret_material_scope = SecurityScope(
        identity=identity("managed-secret"),
        sensitivity=Sensitivity.S5,
    )
    definition = ModuleDefinition(
        identity=module_identity,
        description="An ordinary Module",
        security=SecurityScope(
            identity=module_identity,
            privacy=Privacy.SECRET,
            integrity=Integrity.I5,
        ),
        managed_scopes=(secret_material_scope,),
        agents=(agent,),
        skills=(skill,),
        workflows=(workflow,),
    )

    restored = ModuleDefinition.model_validate_json(definition.model_dump_json())

    assert restored == definition
    assert restored.surface.sensitivity is Sensitivity.S5
    assert restored.surface.privacy is Privacy.LOCAL_PRIVATE
    assert agent.surface.privacy is Privacy.LOCAL_PRIVATE

    reduced_agent = agent.model_copy(update={"operations": (private_operation,)})
    assert reduced_agent.surface.privacy is Privacy.SECRET
