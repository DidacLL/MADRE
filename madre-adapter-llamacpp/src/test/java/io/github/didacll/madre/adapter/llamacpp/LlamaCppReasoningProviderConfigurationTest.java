package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LlamaCppReasoningProviderConfigurationTest {
    @Test void transportShapesExposeDistinctStableProviderIdentities() {
        assertEquals("llamacpp-http", new LlamaCppHttpReasoningProvider().descriptor().id().value());
        assertEquals("llamacpp-unix",
                new LlamaCppUnixSocketReasoningProvider().descriptor().id().value());
    }

    @Test void httpConfigurationUsesProviderOwnedFieldsAndPreservesDisableState() {
        LlamaCppHttpReasoningProvider provider = new LlamaCppHttpReasoningProvider();
        ReasoningProviderConfiguration empty = new ReasoningProviderConfiguration(Map.of());
        ReasoningProviderConfiguration configured = empty.applying(provider.configurator().configure(
                "local", Map.of(
                        "capability-id", "local-http",
                        "endpoint", "http://127.0.0.1:8080/",
                        "model", "local-model",
                        "privacy", "SECRET",
                        "model-slot-units", "1"), empty));

        assertEquals(1, provider.materialize(configured).size());
        assertEquals("1", provider.configurator().configuredInstances(configured).getFirst()
                .values().get("model-slot-units"));

        ReasoningProviderConfiguration disabled = configured.applying(
                provider.configurator().setEnabled("local", false, configured));
        assertEquals(0, provider.materialize(disabled).size());
        assertTrue(disabled.value("reasoning.llamacpp-http.local.model").isPresent());
    }

    @Test void unixSummaryExposesOnlyFieldsDeclaredByUnixProvider() {
        LlamaCppUnixSocketReasoningProvider provider = new LlamaCppUnixSocketReasoningProvider();
        ReasoningProviderConfiguration configuration = new ReasoningProviderConfiguration(Map.of(
                "reasoning.llamacpp-unix.instances", "local",
                "reasoning.llamacpp-unix.local.id", "unix-text",
                "reasoning.llamacpp-unix.local.socket", "/tmp/llama.sock",
                "reasoning.llamacpp-unix.local.model", "local-model",
                "reasoning.llamacpp-unix.local.computation", "madre.text-embedding.v1",
                "reasoning.llamacpp-unix.local.embedding-space-id", "foreign-space",
                "reasoning.llamacpp-unix.local.embedding-dimensions", "3",
                "reasoning.llamacpp-unix.local.privacy", "SECRET",
                "reasoning.llamacpp-unix.local.expected-latency-ms", "10",
                "reasoning.llamacpp-unix.local.preference", "0"));

        Map<String, String> values = provider.configurator().configuredInstances(configuration)
                .getFirst().values();
        assertFalse(values.containsKey("computation"));
        assertFalse(values.containsKey("embedding-space-id"));
        assertFalse(values.containsKey("embedding-dimensions"));
        assertEquals("/tmp/llama.sock", values.get("socket"));
    }

    @Test void loopbackValidationRemainsProviderOwned() {
        LlamaCppHttpReasoningProvider provider = new LlamaCppHttpReasoningProvider();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> provider.configurator().configure("bad", Map.of(
                        "capability-id", "bad-http",
                        "endpoint", "https://example.com/",
                        "model", "model",
                        "privacy", "LOCAL"), new ReasoningProviderConfiguration(Map.of())));
        assertTrue(failure.getMessage().contains("loopback"));
    }
}
