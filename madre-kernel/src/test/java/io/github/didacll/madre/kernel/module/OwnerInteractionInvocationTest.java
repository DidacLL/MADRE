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
    private static final OperationDefinition PLAIN = definition("plain");

    @Test void explicitInteractionEntryIsCoreHostReachableButNotComposableOrPublic() {
        LiveModuleRegistry registry = new LiveModuleRegistry(MODULE);
        registry.register(instance());
        Material<String> input = new Material<>(new MaterialId(MODULE, "input-1"), INPUT,
                "secret", Sensitivity.S5);
        OperationCall<String, String> call = OperationCall.withoutEffect(INTERACTION, input);

        assertEquals("answer:secret", registry.invokeOwnerInteraction(call)
                .toCompletableFuture().join().payload());
        assertThrows(IllegalArgumentException.class, () -> registry.invokePublic(call));

        ModuleId caller = new ModuleId("ordinary.caller");
        registry.register(new ModuleInstance(new ModuleDefinition(caller, "1", "caller",
                Map.of(), Set.of(INPUT.id(), OUTPUT.id()), Map.of(), Map.of(), Map.of()),
                Map.of(), Map.of()));
        Material<String> callerInput = new Material<>(new MaterialId(caller, "input"), INPUT,
                "secret", Sensitivity.S5);
        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(caller).invoke(
                        OperationCall.withoutEffect(INTERACTION, callerInput)));
    }

    @Test void coreAssignmentDoesNotTurnOrdinaryOperationsIntoInteractionEntries() {
        LiveModuleRegistry registry = new LiveModuleRegistry(MODULE);
        registry.register(instance());
        Material<String> input = new Material<>(new MaterialId(MODULE, "input-2"), INPUT,
                "secret", Sensitivity.S5);
        OperationCall<String, String> call = OperationCall.withoutEffect(PLAIN, input);

        assertThrows(IllegalArgumentException.class, () -> registry.invokeOwnerInteraction(call));
        assertEquals("answer:secret", registry.invokeOwner(call).toCompletableFuture().join().payload());
    }

    @Test void interactionEntryMustBelongToTheAssignedCoreModule() {
        LiveModuleRegistry unassigned = new LiveModuleRegistry();
        unassigned.register(instance());
        Material<String> input = new Material<>(new MaterialId(MODULE, "input-3"), INPUT,
                "secret", Sensitivity.S5);
        OperationCall<String, String> call = OperationCall.withoutEffect(INTERACTION, input);

        assertThrows(IllegalStateException.class, () -> unassigned.invokeOwnerInteraction(call));
    }

    private static ModuleInstance instance() {
        Operation<String, String> implementation = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(MODULE, "result-" + call.input().id().value()), OUTPUT,
                        "answer:" + call.input().payload(), call.input().sensitivity())));
        Map<OperationId, OperationDefinition> operations = Map.of(
                INTERACTION.id(), INTERACTION, PLAIN.id(), PLAIN);
        ModuleDefinition definition = new ModuleDefinition(MODULE, "1", "fixture",
                Map.of(INPUT.id(), INPUT.definition(), OUTPUT.id(), OUTPUT.definition()), Set.of(),
                Map.of(), Map.of(), operations, Set.of());
        return new ModuleInstance(definition, Map.of(INPUT.id(), INPUT, OUTPUT.id(), OUTPUT),
                Map.of(INTERACTION.id(), OperationBinding.ownerInteractionOperation(
                                INTERACTION, implementation),
                        PLAIN.id(), OperationBinding.operation(PLAIN, implementation)));
    }

    private static OperationDefinition definition(String name) {
        return new OperationDefinition(new OperationId(MODULE, name), name,
                Map.of(INPUT.id(), Privacy.SECRET), Map.of(OUTPUT.id(), Sensitivity.S5), Map.of());
    }
}
