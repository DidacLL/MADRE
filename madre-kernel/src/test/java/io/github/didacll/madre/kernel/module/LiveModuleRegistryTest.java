package io.github.didacll.madre.kernel.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class LiveModuleRegistryTest {
    private static final MaterialCodec<String> STRINGS = new MaterialCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    @Test void registryFiltersReachabilityAndCoreIsOnlyAnOptionalLiveIdentity() {
        Fixture fixture = fixture();
        LiveModuleRegistry registry = new LiveModuleRegistry(fixture.moduleId());
        var registration = registry.register(fixture.instance());
        var directory = registry.directoryFor(fixture.moduleId());
        assertEquals(1, directory.reachable(new ReachabilityQuery(
                fixture.input().id(), Sensitivity.S2)).size());
        assertTrue(directory.reachable(new ReachabilityQuery(
                fixture.input().id(), Sensitivity.S4)).isEmpty());
        assertEquals(fixture.moduleId(), registry.resolvedCore().orElseThrow());
        registration.close();
        assertTrue(registry.resolvedCore().isEmpty());
        assertTrue(new LiveModuleRegistry().resolvedCore().isEmpty());
    }

    @Test void moduleReceiverPreservesCalleeMaterialWithoutPublicTransformation() {
        Fixture fixture = fixture();
        ModuleId callerId = new ModuleId("calling.module");
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(fixture.instance());
        registry.register(receiver(callerId, Set.of(fixture.input().id(), fixture.output().id())));
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "foreign-input"),
                fixture.input(), "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);

        Material<String> result = registry.invokerFor(callerId).invoke(call)
                .toCompletableFuture().join();

        assertEquals("internal:hello", result.payload());
        assertEquals(Sensitivity.S4, result.sensitivity());
        assertEquals(fixture.moduleId(), result.id().moduleId());
        assertEquals("internal-result", result.id().value());
    }

    @Test void moduleReceiverIdentityIsBoundAndUndeclaredResultsNeverReachCaller() {
        Fixture fixture = fixture();
        ModuleId allowedId = new ModuleId("allowed.module");
        ModuleId deniedId = new ModuleId("denied.module");
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(fixture.instance());
        registry.register(receiver(allowedId,
                Set.of(fixture.input().id(), fixture.output().id())));
        registry.register(receiver(deniedId, Set.of(fixture.input().id())));
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "foreign-input"),
                fixture.input(), "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);

        assertEquals("internal:hello", registry.invokerFor(allowedId).invoke(call)
                .toCompletableFuture().join().payload());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> registry.invokerFor(deniedId).invoke(call).toCompletableFuture().join());
    }

    @Test void moduleReceiverRejectsSensitivityAboveModulePrivacyBeforeExposure() {
        Fixture fixture = createFixture(false, Sensitivity.S5);
        ModuleId callerId = new ModuleId("calling.module");
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(fixture.instance());
        registry.register(receiver(callerId, Set.of(fixture.input().id(), fixture.output().id())));
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "foreign-input"),
                fixture.input(), "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> registry.invokerFor(callerId).invoke(call).toCompletableFuture().join());
    }

    @Test void publicInvocationReturnsOnlyModuleTransformedPublicMaterial() {
        Fixture fixture = fixture();
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(fixture.instance());
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "input"),
                fixture.input(), "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);

        Material<String> result = registry.invokePublic(call).toCompletableFuture().join();

        assertEquals("public:hello", result.payload());
        assertEquals(Sensitivity.S1, result.sensitivity());
        assertNotEquals("internal-result", result.id().value());
    }

    @Test void ownerInvocationReturnsValidatedModuleMaterialWithoutPublicTransformation() {
        Fixture fixture = fixture();
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(fixture.instance());
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "input"),
                fixture.input(), "hello", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);

        Material<String> result = registry.invokeOwner(call).toCompletableFuture().join();

        assertEquals("internal:hello", result.payload());
        assertEquals(Sensitivity.S4, result.sensitivity());
        assertEquals("internal-result", result.id().value());
    }

    @Test void coreDesignationDoesNotChangeEitherInvocationBoundary() {
        Fixture fixture = fixture();
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "input"),
                fixture.input(), "same", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);
        LiveModuleRegistry ordinary = new LiveModuleRegistry();
        LiveModuleRegistry coreAssigned = new LiveModuleRegistry(fixture.moduleId());
        ordinary.register(fixture.instance());
        coreAssigned.register(fixture.instance());

        assertEquals(ordinary.invokePublic(call).toCompletableFuture().join().payload(),
                coreAssigned.invokePublic(call).toCompletableFuture().join().payload());
        Material<String> ordinaryOwner = ordinary.invokeOwner(call).toCompletableFuture().join();
        Material<String> coreOwner = coreAssigned.invokeOwner(call).toCompletableFuture().join();
        assertEquals(ordinaryOwner.payload(), coreOwner.payload());
        assertEquals(ordinaryOwner.sensitivity(), coreOwner.sensitivity());
    }

    @Test void moduleAssemblyRejectsMissingUndeclaredAndMismatchedBindings() {
        Fixture fixture = fixture();
        Map<MaterialTypeId, MaterialType<?>> types = Map.of(
                fixture.input().id(), fixture.input(), fixture.output().id(), fixture.output());
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleInstance(fixture.definition(), types, Map.of()));

        OperationDefinition impostor = new OperationDefinition(
                fixture.publicOperation().id(), "Impostor", OperationVisibility.PUBLIC,
                fixture.publicOperation().acceptedMaterial(),
                fixture.publicOperation().producedMaterial(), Map.of());
        OperationBinding<String, String> impostorBinding = OperationBinding.publicOperation(
                impostor, fixture.implementation(), fixture.transformer());
        assertThrows(IllegalArgumentException.class, () -> new ModuleInstance(
                fixture.definition(), types, Map.of(impostor.id(), impostorBinding)));

        OperationId undeclaredId = new OperationId(fixture.moduleId(), "undeclared");
        OperationDefinition undeclared = new OperationDefinition(undeclaredId,
                "Undeclared", OperationVisibility.PUBLIC,
                Map.of(fixture.input().id(), Privacy.UNKNOWN),
                Map.of(fixture.output().id(), Sensitivity.S4), Map.of());
        OperationBinding<String, String> undeclaredBinding = OperationBinding.publicOperation(
                undeclared, fixture.implementation(), fixture.transformer());
        assertThrows(IllegalArgumentException.class, () -> new ModuleInstance(
                fixture.definition(), types, Map.of(undeclaredId, undeclaredBinding)));
    }

    @Test void invocationRejectsPrivateAndForgedCallsAtAllReceiverBoundaries() {
        Fixture fixture = fixtureWithPrivateOperation();
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(fixture.instance());
        ModuleId callerId = new ModuleId("calling.module");
        registry.register(receiver(callerId, Set.of(fixture.input().id(), fixture.output().id())));
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "input"),
                fixture.input(), "hello", Sensitivity.S2);

        OperationCall<String, String> privateCall = OperationCall.withoutEffect(
                fixture.privateOperation(), input);
        assertThrows(IllegalArgumentException.class, () -> registry.invokePublic(privateCall));
        assertThrows(IllegalArgumentException.class, () -> registry.invokeOwner(privateCall));
        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(callerId).invoke(privateCall));

        OperationDefinition forged = new OperationDefinition(
                fixture.publicOperation().id(), "Forged call", OperationVisibility.PUBLIC,
                fixture.publicOperation().acceptedMaterial(),
                fixture.publicOperation().producedMaterial(), Map.of());
        OperationCall<String, String> forgedCall = OperationCall.withoutEffect(forged, input);
        assertThrows(IllegalArgumentException.class, () -> registry.invokePublic(forgedCall));
        assertThrows(IllegalArgumentException.class, () -> registry.invokeOwner(forgedCall));
        assertThrows(IllegalArgumentException.class,
                () -> registry.invokerFor(callerId).invoke(forgedCall));
    }

    @Test void publicBoundaryRejectsTransformerThatReturnsRawOrNonPublicMaterial() {
        Fixture fixture = fixture();
        Map<MaterialTypeId, MaterialType<?>> types = Map.of(
                fixture.input().id(), fixture.input(), fixture.output().id(), fixture.output());
        LiveModuleRegistry rawRegistry = new LiveModuleRegistry();
        OperationBinding<String, String> rawBinding = OperationBinding.publicOperation(
                fixture.publicOperation(), fixture.implementation(), internal -> internal);
        rawRegistry.register(new ModuleInstance(fixture.definition(), types,
                Map.of(fixture.publicOperation().id(), rawBinding)));
        Material<String> input = new Material<>(new MaterialId(fixture.moduleId(), "input"),
                fixture.input(), "secret", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(
                fixture.publicOperation(), input);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> rawRegistry.invokePublic(call).toCompletableFuture().join());

        LiveModuleRegistry sensitiveRegistry = new LiveModuleRegistry();
        OperationBinding<String, String> sensitiveBinding = OperationBinding.publicOperation(
                fixture.publicOperation(), fixture.implementation(), internal ->
                        new Material<>(new MaterialId(fixture.moduleId(), "new-sensitive"),
                                fixture.output(), internal.payload(), Sensitivity.S2));
        sensitiveRegistry.register(new ModuleInstance(fixture.definition(), types,
                Map.of(fixture.publicOperation().id(), sensitiveBinding)));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> sensitiveRegistry.invokePublic(call).toCompletableFuture().join());
    }

    private static Fixture fixture() {
        return createFixture(false, Sensitivity.S4);
    }

    private static Fixture fixtureWithPrivateOperation() {
        return createFixture(true, Sensitivity.S4);
    }

    private static Fixture createFixture(boolean includePrivate, Sensitivity outputSensitivity) {
        ModuleId moduleId = new ModuleId("ordinary.module");
        MaterialType<String> input = new MaterialType<>(new MaterialTypeId(moduleId, "input"),
                String.class, "text/plain", STRINGS);
        MaterialType<String> output = new MaterialType<>(new MaterialTypeId(moduleId, "output"),
                String.class, "text/plain", STRINGS);
        OperationId publicId = new OperationId(moduleId, "receive");
        OperationDefinition publicOperation = new OperationDefinition(publicId,
                "Receive text", OperationVisibility.PUBLIC,
                Map.of(input.id(), Privacy.UNKNOWN), Map.of(output.id(), outputSensitivity), Map.of());
        Operation<String, String> implementation = new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                return CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(moduleId, "internal-result"), output,
                        "internal:" + call.input().payload(), outputSensitivity));
            }
        };
        var transformer = (io.github.didacll.madre.sdk.module.PublicResultTransformer<String>)
                internal -> new Material<>(new MaterialId(moduleId,
                        "external-" + internal.id().value()), output,
                        internal.payload().replaceFirst("^internal:", "public:"), Sensitivity.S1);
        Map<OperationId, OperationDefinition> declarations;
        Map<OperationId, OperationBinding<?, ?>> bindings;
        OperationDefinition privateOperation = null;
        if (includePrivate) {
            OperationId privateId = new OperationId(moduleId, "private");
            privateOperation = new OperationDefinition(privateId, "Private text",
                    OperationVisibility.PRIVATE, Map.of(input.id(), Privacy.UNKNOWN),
                    Map.of(output.id(), outputSensitivity), Map.of());
            declarations = Map.of(publicId, publicOperation, privateId, privateOperation);
            bindings = Map.of(publicId, OperationBinding.publicOperation(publicOperation,
                            implementation, transformer),
                    privateId, OperationBinding.privateOperation(privateOperation, implementation));
        } else {
            declarations = Map.of(publicId, publicOperation);
            bindings = Map.of(publicId, OperationBinding.publicOperation(publicOperation,
                    implementation, transformer));
        }
        AgentId agentId = new AgentId(moduleId, "interaction");
        ModuleDefinition definition = new ModuleDefinition(moduleId, "1", "Ordinary module",
                Map.of(input.id(), input.definition(), output.id(), output.definition()), Set.of(),
                Map.of(agentId, new AgentDefinition(agentId, "Interaction", Integrity.I5,
                        Set.of(), Map.of(), declarations.keySet())),
                Map.of(), declarations);
        Map<MaterialTypeId, MaterialType<?>> types = Map.of(
                input.id(), input, output.id(), output);
        return new Fixture(moduleId, input, output, publicOperation, privateOperation,
                implementation, transformer, definition,
                new ModuleInstance(definition, types, bindings));
    }

    private static ModuleInstance receiver(ModuleId moduleId, Set<MaterialTypeId> references) {
        ModuleDefinition definition = new ModuleDefinition(moduleId, "1", "Receiving module",
                Map.of(), references, Map.of(), Map.of(), Map.of());
        return new ModuleInstance(definition, Map.of(), Map.of());
    }

    private record Fixture(ModuleId moduleId, MaterialType<String> input,
            MaterialType<String> output, OperationDefinition publicOperation,
            OperationDefinition privateOperation,
            Operation<String, String> implementation,
            io.github.didacll.madre.sdk.module.PublicResultTransformer<String> transformer,
            ModuleDefinition definition, ModuleInstance instance) { }
}
