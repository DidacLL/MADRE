from __future__ import annotations

from madre_sdk import (
    AgentDefinition,
    AgentId,
    Autonomy,
    DisplayName,
    EffectProfile,
    EffectProfileId,
    InputSurface,
    InputSurfaces,
    MaterialType,
    MaterialTypeId,
    ModuleDefinition,
    ModuleId,
    OperationDefinition,
    OperationId,
    OutputSurface,
    OutputSurfaces,
    Privacy,
    Purpose,
    Repeatability,
    Risk,
    Sensitivity,
    SkillDefinition,
    SkillId,
    SurfaceId,
    WorkflowDefinition,
    WorkflowId,
)


def build_module_definition() -> ModuleDefinition:
    module_id = ModuleId("owner-research", "3")
    secret_type = MaterialType[object](
        MaterialTypeId(module_id, "secret-text"),
        "text/plain",
    )
    summary_type = MaterialType[object](
        MaterialTypeId(module_id, "summary-text"),
        "text/plain",
    )
    report_type = MaterialType[object](
        MaterialTypeId(module_id, "report"),
        "application/json",
    )

    local_operation_id = OperationId(module_id, "private-summary")
    local_operation = OperationDefinition(
        identity=local_operation_id,
        name=DisplayName("Private summary"),
        purpose=Purpose("Summarize owner material inside a private receiving boundary."),
        inputs=InputSurfaces.of(
            InputSurface(
                SurfaceId(module_id, "private-summary-input"),
                secret_type.identity,
                Privacy.P5,
            )
        ),
        outputs=OutputSurfaces.of(
            OutputSurface(
                SurfaceId(module_id, "private-summary-output"),
                summary_type.identity,
                Sensitivity.S4,
            )
        ),
        effect_profiles=(
            EffectProfile(
                EffectProfileId(local_operation_id, "owner-triggered"),
                Risk.R2,
                Autonomy.A1,
            ),
        ),
        repeatability=Repeatability.IDEMPOTENT,
    )

    publish_operation_id = OperationId(module_id, "publish-minimized")
    publish_operation = OperationDefinition(
        identity=publish_operation_id,
        name=DisplayName("Publish minimized report"),
        purpose=Purpose("Publish only independently minimized report material."),
        inputs=InputSurfaces.of(
            InputSurface(
                SurfaceId(module_id, "publish-minimized-input"),
                report_type.identity,
                Privacy.P3,
            )
        ),
        outputs=OutputSurfaces.of(
            OutputSurface(
                SurfaceId(module_id, "publish-minimized-output"),
                report_type.identity,
                Sensitivity.S2,
            )
        ),
        effect_profiles=(
            EffectProfile(
                EffectProfileId(publish_operation_id, "reviewed"),
                Risk.R3,
                Autonomy.A1,
            ),
        ),
        repeatability=Repeatability.SINGLE_USE,
    )

    skill_id = SkillId(module_id, "summarization")
    skill = SkillDefinition(
        identity=skill_id,
        name=DisplayName("Summarization"),
        purpose=Purpose("Produce a concise semantic summary."),
        inputs=local_operation.inputs,
        outputs=local_operation.outputs,
    )
    workflow_id = WorkflowId(module_id, "review-and-publish")
    workflow = WorkflowDefinition(
        identity=workflow_id,
        name=DisplayName("Review and publish"),
        purpose=Purpose("Create and publish a minimized report after owner review."),
        inputs=publish_operation.inputs,
        outputs=publish_operation.outputs,
        skills=(skill_id,),
        operations=(publish_operation_id,),
    )
    agent = AgentDefinition(
        identity=AgentId(module_id, "researcher"),
        name=DisplayName("Researcher"),
        purpose=Purpose("Help the owner prepare research material."),
        outputs=local_operation.outputs,
        skills=(skill_id,),
        workflows=(workflow_id,),
        exposed_operations=(local_operation_id, publish_operation_id),
    )
    return ModuleDefinition(
        identity=module_id,
        name=DisplayName("Owner research"),
        purpose=Purpose("Manage the owner's private research material."),
        material_types=(secret_type, summary_type, report_type),
        agents=(agent,),
        skills=(skill,),
        workflows=(workflow,),
        operations=(local_operation, publish_operation),
        public_outputs=publish_operation.outputs,
    )
