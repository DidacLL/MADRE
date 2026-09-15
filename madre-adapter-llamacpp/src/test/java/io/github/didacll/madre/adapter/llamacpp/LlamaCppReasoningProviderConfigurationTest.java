package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
