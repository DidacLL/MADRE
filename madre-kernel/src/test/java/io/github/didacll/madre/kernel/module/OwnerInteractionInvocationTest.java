package io.github.didacll.madre.kernel.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class OwnerInteractionInvocationTest {
    private static final ModuleId MODULE = new ModuleId("owner.interaction.fixture");
    private static final MaterialType<String> INPUT = new MaterialType<>(
            new MaterialTypeId(MODULE, "input"), String.class, "text/plain",
            MaterialCodecs.utf8String());
    private static final MaterialType<String> OUTPUT = new MaterialType<>(
            new MaterialTypeId(MODULE, "output"), String.class, "text/plain",
            MaterialCodecs.utf8String());
    private static final OperationDefinition INTERACTION = definition("interaction");
    private static final OperationDefinition PLAIN_PRIVATE = definition("private");

    @Test void explicitPrivateInteractionEntryIsHostReachableButNotPublicOrComposable() {
        LiveModuleRegistry registry = new LiveModuleRegistry(MODULE);
        registry.register(instance());
        Material<String> input = new Material<>(new MaterialId(MODULE, "input-1"), INPUT,
                "secret", Sensitivity.S5);
        OperationCall<String, String> call = OperationCall.withoutEffect(INTERACTION, input);

        assertEquals("answer:secret", registry.invokeOwnerInteraction(call)
                .toCompletableFuture().join().payload());
        assertThrows(IllegalArgumentException.class, () -> registry.invokePublic(call));
        assertThrows(IllegalArgumentException.class, () -> registry.invokeOwner(call));

        ModuleId caller = new ModuleId("ordinary.caller");
        registry.register(new ModuleInstance(new ModuleDefinition(caller, "1", "caller",
                Map.of(), Set.of(INPUT.id(), OUTPUT.id()), Map.of(), Map.of(), Map.of()),
                Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(caller).invoke(call));
    }

    @Test void genericPrivateOperationDoesNotBecomeHostReachableThroughCoreDesignation() {
        LiveModuleRegistry registry = new LiveModuleRegistry(MODULE);
        registry.register(instance());
        Material<String> input = new Material<>(new MaterialId(MODULE, "input-2"), INPUT,
                "secret", Sensitivity.S5);
        OperationCall<String, String> call = OperationCall.withoutEffect(PLAIN_PRIVATE, input);

        assertThrows(IllegalArgumentException.class, () -> registry.invokeOwnerInteraction(call));
    }

    private static ModuleInstance instance() {
        Operation<String, String> implementation = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(MODULE, "result-" + call.input().id().value()), OUTPUT,
                        "answer:" + call.input().payload(), call.input().sensitivity())));
        Map<OperationId, OperationDefinition> operations = Map.of(
                INTERACTION.id(), INTERACTION, PLAIN_PRIVATE.id(), PLAIN_PRIVATE);
        ModuleDefinition definition = new ModuleDefinition(MODULE, "1", "fixture",
                Map.of(INPUT.id(), INPUT.definition(), OUTPUT.id(), OUTPUT.definition()), Set.of(),
                Map.of(), Map.of(), operations);
        return new ModuleInstance(definition, Map.of(INPUT.id(), INPUT, OUTPUT.id(), OUTPUT),
                Map.of(INTERACTION.id(), OperationBinding.ownerInteractionOperation(
                                INTERACTION, implementation),
                        PLAIN_PRIVATE.id(), OperationBinding.privateOperation(
                                PLAIN_PRIVATE, implementation)));
    }

    private static OperationDefinition definition(String name) {
        return new OperationDefinition(new OperationId(MODULE, name), name,
                OperationVisibility.PRIVATE, Map.of(INPUT.id(), Privacy.SECRET),
                Map.of(OUTPUT.id(), Sensitivity.S5), Map.of());
    }
}
