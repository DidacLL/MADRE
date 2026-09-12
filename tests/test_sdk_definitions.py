from __future__ import annotations

import pytest

from madre.catalog import ModuleCatalog
from madre_sdk import (
    AgentDefinition,
    Autonomy,
    EffectProfile,
    IdentityKind,
    Integrity,
    MaterialContract,
    ModuleDefinition,
    OperationDefinition,
    OperationReference,
    Privacy,
    Repeatability,
    Risk,
    ScopeIdentity,
    SecurityScope,
    Sensitivity,
    SkillDefinition,
    SkillReference,
    SurfaceReference,
    WorkflowDefinition,
    WorkflowReference,
)


class DefinitionStore:
    def __init__(self) -> None:
        self._definitions: tuple[ModuleDefinition, ...] = ()

    def put_module(self, definition: ModuleDefinition) -> None:
        self._definitions = (definition,)

    def modules(self) -> tuple[ModuleDefinition, ...]:
        return self._definitions


def identity(
    name: str,
    kind: IdentityKind = IdentityKind.SURFACE,
) -> ScopeIdentity:
    return ScopeIdentity(kind=kind, owner="module", name=name, revision="7")


def contract(name: str) -> MaterialContract:
    return MaterialContract(
        identity=identity(name, IdentityKind.MATERIAL_CONTRACT),
        media_type="application/json",
    )


def operation(name: str, privacy: Privacy) -> OperationDefinition:
    operation_identity = identity(name, IdentityKind.OPERATION)
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
                identity=identity(f"{name}.bounded", IdentityKind.EFFECT_PROFILE),
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
        identity=identity("skill", IdentityKind.SKILL),
        purpose="Reusable knowledge",
        instructions=("Preserve Module meaning.",),
        input_contract=contract("input"),
        output_contract=contract("output"),
    )
    workflow = WorkflowDefinition(
        identity=identity("workflow", IdentityKind.WORKFLOW),
        purpose="Reusable semantic recipe",
        instructions=("Interpret inside the Module.",),
        input_contract=contract("input"),
        output_contract=contract("output"),
    )
    agent_identity = identity("agent", IdentityKind.AGENT)
    agent = AgentDefinition(
        identity=agent_identity,
        purpose="Module-owned actor",
        input_contract=contract("input"),
        output_contract=contract("output"),
        security=SecurityScope(identity=agent_identity, privacy=Privacy.SECRET),
        skills=(SkillReference(identity=skill.identity),),
        workflows=(WorkflowReference(identity=workflow.identity),),
        exposed_operations=(
            OperationReference(identity=private_operation.identity),
            OperationReference(identity=narrower_operation.identity),
        ),
    )
    module_identity = identity("module", IdentityKind.MODULE)
    secret_material_scope = SecurityScope(
        identity=identity("managed-secret", IdentityKind.MATERIAL),
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
        operations=(private_operation, narrower_operation),
        public_surfaces=(SurfaceReference(identity=private_operation.identity),),
    )

    restored = ModuleDefinition.model_validate_json(definition.model_dump_json())

    assert restored == definition
    assert restored.surface.sensitivity is Sensitivity.S5
    assert restored.surface.privacy is Privacy.LOCAL_PRIVATE
    assert restored.agent_surface(agent.identity).privacy is Privacy.LOCAL_PRIVATE

    reduced_agent = agent.model_copy(
        update={"exposed_operations": (OperationReference(identity=private_operation.identity),)}
    )
    reduced_definition = definition.model_copy(update={"agents": (reduced_agent,)})
    assert reduced_definition.agent_surface(agent.identity).privacy is Privacy.SECRET

    catalog = ModuleCatalog(DefinitionStore())
    catalog.register(restored)
    assert catalog.skills() == (skill,)
    assert catalog.workflows() == (workflow,)
    assert catalog.operations() == (private_operation, narrower_operation)

    unresolved_agent = agent.model_copy(
        update={
            "skills": (SkillReference(identity=identity("missing-skill", IdentityKind.SKILL)),),
        }
    )
    with pytest.raises(ValueError, match="Skill reference does not resolve"):
        definition.model_copy(update={"agents": (unresolved_agent,)})


def test_definition_identities_are_not_interchangeable_by_shape() -> None:
    with pytest.raises(ValueError, match="MaterialContract requires a material_contract"):
        MaterialContract(
            identity=identity("wrong-kind", IdentityKind.OPERATION),
            media_type="application/json",
        )
