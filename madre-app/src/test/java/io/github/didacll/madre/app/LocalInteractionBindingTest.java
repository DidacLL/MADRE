package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.Operation;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class LocalInteractionBindingTest {
    private static final ModuleId MODULE = new ModuleId("test.interaction");
    private static final MaterialType<String> PROMPT = textType("prompt");
    private static final MaterialType<String> COLLECT = textType("collect-request");
    private static final MaterialType<String> ANSWER = textType("answer");

    @Test void absentInteractionNamespaceLeavesPresentationUnconfigured() {
        assertTrue(LocalInteractionBinding.resolve(new Properties(), List.of(instance(false)))
                .isEmpty());
    }

    @Test void resolvesCompatibleInstalledDeclarationsWithoutCoreFacts() {
        LocalInteractionBinding binding = LocalInteractionBinding.resolve(configuration("fast"),
                List.of(instance(false))).orElseThrow();

        assertEquals(MODULE, binding.moduleId());
        assertEquals("fast", binding.defaultOperation());
        assertEquals("standard", binding.standardOperation());
        assertEquals("prompt", binding.promptMaterialType());
        assertEquals(Sensitivity.S5, binding.defaultSensitivity());
        assertEquals("collect", binding.updates().orElseThrow().payload());
        assertEquals(Sensitivity.S1, binding.updates().orElseThrow().sensitivity());
    }

    @Test void rejectsMissingConfiguredModule() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LocalInteractionBinding.resolve(configuration("fast"), List.of()));
        assertTrue(failure.getMessage().contains("configured interaction Module is not installed"));
    }

    @Test void rejectsAmbiguousEffectProfileSelection() {
        Properties properties = configuration("ambiguous");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LocalInteractionBinding.resolve(properties, List.of(instance(true))));
        assertTrue(failure.getMessage().contains("multiple EffectProfiles"));

        properties.setProperty("interaction.default-operation", "ambiguous@first");
        assertEquals("ambiguous@first", LocalInteractionBinding.resolve(properties,
                List.of(instance(true))).orElseThrow().defaultOperation());
    }

    @Test void rejectsSystemReservedDefaultSensitivity() {
        Properties properties = configuration("fast");
        properties.setProperty("interaction.default-sensitivity", "SYSTEM_RESERVED");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LocalInteractionBinding.resolve(properties, List.of(instance(false))));
        assertTrue(failure.getMessage().contains("cannot be SYSTEM_RESERVED"));
    }

    private static Properties configuration(String defaultOperation) {
        Properties properties = new Properties();
        properties.setProperty("interaction.module", MODULE.value());
        properties.setProperty("interaction.default-operation", defaultOperation);
        properties.setProperty("interaction.standard-operation", "standard");
        properties.setProperty("interaction.prompt-material-type", "prompt");
        properties.setProperty("interaction.default-sensitivity", "S5");
        properties.setProperty("interaction.updates-operation", "updates");
        properties.setProperty("interaction.updates-material-type", "collect-request");
        properties.setProperty("interaction.updates-payload", "collect");
        properties.setProperty("interaction.updates-sensitivity", "S1");
        return properties;
    }

    private static ModuleInstance instance(boolean ambiguous) {
        OperationId standardId = new OperationId(MODULE, "standard");
        OperationId fastId = new OperationId(MODULE, "fast");
        OperationId ambiguousId = new OperationId(MODULE, "ambiguous");
        OperationId updatesId = new OperationId(MODULE, "updates");
        EffectProfile fastProfile = new EffectProfile(new EffectProfileId(fastId, "write"),
                Risk.WRITE, Autonomy.AUTONOMOUS);
        EffectProfile first = new EffectProfile(new EffectProfileId(ambiguousId, "first"),
                Risk.WRITE, Autonomy.AUTONOMOUS);
        EffectProfile second = new EffectProfile(new EffectProfileId(ambiguousId, "second"),
                Risk.DELETE, Autonomy.ASK_ONCE);
        EffectProfile updatesProfile = new EffectProfile(new EffectProfileId(updatesId, "delete"),
                Risk.DELETE, Autonomy.LIVE_INTERACTION);
        OperationDefinition standard = operation(standardId, PROMPT, Map.of());
        OperationDefinition fast = operation(fastId, PROMPT,
                Map.of(fastProfile.id(), fastProfile));
        OperationDefinition ambiguousOperation = operation(ambiguousId, PROMPT,
                Map.of(first.id(), first, second.id(), second));
        OperationDefinition updates = operation(updatesId, COLLECT,
                Map.of(updatesProfile.id(), updatesProfile));
        Map<OperationId, OperationDefinition> operations = ambiguous
                ? Map.of(standardId, standard, fastId, fast, ambiguousId, ambiguousOperation,
                        updatesId, updates)
                : Map.of(standardId, standard, fastId, fast, updatesId, updates);
        ModuleDefinition definition = new ModuleDefinition(MODULE, "1.0.0", "interaction fixture",
                Map.of(PROMPT.id(), PROMPT.definition(), COLLECT.id(), COLLECT.definition(),
                        ANSWER.id(), ANSWER.definition()), Set.of(), Map.of(), Map.of(), operations);
        Map<OperationId, OperationBinding<?, ?>> bindings = new LinkedHashMap<>();
        operations.forEach((id, contract) -> bindings.put(id, fixtureBinding(contract)));
        return new ModuleInstance(definition,
                Map.of(PROMPT.id(), PROMPT, COLLECT.id(), COLLECT, ANSWER.id(), ANSWER), bindings);
    }

    private static OperationBinding<String, String> fixtureBinding(OperationDefinition contract) {
        Operation<String, String> operation = Operation.of(call ->
                CompletableFuture.failedFuture(new UnsupportedOperationException("not invoked")));
        return OperationBinding.publicOperation(contract, operation, internal -> internal);
    }

    private static OperationDefinition operation(OperationId id,
            MaterialType<String> input, Map<EffectProfileId, EffectProfile> profiles) {
        return new OperationDefinition(id, "fixture operation", OperationVisibility.PUBLIC,
                Map.of(input.id(), Privacy.SECRET), Map.of(ANSWER.id(), Sensitivity.S5), profiles);
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(MODULE, name), String.class,
                "text/plain; charset=utf-8", new MaterialCodec<>() {
                    @Override public byte[] encode(String value) {
                        return value.getBytes(StandardCharsets.UTF_8);
                    }
                    @Override public String decode(byte[] bytes) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                });
    }
}
