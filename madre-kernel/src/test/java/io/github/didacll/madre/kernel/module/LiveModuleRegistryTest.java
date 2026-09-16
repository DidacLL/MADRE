package io.github.didacll.madre.kernel.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

final class LiveModuleRegistryTest {
    private static final ModuleId CALLEE = new ModuleId("ordinary.module");
    private static final ModuleId CALLER = new ModuleId("calling.module");
    private static final MaterialType<String> CALLER_TEXT = new MaterialType<>(
            new MaterialTypeId(CALLER, "text"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    private static final OperationDefinition EXPOSED = definition("exposed", Sensitivity.S4);
    private static final OperationDefinition UNEXPOSED = definition("unexposed", Sensitivity.S4);

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
}
