package io.github.didacll.madre.adapter.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleReasoningProviderConfigurationTest {
    @Test void configuresDisablesAndRemovesOneNamedInstanceWithoutNetworkAccess() {
        OpenAiCompatibleReasoningProvider provider = new OpenAiCompatibleReasoningProvider();
        ReasoningProviderConfiguration initial = new ReasoningProviderConfiguration(Map.of(
                "reasoning.foreign.instances", "keep"));

        var update = provider.configurator().configure("primary", Map.of(
                "capability-id", "compatible-text",
                "endpoint", "http://127.0.0.1:8081/v1/",
                "model", "compatible-model",
                "privacy", "SECRET",
                "location", "LOCAL"), initial);
        ReasoningProviderConfiguration configured = initial.applying(update);

        assertEquals("openai-compatible", provider.descriptor().id().value());
        assertEquals(1, provider.configurator().configuredInstances(configured).size());
        assertTrue(provider.configurator().configuredInstances(configured).getFirst().enabled());
        assertEquals(1, provider.materialize(configured).size());
        assertEquals("keep", configured.value("reasoning.foreign.instances").orElseThrow());

        ReasoningProviderConfiguration disabled = configured.applying(
                provider.configurator().setEnabled("primary", false, configured));
        assertFalse(provider.configurator().configuredInstances(disabled).getFirst().enabled());
        assertEquals(0, provider.materialize(disabled).size());
        assertEquals("http://127.0.0.1:8081/v1/",
                disabled.value("reasoning.openai-compatible.primary.endpoint").orElseThrow());

        ReasoningProviderConfiguration removed = disabled.applying(
                provider.configurator().remove("primary", disabled));
        assertTrue(provider.configurator().configuredInstances(removed).isEmpty());
        assertEquals("keep", removed.value("reasoning.foreign.instances").orElseThrow());
    }

    @Test void providerOwnedValidationRejectsMalformedInputBeforeProducingAnUpdate() {
        OpenAiCompatibleReasoningProvider provider = new OpenAiCompatibleReasoningProvider();
        ReasoningProviderConfiguration empty = new ReasoningProviderConfiguration(Map.of());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> provider.configurator().configure("bad", Map.of(
                        "capability-id", "bad",
                        "endpoint", "relative/path",
                        "model", "model",
                        "privacy", "SECRET",
                        "location", "LOCAL"), empty));
        assertTrue(failure.getMessage().contains("absolute"));
        assertTrue(empty.keys().isEmpty());
    }
}
