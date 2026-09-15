package io.github.didacll.madre.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
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

    @Test
    void utf8StringCodecRoundTripsUnicodeWithoutOwningMaterialSemantics() {
        MaterialCodec<String> codec = MaterialCodecs.utf8String();
        String value = "MADRE — módulo — reasoning";

        assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), codec.encode(value));
        assertEquals(value, codec.decode(codec.encode(value)));
    }

    @Test
    void functionalOperationStillUsesNormalOutputValidation() {
        OperationDefinition<String, String> definition = operationDefinition(
                OperationVisibility.PRIVATE);
        Material<String> input = new Material<>(new MaterialId(OWNER, "input"), TEXT,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(definition, input);
        Operation<String, String> implementation = Operation.of(ignored ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(FOREIGN, "wrong-owner"), TEXT, "result", Sensitivity.S2)));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> implementation.invoke(call).toCompletableFuture().join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void publicBindingStillRequiresExplicitSemanticTransformation() {
        OperationDefinition<String, String> definition = operationDefinition(
                OperationVisibility.PUBLIC);
        Operation<String, String> implementation = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(OWNER, "output"), TEXT, "result", Sensitivity.S2)));

        assertThrows(NullPointerException.class,
                () -> OperationBinding.publicOperation(definition, implementation, null));
    }

    @Test
    void stableModelStillRejectsForeignOwnershipAndNonCanonicalKeys() {
        MaterialType<String> foreignType = new MaterialType<>(
                new MaterialTypeId(FOREIGN, "text"), String.class, "text/plain",
                MaterialCodecs.utf8String());

        assertThrows(IllegalArgumentException.class, () -> new ModuleDefinition(
                OWNER, "1.0.0", "Foreign ownership must fail",
                Map.of(foreignType.id(), foreignType), Set.of(), Map.of(), Map.of(), Map.of()));

        MaterialTypeId alias = new MaterialTypeId(OWNER, "alias");
        assertThrows(IllegalArgumentException.class, () -> new ModuleDefinition(
                OWNER, "1.0.0", "Non-canonical key must fail",
                Map.of(alias, TEXT), Set.of(), Map.of(), Map.of(), Map.of()));
    }

    private static OperationDefinition<String, String> operationDefinition(
            OperationVisibility visibility) {
        return new OperationDefinition<>(new OperationId(OWNER, "run"), "Run authoring test",
                visibility, Map.of(TEXT.id(), Privacy.LOCAL),
                Map.of(TEXT.id(), Sensitivity.S3), Map.of());
    }
}
