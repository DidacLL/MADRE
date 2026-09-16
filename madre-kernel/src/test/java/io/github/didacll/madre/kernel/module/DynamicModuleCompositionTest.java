package io.github.didacll.madre.kernel.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class DynamicModuleCompositionTest {
    private static final ModuleId CALLER = new ModuleId("dynamic.caller");
    private static final ModuleId CALLEE = new ModuleId("dynamic.callee");
    private static final MaterialType<String> COMMAND = textType("command");
    private static final MaterialType<String> RESULT = textType("result");
    private static final MaterialType<String> PRIVATE_RESULT = textType("private-result");
    private static final EffectProfile WRITE_PROFILE = new EffectProfile(
            new EffectProfileId(new OperationId(CALLEE, "record"), "write"),
            Risk.WRITE, Autonomy.ASK_ALWAYS);
    private static final OperationDefinition RECORD = new OperationDefinition(
            WRITE_PROFILE.id().operationId(), "Store one bounded value",
            Map.of(COMMAND.id(), Privacy.MODULE), Map.of(RESULT.id(), Sensitivity.S2),
            Map.of(WRITE_PROFILE.id(), WRITE_PROFILE));
    private static final OperationDefinition PRIVATE = new OperationDefinition(
            new OperationId(CALLEE, "private"), "Return Module-private state",
            Map.of(COMMAND.id(), Privacy.MODULE),
            Map.of(PRIVATE_RESULT.id(), Sensitivity.S5), Map.of());
    private static final OperationDefinition INTERNAL = new OperationDefinition(
            new OperationId(CALLEE, "internal"), "Unexposed behavior",
            Map.of(COMMAND.id(), Privacy.MODULE), Map.of(RESULT.id(), Sensitivity.S2), Map.of());

    @Test void broadDirectoryPublishesExposedContractsAndCalleeOwnedNominalTypes() {
        LiveModuleRegistry registry = registry();

        assertTrue(registry.directoryFor(CALLER).reachable(
                new ReachabilityQuery(COMMAND.id(), Sensitivity.S2)).isEmpty(),
                "exact discovery requires a caller-known nominal type");
        List<ReachableModule> broad = registry.directoryFor(CALLER).exposed(Sensitivity.S2);

        assertEquals(1, broad.size());
        ReachableModule callee = broad.getFirst();
        assertEquals(CALLEE, callee.id());
        assertTrue(callee.operations().containsKey(RECORD.id()));
        assertTrue(callee.operations().containsKey(PRIVATE.id()));
        assertFalse(callee.operations().containsKey(INTERNAL.id()));
        assertEquals(COMMAND.definition(), callee.materialTypes().get(COMMAND.id()));
        assertEquals(RESULT.definition(), callee.materialTypes().get(RESULT.id()));
        assertEquals(PRIVATE_RESULT.definition(), callee.materialTypes().get(PRIVATE_RESULT.id()));
    }

    @Test void callerCanUseExactReceiverPublishedNominalContractWithoutStaticForeignReference() {
        LiveModuleRegistry registry = registry();
        ReachableModule reachable = registry.directoryFor(CALLER).exposed(Sensitivity.S2).getFirst();
        MaterialType<String> dynamicInput = new MaterialType<>(
                reachable.materialTypes().get(COMMAND.id()), String.class,
                MaterialCodecs.utf8String());
        Material<String> input = new Material<>(new MaterialId(CALLER, "dynamic-input"),
                dynamicInput, "hello", Sensitivity.S2);
        OperationDefinition operation = reachable.operations().get(RECORD.id());
        EffectProfile profile = operation.effectProfiles().values().iterator().next();
        OperationCall<String, String> call = OperationCall.withEffect(
                operation, profile, input, List.of(Integrity.I2));

        Material<String> result = registry.invokerFor(CALLER).invoke(call)
                .toCompletableFuture().join();

        assertEquals(List.of(Integrity.I2), call.nonUserCausalParticipants());
        assertEquals(CALLEE, result.id().moduleId());
        assertEquals(RESULT.id(), result.type().id());
        assertEquals(RESULT.definition(), result.type().definition());
        assertEquals(Sensitivity.S2, result.sensitivity());
        assertEquals("stored:hello;causal=I2", result.payload());
    }

    @Test void dynamicNominalUseStillRejectsForgeryUnexposedBehaviorAndTooSensitiveReturn() {
        LiveModuleRegistry registry = registry();
        ReachableModule reachable = registry.directoryFor(CALLER).exposed(Sensitivity.S2).getFirst();
        MaterialType<String> forgedType = new MaterialType<>(COMMAND.id(), String.class,
                "application/json", MaterialCodecs.utf8String());
        Material<String> forgedInput = new Material<>(new MaterialId(CALLER, "forged"),
                forgedType, "hello", Sensitivity.S2);
        assertThrows(IllegalArgumentException.class, () -> registry.invokerFor(CALLER).invoke(
                OperationCall.withEffect(RECORD, WRITE_PROFILE, forgedInput,
                        List.of(Integrity.I2))));

        MaterialType<String> dynamicInput = new MaterialType<>(
                reachable.materialTypes().get(COMMAND.id()), String.class,
                MaterialCodecs.utf8String());
        Material<String> input = new Material<>(new MaterialId(CALLER, "real"),
                dynamicInput, "hello", Sensitivity.S2);
        assertThrows(IllegalArgumentException.class, () -> registry.invokerFor(CALLER).invoke(
                OperationCall.withoutEffect(INTERNAL, input)));

        OperationCall<String, String> privateCall = OperationCall.withoutEffect(PRIVATE, input);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> registry.invokerFor(CALLER).invoke(privateCall).toCompletableFuture().join());
    }

    private static LiveModuleRegistry registry() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee());
        registry.register(caller());
        return registry;
    }

    private static ModuleInstance caller() {
        ModuleDefinition definition = new ModuleDefinition(CALLER, "1", "Dynamic caller",
                Map.of(), Set.of(), Map.of(), Map.of(), Map.of());
        return new ModuleInstance(definition, Map.of(), Map.of());
    }

    private static ModuleInstance callee() {
        Operation<String, String> record = Operation.of(call -> {
            if (!call.nonUserCausalParticipants().equals(List.of(Integrity.I2))) {
                throw new IllegalStateException("write must receive actual I2 causal participant");
            }
            return CompletableFuture.completedFuture(new Material<>(
                    new MaterialId(CALLEE, "record-" + UUID.randomUUID()), RESULT,
                    "stored:" + call.input().payload() + ";causal=I2", Sensitivity.S2));
        });
        Operation<String, String> privateRead = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(CALLEE, "private-" + UUID.randomUUID()), PRIVATE_RESULT,
                        "private:" + call.input().payload(), Sensitivity.S5)));
        Operation<String, String> internal = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(CALLEE, "internal-" + UUID.randomUUID()), RESULT,
                        "internal:" + call.input().payload(), Sensitivity.S2)));
        ModuleDefinition definition = new ModuleDefinition(CALLEE, "1", "Dynamic callee",
                Map.of(COMMAND.id(), COMMAND.definition(), RESULT.id(), RESULT.definition(),
                        PRIVATE_RESULT.id(), PRIVATE_RESULT.definition()),
                Set.of(), Map.of(), Map.of(),
                Map.of(RECORD.id(), RECORD, PRIVATE.id(), PRIVATE, INTERNAL.id(), INTERNAL),
                Set.of(RECORD.id(), PRIVATE.id()));
        return new ModuleInstance(definition,
                Map.of(COMMAND.id(), COMMAND, RESULT.id(), RESULT,
                        PRIVATE_RESULT.id(), PRIVATE_RESULT),
                Map.of(RECORD.id(), OperationBinding.operation(RECORD, record),
                        PRIVATE.id(), OperationBinding.operation(PRIVATE, privateRead),
                        INTERNAL.id(), OperationBinding.operation(INTERNAL, internal)));
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(CALLEE, name), String.class,
                "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    }
}
