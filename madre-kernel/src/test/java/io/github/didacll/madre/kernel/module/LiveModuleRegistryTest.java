package io.github.didacll.madre.kernel.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class LiveModuleRegistryTest {
    private static final ModuleId CALLEE = new ModuleId("ordinary.module");
    private static final ModuleId CALLER = new ModuleId("calling.module");
    private static final MaterialType<String> CALLER_TEXT = new MaterialType<>(
            new MaterialTypeId(CALLER, "text"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    private static final MaterialType<String> TARGET_COMMAND = new MaterialType<>(
            new MaterialTypeId(CALLEE, "target-command"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    private static final MaterialType<String> TARGET_RESULT = new MaterialType<>(
            new MaterialTypeId(CALLEE, "target-result"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    private static final OperationDefinition EXPOSED = definition("exposed", Sensitivity.S4);
    private static final OperationDefinition UNEXPOSED = definition("unexposed", Sensitivity.S4);
    private static final OperationDefinition DYNAMIC = dynamicDefinition("dynamic", Sensitivity.S4);

    @Test void moduleBoundaryControlsDiscoveryAndCompositionWithoutChangingOperationOntology() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee(Set.of(EXPOSED.id()), true));
        registry.register(caller());
        Material<String> input = callerMaterial("hello", Sensitivity.S2);

        var reachable = registry.directoryFor(CALLER).reachable(
                new ReachabilityQuery(CALLER_TEXT.id(), input.sensitivity()));
        assertEquals(1, reachable.size());
        assertTrue(reachable.getFirst().operations().containsKey(EXPOSED.id()));
        assertTrue(!reachable.getFirst().operations().containsKey(UNEXPOSED.id()));

        OperationCall<String, String> exposedCall = OperationCall.withoutEffect(EXPOSED, input);
        Material<String> result = registry.invokerFor(CALLER).invoke(exposedCall)
                .toCompletableFuture().join();
        assertEquals(CALLEE, result.id().moduleId());
        assertEquals(CALLER_TEXT.id(), result.type().id());
        assertEquals("internal:hello", result.payload());
        assertEquals(Sensitivity.S4, result.sensitivity());

        OperationCall<String, String> unexposedCall = OperationCall.withoutEffect(UNEXPOSED, input);
        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(CALLER).invoke(unexposedCall));
    }

    @Test void structuralDiscoverySupportsExactTargetOwnedContractsWithoutStaticReference() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        AtomicReference<Material<String>> produced = new AtomicReference<>();
        registry.register(dynamicCallee(Set.of(DYNAMIC.id()), Sensitivity.S4, produced));
        registry.register(caller());

        var discovered = registry.directoryFor(CALLER).reachableOperations(Sensitivity.S2);
        assertEquals(1, discovered.size());
        var reachable = discovered.getFirst();
        assertEquals(DYNAMIC, reachable.operation());
        assertEquals(TARGET_COMMAND.definition(),
                reachable.materialTypes().get(TARGET_COMMAND.id()));
        assertEquals(TARGET_RESULT.definition(),
                reachable.materialTypes().get(TARGET_RESULT.id()));

        MaterialType<String> inputType = new MaterialType<>(
                reachable.materialTypes().get(TARGET_COMMAND.id()), String.class,
                MaterialCodecs.utf8String());
        Material<String> input = new Material<>(new MaterialId(CALLER, "dynamic-input"),
                inputType, "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(DYNAMIC, input);
        Material<String> result = registry.invokerFor(CALLER).invoke(call)
                .toCompletableFuture().join();

        assertSame(produced.get(), result);
        assertEquals(CALLEE, result.id().moduleId());
        assertEquals(TARGET_RESULT.id(), result.type().id());
        assertEquals("target:hello", result.payload());
        assertEquals(Sensitivity.S4, result.sensitivity());
    }

    @Test void targetScopedDynamicContractRejectsForgery() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(dynamicCallee(Set.of(DYNAMIC.id()), Sensitivity.S4,
                new AtomicReference<>()));
        registry.register(caller());
        MaterialType<String> forged = new MaterialType<>(
                new MaterialTypeDefinition(TARGET_COMMAND.id(), "application/json"), String.class,
                MaterialCodecs.utf8String());
        Material<String> input = new Material<>(new MaterialId(CALLER, "forged-input"), forged,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(DYNAMIC, input);

        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(CALLER).invoke(call));
    }

    @Test void targetOwnedResultStillEnforcesModuleReceiverBoundary() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        OperationDefinition sensitive = dynamicDefinition("dynamic", Sensitivity.S5);
        registry.register(dynamicCallee(Set.of(sensitive.id()), Sensitivity.S5,
                new AtomicReference<>()));
        registry.register(caller());
        Material<String> input = new Material<>(new MaterialId(CALLER, "sensitive-input"),
                new MaterialType<>(TARGET_COMMAND.definition(), String.class,
                        MaterialCodecs.utf8String()),
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(sensitive, input);

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> registry.invokerFor(CALLER).invoke(call).toCompletableFuture().join());
    }

    @Test void callerOwnedNominalContractAcceptsCalleeOwnedConcreteResult() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee(Set.of(EXPOSED.id()), false));
        registry.register(caller());
        OperationCall<String, String> call = OperationCall.withoutEffect(EXPOSED,
                callerMaterial("contract", Sensitivity.S2));

        Material<String> result = registry.invokerFor(CALLER).invoke(call)
                .toCompletableFuture().join();

        assertEquals(CALLEE, result.id().moduleId());
        assertEquals(CALLER, result.type().id().moduleId());
        assertEquals("internal:contract", result.payload());
    }

    @Test void moduleReceiverStillEnforcesModulePrivacyBoundary() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee(Set.of(EXPOSED.id()), false, Sensitivity.S5));
        registry.register(caller());
        OperationDefinition highOutput = definition("exposed", Sensitivity.S5);
        OperationCall<String, String> call = OperationCall.withoutEffect(highOutput,
                callerMaterial("secret", Sensitivity.S2));

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> registry.invokerFor(CALLER).invoke(call).toCompletableFuture().join());
    }

    @Test void externalPublicDisclosureIsIndependentFromModuleExposure() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee(Set.of(), true));
        Material<String> input = new Material<>(new MaterialId(CALLEE, "host-input"), CALLER_TEXT,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(EXPOSED, input);

        Material<String> result = registry.invokePublic(call).toCompletableFuture().join();

        assertEquals("public:hello", result.payload());
        assertEquals(Sensitivity.S1, result.sensitivity());
        assertEquals(CALLEE, result.id().moduleId());
        assertNotEquals("internal", result.id().value());
    }

    @Test void externalPublicBoundaryRejectsMissingOrUnsafeTransformation() {
        LiveModuleRegistry noDisclosure = new LiveModuleRegistry();
        noDisclosure.register(callee(Set.of(EXPOSED.id()), false));
        Material<String> input = new Material<>(new MaterialId(CALLEE, "host-input"), CALLER_TEXT,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(EXPOSED, input);
        assertThrows(IllegalArgumentException.class, () -> noDisclosure.invokePublic(call));

        LiveModuleRegistry unsafe = new LiveModuleRegistry();
        unsafe.register(calleeWithTransformer(internal -> new Material<>(
                new MaterialId(CALLEE, "still-sensitive"), CALLER_TEXT,
                internal.payload(), Sensitivity.S2)));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> unsafe.invokePublic(call).toCompletableFuture().join());
    }

    @Test void ownerDebugEntryInvokesUnexposedOperationWithoutPublicTransformation() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee(Set.of(), true));
        Material<String> input = new Material<>(new MaterialId(CALLEE, "host-input"), CALLER_TEXT,
                "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(EXPOSED, input);

        Material<String> result = registry.invokeOwner(call).toCompletableFuture().join();

        assertEquals("internal:hello", result.payload());
        assertEquals(Sensitivity.S4, result.sensitivity());
    }

    @Test void exactInstalledContractStillRejectsForgery() {
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(callee(Set.of(EXPOSED.id()), true));
        registry.register(caller());
        OperationDefinition forged = new OperationDefinition(EXPOSED.id(), "forged",
                EXPOSED.acceptedMaterial(), EXPOSED.producedMaterial(), Map.of());
        OperationCall<String, String> call = OperationCall.withoutEffect(forged,
                callerMaterial("hello", Sensitivity.S2));

        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(CALLER).invoke(call));
    }

    private static ModuleInstance callee(Set<OperationId> exposed, boolean publicDisclosure) {
        return callee(exposed, publicDisclosure, Sensitivity.S4);
    }

    private static ModuleInstance callee(Set<OperationId> exposed, boolean publicDisclosure,
            Sensitivity outputSensitivity) {
        OperationDefinition exposedDefinition = definition("exposed", outputSensitivity);
        OperationDefinition unexposedDefinition = definition("unexposed", outputSensitivity);
        Operation<String, String> implementation = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(CALLEE, "internal-" + call.operation().id().name()),
                        CALLER_TEXT, "internal:" + call.input().payload(), outputSensitivity)));
        OperationBinding<String, String> exposedBinding = publicDisclosure
                ? OperationBinding.publicDisclosure(exposedDefinition, implementation,
                        internal -> new Material<>(new MaterialId(CALLEE, "external"),
                                CALLER_TEXT,
                                internal.payload().replaceFirst("^internal:", "public:"),
                                Sensitivity.S1))
                : OperationBinding.operation(exposedDefinition, implementation);
        OperationBinding<String, String> unexposedBinding =
                OperationBinding.operation(unexposedDefinition, implementation);
        ModuleDefinition definition = new ModuleDefinition(CALLEE, "1", "callee", Map.of(),
                Set.of(CALLER_TEXT.id()), Map.of(), Map.of(),
                Map.of(exposedDefinition.id(), exposedDefinition,
                        unexposedDefinition.id(), unexposedDefinition), exposed);
        return new ModuleInstance(definition, Map.of(),
                Map.of(exposedDefinition.id(), exposedBinding,
                        unexposedDefinition.id(), unexposedBinding));
    }

    private static ModuleInstance dynamicCallee(Set<OperationId> exposed,
            Sensitivity outputSensitivity, AtomicReference<Material<String>> produced) {
        OperationDefinition dynamic = dynamicDefinition("dynamic", outputSensitivity);
        Operation<String, String> implementation = Operation.of(call -> {
            Material<String> output = new Material<>(
                    new MaterialId(CALLEE, "dynamic-output"), TARGET_RESULT,
                    "target:" + call.input().payload(), outputSensitivity);
            produced.set(output);
            return CompletableFuture.completedFuture(output);
        });
        ModuleDefinition definition = new ModuleDefinition(CALLEE, "1", "dynamic callee",
                Map.of(TARGET_COMMAND.id(), TARGET_COMMAND.definition(),
                        TARGET_RESULT.id(), TARGET_RESULT.definition()),
                Set.of(), Map.of(), Map.of(), Map.of(dynamic.id(), dynamic), exposed);
        return new ModuleInstance(definition,
                Map.of(TARGET_COMMAND.id(), TARGET_COMMAND, TARGET_RESULT.id(), TARGET_RESULT),
                Map.of(dynamic.id(), OperationBinding.operation(dynamic, implementation)));
    }

    private static ModuleInstance calleeWithTransformer(
            io.github.didacll.madre.sdk.module.PublicResultTransformer<String> transformer) {
        Operation<String, String> implementation = Operation.of(call ->
                CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(CALLEE, "internal"), CALLER_TEXT,
                        "internal:" + call.input().payload(), Sensitivity.S4)));
        ModuleDefinition definition = new ModuleDefinition(CALLEE, "1", "callee", Map.of(),
                Set.of(CALLER_TEXT.id()), Map.of(), Map.of(), Map.of(EXPOSED.id(), EXPOSED),
                Set.of());
        return new ModuleInstance(definition, Map.of(), Map.of(EXPOSED.id(),
                OperationBinding.publicDisclosure(EXPOSED, implementation, transformer)));
    }

    private static ModuleInstance caller() {
        ModuleDefinition definition = new ModuleDefinition(CALLER, "1", "caller",
                Map.of(CALLER_TEXT.id(), CALLER_TEXT.definition()), Set.of(), Map.of(), Map.of(),
                Map.of());
        return new ModuleInstance(definition, Map.of(CALLER_TEXT.id(), CALLER_TEXT), Map.of());
    }

    private static Material<String> callerMaterial(String value, Sensitivity sensitivity) {
        return new Material<>(new MaterialId(CALLER, "input-" + value), CALLER_TEXT,
                value, sensitivity);
    }

    private static OperationDefinition definition(String name, Sensitivity output) {
        return new OperationDefinition(new OperationId(CALLEE, name), name,
                Map.of(CALLER_TEXT.id(), Privacy.MODULE), Map.of(CALLER_TEXT.id(), output), Map.of());
    }

    private static OperationDefinition dynamicDefinition(String name, Sensitivity output) {
        return new OperationDefinition(new OperationId(CALLEE, name), "dynamic text contract",
                Map.of(TARGET_COMMAND.id(), Privacy.MODULE),
                Map.of(TARGET_RESULT.id(), output), Map.of());
    }
}
