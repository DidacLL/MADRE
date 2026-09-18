package io.github.didacll.madre.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

final class AuthoringConvenienceTest {
    private static final ModuleId OWNER = new ModuleId("authoring.owner");
    private static final ModuleId FOREIGN = new ModuleId("authoring.foreign");
    private static final MaterialType<String> TEXT = new MaterialType<>(
            new MaterialTypeId(OWNER, "text"), String.class, "text/plain; charset=utf-8",
            MaterialCodecs.utf8String());
    private static final MaterialType<String> UNDECLARED = new MaterialType<>(
            new MaterialTypeId(OWNER, "undeclared"), String.class, "text/plain; charset=utf-8",
            MaterialCodecs.utf8String());

    @Test
    void utf8StringCodecRoundTripsUnicodeWithoutOwningMaterialSemantics() {
        MaterialCodec<String> codec = MaterialCodecs.utf8String();
        String value = "MADRE — módulo — reasoning";

        assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), codec.encode(value));
        assertEquals(value, codec.decode(codec.encode(value)));
    }

    @Test
    void functionalOperationStillUsesNormalOutputValidation() {
        OperationDefinition definition = operationDefinition();
        Material<String> input = new Material<>(new MaterialId(OWNER, "input"), TEXT,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(definition, input);
        Operation<String, String> implementation = Operation.of(ignored ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(OWNER, "undeclared-output"), UNDECLARED,
                        "result", Sensitivity.S2)));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> implementation.invoke(agent("owner", Set.of(definition.id())), call)
                        .toCompletableFuture().join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void operationBindingRequiresAnAgentThatOwnsLocalExecution() {
        OperationDefinition definition = operationDefinition();
        Operation<String, String> implementation = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(OWNER, "output"), TEXT, "result", Sensitivity.S2)));
        OperationBinding<String, String> binding = OperationBinding.operation(
                definition, implementation);
        Material<String> input = new Material<>(new MaterialId(OWNER, "input"), TEXT,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(definition, input);
        Agent unrelated = agent("unrelated", Set.of());
        Agent owner = agent("owner", Set.of(definition.id()));

        assertThrows(NullPointerException.class, () -> binding.invoke(null, call));
        assertThrows(IllegalArgumentException.class, () -> binding.invoke(unrelated, call));
        assertEquals("result", binding.invoke(owner, call).toCompletableFuture().join().payload());
    }

    @Test
    void stableModelStillRejectsForeignOwnershipAndNonCanonicalKeys() {
        MaterialType<String> foreignType = new MaterialType<>(
                new MaterialTypeId(FOREIGN, "text"), String.class, "text/plain",
                MaterialCodecs.utf8String());

        assertThrows(IllegalArgumentException.class, () -> new ModuleDefinition(
                OWNER, "1.0.0", "Foreign ownership must fail",
                Map.of(foreignType.id(), foreignType.definition()), Set.of(), Map.of(), Map.of(),
                Map.of()));

        MaterialTypeId alias = new MaterialTypeId(OWNER, "alias");
        assertThrows(IllegalArgumentException.class, () -> new ModuleDefinition(
                OWNER, "1.0.0", "Non-canonical key must fail",
                Map.of(alias, TEXT.definition()), Set.of(), Map.of(), Map.of(), Map.of()));
    }

    private static OperationDefinition operationDefinition() {
        return new OperationDefinition(new OperationId(OWNER, "run"), "Run authoring test",
                Map.of(TEXT.id(), Privacy.LOCAL), Map.of(TEXT.id(), Sensitivity.S3), Map.of());
    }

    private static Agent agent(String name, Set<OperationId> operations) {
        return new Agent() {
            @Override public AgentId id() { return new AgentId(OWNER, name); }
            @Override public String purpose() { return "Test acting Agent"; }
            @Override public Set<OperationId> operations() { return operations; }
        };
    }
}
