package io.github.didacll.madre.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.codec.CodecException;
import io.github.didacll.madre.sdk.codec.ModuleDefinitionJsonCodec;
import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.PhysicalRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.module.WorkflowDefinition;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SdkInvariantTest {
    private static final MaterialCodec<String> STRINGS = new MaterialCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    @Test void adaptationRequiresIndependentIdentityAndSensitivity() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        Material<String> source = new Material<>(new MaterialId(owner, "source"), type,
                "secret", Sensitivity.S5);
        Material<String> adapted = new Material<>(new MaterialId(owner, "minimized"), type,
                "s", Sensitivity.S2);
        assertNotEquals(source.id(), adapted.id());
        assertEquals(Sensitivity.S5, source.sensitivity());
    }

    @Test void consequentialConstructionAppliesOnlyTheSelectedProfile() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        Material<String> input = new Material<>(new MaterialId(owner, "input"), type,
                "hello", Sensitivity.S2);
        OperationId operationId = new OperationId(owner, "run");
        EffectProfile profile = new EffectProfile(new EffectProfileId(operationId, "bounded"),
                Risk.R3, Autonomy.A2);
        OperationDefinition<String, String> operation = new OperationDefinition<>(operationId,
                "Run bounded behavior", OperationVisibility.PUBLIC,
                Map.of(type.id(), Privacy.LOCAL), Map.of(type.id(), Sensitivity.S3),
                Map.of(profile.id(), profile));
        OperationCall<String, String> call = OperationCall.withEffect(
                operation, profile, input, List.of(Integrity.I2));
        assertThrows(IllegalArgumentException.class, () ->
                OperationCall.withEffect(operation, profile, input, List.of(Integrity.I1)));
        assertThrows(IllegalArgumentException.class, () ->
                OperationCall.withEffect(operation, profile, input,
                        List.of(Integrity.SYSTEM_RESERVED)));
        Material<String> undeclaredSensitivity = new Material<>(
                new MaterialId(owner, "output"), type, "result", Sensitivity.S4);
        assertThrows(IllegalArgumentException.class,
                () -> call.acceptOutput(undeclaredSensitivity));
    }

    @Test void workRequestDerivesItsCarriedValuesFromTheBoundedOperationCall() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        Material<String> input = new Material<>(new MaterialId(owner, "input"), type,
                "hello", Sensitivity.S3);
        OperationDefinition<String, String> operation = new OperationDefinition<>(
                new OperationId(owner, "read"), "Read text", OperationVisibility.PRIVATE,
                Map.of(type.id(), Privacy.LOCAL), Map.of(type.id(), Sensitivity.S3), Map.of());
        OperationCall<String, String> call = OperationCall.withoutEffect(operation, input);

        WorkRequest<String, String> request = WorkRequest.immediate(call, "physical",
                String.class, 1, Duration.ofSeconds(1), PhysicalRetryPolicy.none(),
                Optional.empty(), PhysicalPreferences.unconstrained());

        assertEquals(owner, request.originatingModule());
        assertEquals(Sensitivity.S3, request.carriedSensitivity());
        assertEquals(Optional.empty(), request.effectRisk());
    }

    @Test void agentOwnsOrderedWorkflowsAndDefinitionsRoundTrip() {
        ModuleDefinition definition = definition();
        AgentDefinition agent = definition.agents().values().iterator().next();
        WorkflowDefinition workflow = agent.workflows().values().iterator().next();
        assertEquals(agent.id(), workflow.id().agentId());
        assertEquals(List.of(workflow.operations().get(0), workflow.operations().get(0)),
                workflow.operations());
        assertEquals(Privacy.LOCAL, agent.effectivePrivacy(definition.operations()));
        assertEquals(Sensitivity.S4, definition.effectiveSensitivity(List.of()).orElseThrow());
        ModuleDefinitionJsonCodec codec = new ModuleDefinitionJsonCodec(
                (id, contentType) -> definition.materialTypes().get(id));
        ModuleDefinition decoded = codec.decode(codec.encode(definition));
        AgentDefinition decodedAgent = decoded.agents().get(agent.id());
        assertEquals(definition.id(), decoded.id());
        assertEquals(definition.operations().keySet(), decoded.operations().keySet());
        assertEquals(workflow.operations(),
                decodedAgent.workflows().get(workflow.id()).operations());
        assertThrows(CodecException.class, () -> codec.decode(
                codec.encode(definition).replaceFirst("\\{", "{\"metadata\":{},")));
    }

    @Test void systemReservedValuesAreRejectedByOrdinarySdkObjects() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        OperationId operationId = new OperationId(owner, "run");

        assertThrows(IllegalArgumentException.class, () -> new Material<>(
                new MaterialId(owner, "system"), type, "value", Sensitivity.SYSTEM_RESERVED));
        assertThrows(IllegalArgumentException.class, () -> new OperationDefinition<>(operationId,
                "Reserved input", OperationVisibility.PRIVATE,
                Map.of(type.id(), Privacy.SYSTEM_RESERVED), Map.of(type.id(), Sensitivity.S1),
                Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new OperationDefinition<>(operationId,
                "Reserved output", OperationVisibility.PRIVATE,
                Map.of(type.id(), Privacy.PUBLIC),
                Map.of(type.id(), Sensitivity.SYSTEM_RESERVED), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EffectProfile(
                new EffectProfileId(operationId, "reserved"),
                Risk.SYSTEM_RESERVED, Autonomy.A1));
        assertThrows(IllegalArgumentException.class, () -> new AgentDefinition(
                new AgentId(owner, "reserved"), "Reserved agent", Integrity.SYSTEM_RESERVED,
                Set.of(), Map.of(), Set.of(operationId)));
    }

    @Test void agentRejectsWorkflowOwnedByAnotherAgent() {
        ModuleId owner = new ModuleId("owner.module");
        OperationId operation = new OperationId(owner, "run");
        AgentId actualOwner = new AgentId(owner, "actual");
        AgentId foreignOwner = new AgentId(owner, "foreign");
        WorkflowDefinition foreignWorkflow = new WorkflowDefinition(
                new WorkflowId(foreignOwner, "flow"), "Foreign flow", List.of(operation));
        assertThrows(IllegalArgumentException.class, () -> new AgentDefinition(actualOwner,
                "Actual agent", Integrity.I4, Set.of(),
                Map.of(foreignWorkflow.id(), foreignWorkflow), Set.of(operation)));
    }

    private static ModuleDefinition definition() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        OperationId operationId = new OperationId(owner, "answer");
        OperationDefinition<String, String> operation = new OperationDefinition<>(operationId,
                "Answer text", OperationVisibility.PUBLIC, Map.of(type.id(), Privacy.LOCAL),
                Map.of(type.id(), Sensitivity.S4), Map.of());
        AgentId agentId = new AgentId(owner, "interaction");
        WorkflowId workflowId = new WorkflowId(agentId, "repeat-answer");
        WorkflowDefinition workflow = new WorkflowDefinition(workflowId, "Repeat answer behavior",
                List.of(operationId, operationId));
        AgentDefinition agent = new AgentDefinition(agentId, "Interact", Integrity.I4,
                Set.of(), Map.of(workflowId, workflow), Set.of(operationId));
        return new ModuleDefinition(owner, "1.0.0", "Owner module", Map.of(type.id(), type),
                Set.of(), Map.of(agentId, agent), Map.of(), Map.of(operationId, operation));
    }
}
