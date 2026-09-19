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
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
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
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
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

    @Test void establishedScalarAlgebraCompositionAccumulatesActualConstituents() {
        assertEquals(Sensitivity.S5, Sensitivity.S2.combine(Sensitivity.S5));
        assertEquals(Sensitivity.S5, Sensitivity.S5.combine(Sensitivity.S2));
        assertEquals(Privacy.PUBLIC, Privacy.LOCAL.combine(Privacy.PUBLIC));
        assertEquals(Privacy.PUBLIC, Privacy.PUBLIC.combine(Privacy.LOCAL));
        assertEquals(Integrity.I2, Integrity.I4.combine(Integrity.I2));
        assertEquals(Integrity.I2, Integrity.I2.combine(Integrity.I4));
    }

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

    @Test void materialIdentityModuleCanDifferFromNominalTypeModule() {
        ModuleId contractModule = new ModuleId("contract.module");
        ModuleId materialIdentityModule = new ModuleId("creator.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(contractModule, "text"),
                String.class, "text/plain", STRINGS);

        Material<String> material = new Material<>(
                new MaterialId(materialIdentityModule, "created"), type, "value", Sensitivity.S2);

        assertEquals(materialIdentityModule, material.id().moduleId());
        assertEquals(contractModule, material.type().id().moduleId());
    }

    @Test void consequentialCallUsesSelectedProfileWithoutCrossDimensionEvaluation() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        Material<String> input = new Material<>(new MaterialId(owner, "input"), type,
                "hello", Sensitivity.S5);
        OperationId operationId = new OperationId(owner, "run");
        EffectProfile profile = new EffectProfile(new EffectProfileId(operationId, "bounded"),
                Risk.DELETE, Autonomy.ASK_ALWAYS);
        OperationDefinition operation = new OperationDefinition(operationId,
                "Run bounded behavior", Map.of(type.id(), Privacy.PUBLIC),
                Map.of(type.id(), Sensitivity.S3), Map.of(profile.id(), profile));
        OperationCall<String, String> call = OperationCall.withEffect(operation, profile, input);

        assertEquals(profile, call.effectProfile().orElseThrow());
        assertEquals(Sensitivity.S5, call.input().sensitivity());
        assertEquals(Privacy.PUBLIC, operation.acceptedMaterial().get(type.id()));

        Material<String> excessiveOutput = new Material<>(
                new MaterialId(owner, "output"), type, "result", Sensitivity.S4);
        assertThrows(IllegalArgumentException.class,
                () -> call.acceptOutput(excessiveOutput));
    }

    @Test void reasoningRequestComposesActualCurrentSemanticConstituents() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        Material<String> input = new Material<>(new MaterialId(owner, "input"), type,
                "hello", Sensitivity.S3);
        Material<String> currentContext = new Material<>(
                new MaterialId(owner, "context"), type, "prior context", Sensitivity.S5);
        OperationId operationId = new OperationId(owner, "read");
        OperationDefinition operation = new OperationDefinition(operationId, "Read text",
                Map.of(type.id(), Privacy.LOCAL), Map.of(type.id(), Sensitivity.S3), Map.of());
        OperationCall<String, String> call = OperationCall.withoutEffect(operation, input);
        Agent actor = new Agent() {
            @Override public AgentId id() { return new AgentId(owner, "reasoner"); }
            @Override public String purpose() { return "Own current reasoning context"; }
            @Override public Integrity integrity() { return Integrity.I4; }
            @Override public Set<OperationId> operations() { return Set.of(operationId); }
        };

        ReasoningRequest<String, FixtureReasoning> request = ReasoningRequest.immediate(
                actor, call, List.of(currentContext), List.of(Privacy.PUBLIC),
                List.of(Integrity.I2), new FixtureReasoning("reason"), 1,
                Duration.ofSeconds(1), ReasoningRetryPolicy.none(), Optional.empty(),
                ReasoningPreferences.requirements());

        assertEquals(Sensitivity.S5, request.sensitivity());
        assertEquals(Privacy.PUBLIC, request.privacy());
        assertEquals(Integrity.I2, request.integrity());
        assertEquals(String.class, request.resultType());
    }

    @Test void coordinatorAgentMayHaveNoLocalOperationsAndForeignWorkflowRoundTrips() {
        ModuleId coordinatorModule = new ModuleId("core.module");
        AgentId agentId = new AgentId(coordinatorModule, "coordinator");
        OperationId foreignOperation = new OperationId(
                new ModuleId("calculator.module"), "calculate");
        WorkflowId workflowId = new WorkflowId(agentId, "delegate-calculation");
        WorkflowDefinition workflow = new WorkflowDefinition(workflowId,
                "Coordinate a foreign exposed Operation", List.of(foreignOperation));
        AgentDefinition agent = new AgentDefinition(agentId, "Coordinate installed Modules",
                Integrity.I4, Set.of(), Map.of(workflowId, workflow), Set.of());
        ModuleDefinition definition = new ModuleDefinition(coordinatorModule, "1.0.0",
                "Coordinator module", Map.of(), Map.of(agentId, agent), Map.of(), Map.of());

        ModuleDefinitionJsonCodec codec = new ModuleDefinitionJsonCodec();
        ModuleDefinition decoded = codec.decode(codec.encode(definition));
        AgentDefinition decodedAgent = decoded.agents().get(agentId);

        assertEquals(Set.of(), decodedAgent.operations());
        assertEquals(List.of(foreignOperation),
                decodedAgent.workflows().get(workflowId).operations());
        assertThrows(CodecException.class, () -> codec.decode(
                codec.encode(definition).replaceFirst("\\{", "{\"metadata\":{},")));
    }

    @Test void operationMayReferenceForeignMaterialTypeWithoutLocalDeclarationAndRoundTrips() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialTypeId foreign = new MaterialTypeId(
                new ModuleId("contract.module"), "foreign-text");
        OperationDefinition operation = new OperationDefinition(new OperationId(owner, "emit"),
                "Emit foreign contract", Map.of(foreign, Privacy.MODULE),
                Map.of(foreign, Sensitivity.S3), Map.of());
        ModuleDefinition definition = new ModuleDefinition(owner, "1", "Foreign contract consumer",
                Map.of(), Map.of(), Map.of(), Map.of(operation.id(), operation),
                Set.of(operation.id()));

        ModuleDefinition decoded = new ModuleDefinitionJsonCodec().decode(
                new ModuleDefinitionJsonCodec().encode(definition));
        OperationDefinition decodedOperation = decoded.operations().get(operation.id());

        assertEquals(Map.of(), decoded.materialTypes());
        assertEquals(Privacy.MODULE, decodedOperation.acceptedMaterial().get(foreign));
        assertEquals(Sensitivity.S3, decodedOperation.producedMaterial().get(foreign));
    }

    @Test void descriptionsKeepOperationAlgebraAsContractFactsNotAgentAggregates() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialTypeId type = new MaterialTypeId(new ModuleId("contract.module"), "text");
        OperationId operationId = new OperationId(owner, "possible-operation");
        OperationDefinition operation = new OperationDefinition(operationId,
                "Possible bounded behavior", Map.of(type, Privacy.PUBLIC),
                Map.of(type, Sensitivity.S5), Map.of());
        AgentId agentId = new AgentId(owner, "agent");
        AgentDefinition agent = new AgentDefinition(agentId, "Agent with possible behavior",
                Integrity.I4, Set.of(), Map.of(), Set.of(operationId));
        ModuleDefinition definition = new ModuleDefinition(owner, "1", "Description fixture",
                Map.of(), Map.of(agentId, agent), Map.of(),
                Map.of(operationId, operation));

        assertEquals(Integrity.I4, definition.agents().get(agentId).integrity());
        assertEquals(Privacy.PUBLIC,
                definition.operations().get(operationId).acceptedMaterial().get(type));
        assertEquals(Sensitivity.S5,
                definition.operations().get(operationId).producedMaterial().get(type));
    }

    @Test void unprovenJavaAgentDefaultsToLowestOrdinaryIntegrity() {
        ModuleId owner = new ModuleId("owner.module");
        Agent agent = new Agent() {
            @Override public AgentId id() { return new AgentId(owner, "experimental"); }
            @Override public String purpose() { return "Experimental generated agent"; }
            @Override public Set<OperationId> operations() {
                return Set.of(new OperationId(owner, "run"));
            }
        };
        assertEquals(Integrity.I1, agent.integrity());
        assertEquals(Integrity.I1, agent.definition().integrity());
    }

    @Test void systemReservedValuesAreRejectedByOrdinarySdkObjects() {
        ModuleId owner = new ModuleId("owner.module");
        MaterialType<String> type = new MaterialType<>(new MaterialTypeId(owner, "text"),
                String.class, "text/plain", STRINGS);
        OperationId operationId = new OperationId(owner, "run");

        assertThrows(IllegalArgumentException.class, () -> new Material<>(
                new MaterialId(owner, "system"), type, "value", Sensitivity.SYSTEM_RESERVED));
        assertThrows(IllegalArgumentException.class, () -> new OperationDefinition(operationId,
                "Reserved input", Map.of(type.id(), Privacy.SYSTEM_RESERVED),
                Map.of(type.id(), Sensitivity.S1), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new OperationDefinition(operationId,
                "Reserved output", Map.of(type.id(), Privacy.PUBLIC),
                Map.of(type.id(), Sensitivity.SYSTEM_RESERVED), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EffectProfile(
                new EffectProfileId(operationId, "reserved"),
                Risk.SYSTEM_RESERVED, Autonomy.LIVE_INTERACTION));
        assertThrows(IllegalArgumentException.class, () -> new AgentDefinition(
                new AgentId(owner, "reserved"), "Reserved agent", Integrity.SYSTEM_RESERVED,
                Set.of(), Map.of(), Set.of(operationId)));
    }

    @Test void semanticRiskAndAutonomyNamesRetainTheirOrderedRanks() {
        assertEquals(1, Risk.READ.rank());
        assertEquals(2, Risk.WRITE.rank());
        assertEquals(3, Risk.DELETE.rank());
        assertEquals(4, Risk.EXECUTE.rank());
        assertEquals(5, Risk.POTENTIALLY_HARMFUL.rank());
        assertEquals(1, Autonomy.LIVE_INTERACTION.rank());
        assertEquals(2, Autonomy.ASK_ALWAYS.rank());
        assertEquals(3, Autonomy.ASK_ONCE.rank());
        assertEquals(4, Autonomy.ACKNOWLEDGE.rank());
        assertEquals(5, Autonomy.AUTONOMOUS.rank());
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
        OperationDefinition operation = new OperationDefinition(operationId,
                "Answer text", Map.of(type.id(), Privacy.LOCAL),
                Map.of(type.id(), Sensitivity.S4), Map.of());
        AgentId agentId = new AgentId(owner, "interaction");
        WorkflowId workflowId = new WorkflowId(agentId, "repeat-answer");
        WorkflowDefinition workflow = new WorkflowDefinition(workflowId, "Repeat answer behavior",
                List.of(operationId, operationId));
        AgentDefinition agent = new AgentDefinition(agentId, "Interact", Integrity.I4,
                Set.of(), Map.of(workflowId, workflow), Set.of(operationId));
        return new ModuleDefinition(owner, "1.0.0", "Owner module",
                Map.of(type.id(), type.definition()), Map.of(agentId, agent), Map.of(),
                Map.of(operationId, operation), Set.of(operationId));
    }

    private record FixtureReasoning(String value) implements ReasoningComputation<String> {
        @Override public Class<String> resultType() { return String.class; }
    }
}
