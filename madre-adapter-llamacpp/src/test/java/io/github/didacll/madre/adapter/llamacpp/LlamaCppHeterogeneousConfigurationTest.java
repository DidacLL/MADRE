package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.embedding.TextEmbeddingCodecs;
import io.github.didacll.madre.generation.TextGenerationCodecs;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LlamaCppHeterogeneousConfigurationTest {
    @Test void httpProviderMaterializesConfiguredComputationFamilies() {
        LlamaCppHttpReasoningProvider provider = new LlamaCppHttpReasoningProvider();
        ReasoningProviderConfiguration initial = new ReasoningProviderConfiguration(Map.of());
        ReasoningProviderConfiguration embedding = initial.applying(provider.configure("embedding", Map.of(
                "capability-id", "local-embedding",
                "endpoint", "http://127.0.0.1:8081/",
                "model", "embed-model",
                "computation", TextEmbeddingCodecs.CONTRACT_ID,
                "embedding-space-id", "embed-model/space-v1",
                "embedding-dimensions", "2",
                "privacy", "LOCAL"), initial));
        ReasoningProviderConfiguration configured = embedding.applying(provider.configure("generation", Map.of(
                "capability-id", "local-generation",
                "endpoint", "http://127.0.0.1:8082/",
                "model", "chat-model",
                "computation", TextGenerationCodecs.CONTRACT_ID,
                "privacy", "LOCAL"), embedding));

        var mechanisms = provider.materialize(configured);
        assertEquals(2, mechanisms.size());
        assertInstanceOf(LlamaCppEmbeddingCapability.class, mechanisms.get(0).capability());
        assertInstanceOf(LlamaCppTextGenerationCapability.class, mechanisms.get(1).capability());
    }

    @Test void embeddingConfigurationRequiresExplicitDimensions() {
        LlamaCppHttpReasoningProvider provider = new LlamaCppHttpReasoningProvider();
        ReasoningProviderConfiguration empty = new ReasoningProviderConfiguration(Map.of());
        assertThrows(IllegalArgumentException.class, () -> provider.configure("embedding", Map.of(
                "capability-id", "local-embedding",
                "endpoint", "http://127.0.0.1:8081/",
                "model", "embed-model",
                "computation", TextEmbeddingCodecs.CONTRACT_ID,
                "embedding-space-id", "embed-model/space-v1",
                "privacy", "LOCAL"), empty));
    }
}
