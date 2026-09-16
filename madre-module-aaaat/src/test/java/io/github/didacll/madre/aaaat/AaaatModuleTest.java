package io.github.didacll.madre.aaaat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.module.LiveModuleRegistry;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AaaatModuleTest {
    @TempDir Path temp;

    @Test void ordinaryModuleDiscoversAndInvokesExposedReadWithCallerOwnedForeignType() throws Exception {
        AaaatModule module = new AaaatModule(client(fixture(false)));
        ModuleInstance aaaat = ModuleInstance.from(module);
        ModuleId callerId = new ModuleId("independent.caller");
        Set<MaterialTypeId> references = Set.of(
                AaaatContracts.OPPORTUNITY_RESEARCH_REQUEST,
                AaaatContracts.OPPORTUNITY_RESEARCH_CONTEXT);
        ModuleDefinition caller = new ModuleDefinition(callerId, "1", "Independent caller",
                Map.of(), references, Map.of(), Map.of(), Map.of());

        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(aaaat);
        registry.register(new ModuleInstance(caller, Map.of(), Map.of()));

        var reachable = registry.directoryFor(callerId).reachable(new ReachabilityQuery(
                AaaatContracts.OPPORTUNITY_RESEARCH_REQUEST, Sensitivity.S4));
        assertEquals(1, reachable.size());
        assertTrue(reachable.getFirst().operations().containsKey(
                AaaatContracts.OPPORTUNITY_RESEARCH_READ));
        assertFalse(reachable.getFirst().operations().containsKey(AaaatContracts.CAREER_CONTEXT_READ));

        MaterialType<String> requestType = stringType(aaaat,
                AaaatContracts.OPPORTUNITY_RESEARCH_REQUEST);
        OperationDefinition operation = aaaat.definition().operations().get(
                AaaatContracts.OPPORTUNITY_RESEARCH_READ);
        Material<String> request = new Material<>(new MaterialId(callerId, "request"),
                requestType, "{}", Sensitivity.S4);
        Material<String> result = registry.invokerFor(callerId)
                .invoke(OperationCall.<String, String>withoutEffect(operation, request))
                .toCompletableFuture().join();

        assertEquals(AaaatContracts.MODULE_ID, result.id().moduleId());
        assertEquals(Sensitivity.S4, result.sensitivity());
        assertTrue(result.payload().contains("Senior Engineer"));
    }

    @Test void privateOperationIsNotModuleCallable() throws Exception {
        AaaatModule module = new AaaatModule(client(fixture(false)));
        ModuleInstance aaaat = ModuleInstance.from(module);
        ModuleId callerId = new ModuleId("independent.caller");
        Set<MaterialTypeId> references = Set.of(
                AaaatContracts.CAREER_CONTEXT_REQUEST, AaaatContracts.CAREER_CONTEXT);
        ModuleDefinition caller = new ModuleDefinition(callerId, "1", "Independent caller",
                Map.of(), references, Map.of(), Map.of(), Map.of());
        LiveModuleRegistry registry = new LiveModuleRegistry();
        registry.register(aaaat);
        registry.register(new ModuleInstance(caller, Map.of(), Map.of()));

        assertTrue(registry.directoryFor(callerId).reachable(new ReachabilityQuery(
                AaaatContracts.CAREER_CONTEXT_REQUEST, Sensitivity.S4)).isEmpty());
        Material<String> request = new Material<>(new MaterialId(callerId, "private-request"),
                stringType(aaaat, AaaatContracts.CAREER_CONTEXT_REQUEST), "{}", Sensitivity.S4);
        OperationDefinition operation = aaaat.definition().operations().get(
                AaaatContracts.CAREER_CONTEXT_READ);
        assertEquals(OperationVisibility.PRIVATE, operation.visibility());
        assertThrows(IllegalArgumentException.class, () -> registry.invokerFor(callerId)
                .invoke(OperationCall.<String, String>withoutEffect(operation, request)));
    }

    @Test void consequentialWriteRequiresDeclaredEffectAndSufficientCausalIntegrity() throws Exception {
        ModuleInstance aaaat = ModuleInstance.from(new AaaatModule(client(fixture(false))));
        OperationDefinition operation = aaaat.definition().operations().get(
                AaaatContracts.CANDIDATURE_SOURCE_ADD);
        var effect = operation.effectProfiles().values().iterator().next();
        assertEquals(Risk.WRITE, effect.risk());
        assertEquals(Autonomy.ASK_ALWAYS, effect.autonomy());

        ModuleId callerId = new ModuleId("independent.writer");
        Material<String> input = new Material<>(new MaterialId(callerId, "source"),
                stringType(aaaat, AaaatContracts.CANDIDATURE_SOURCE_ADD_REQUEST),
                "{\"sourceType\":\"url\",\"value\":\"https://example.invalid/job\"}",
                Sensitivity.S4);
        assertThrows(IllegalArgumentException.class,
                () -> OperationCall.<String, String>withoutEffect(operation, input));
        assertThrows(IllegalArgumentException.class,
                () -> OperationCall.<String, String>withEffect(operation, effect, input,
                        List.of(Integrity.I1)));
        OperationCall<String, String> admitted = OperationCall.withEffect(
                operation, effect, input, List.of(Integrity.I5));
        assertEquals(effect, admitted.effectProfile().orElseThrow());
    }

    @Test void protocolAndProcessFailuresRemainExceptional() throws Exception {
        AaaatMcpClient missing = new AaaatMcpClient(temp.resolve("missing-aaaat"), temp);
        assertThrows(IllegalStateException.class, () -> missing.call("x", "{}"));

        Path broken = script("broken-aaaat", """
                #!/bin/sh
                IFS= read -r init
                printf '%s\\n' '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18"}}'
                exit 0
                """);
        AaaatMcpClient brokenClient = client(broken);
        assertThrows(IllegalStateException.class,
                () -> brokenClient.call("opportunity_research_context_read", "{}"));
    }

    private AaaatMcpClient client(Path executable) {
        return new AaaatMcpClient(executable, temp.resolve("workspace"));
    }

    private Path fixture(boolean error) throws Exception {
        Files.createDirectories(temp.resolve("workspace"));
        String toolResult = error
                ? "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"failure\"}]}}"
                : "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"title\\\":\\\"Senior Engineer\\\",\\\"retained\\\":true}\"}]}}";
        return script("fake-aaaat", """
                #!/bin/sh
                IFS= read -r init
                printf '%s\\n' '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18"}}'
                IFS= read -r initialized
                IFS= read -r call
                printf '%s\\n' '%s'
                """.formatted(toolResult));
    }

    private Path script(String name, String source) throws Exception {
        Path path = temp.resolve(name);
        Files.writeString(path, source.stripLeading());
        assertTrue(path.toFile().setExecutable(true));
        return path;
    }

    @SuppressWarnings("unchecked")
    private static MaterialType<String> stringType(ModuleInstance instance, MaterialTypeId id) {
        return (MaterialType<String>) instance.materialTypes().get(id);
    }
}
