package io.github.didacll.madre.adapter.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.embedding.TextEmbeddingCodecs;
import io.github.didacll.madre.generation.TextGenerationCodecs;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleHeterogeneousConfigurationTest {
    @Test void providerMaterializesConfiguredComputationFamiliesWithoutHostKnowledge() {
        OpenAiCompatibleReasoningProvider provider = new OpenAiCompatibleReasoningProvider();
        ReasoningProviderConfiguration initial = new ReasoningProviderConfiguration(Map.of());
        ReasoningProviderConfiguration embedding = initial.applying(provider.configure("embedding", Map.of(
                "capability-id", "remote-embedding",
                "endpoint", "http://127.0.0.1:8081/v1/",
                "model", "embed-model",
                "computation", TextEmbeddingCodecs.CONTRACT_ID,
                "embedding-space-id", "embed-model/space-v1",
                "embedding-dimensions", "3",
                "privacy", "UNKNOWN",
                "location", "REMOTE"), initial));
        ReasoningProviderConfiguration configured = embedding.applying(provider.configure("generation", Map.of(
                "capability-id", "remote-generation",
                "endpoint", "http://127.0.0.1:8082/v1/",
                "model", "chat-model",
                "computation", TextGenerationCodecs.CONTRACT_ID,
                "privacy", "UNKNOWN",
                "location", "REMOTE"), embedding));

        var mechanisms = provider.materialize(configured);
        assertEquals(2, mechanisms.size());
        assertInstanceOf(OpenAiCompatibleEmbeddingCapability.class,
                mechanisms.get(0).capability());
        assertInstanceOf(OpenAiCompatibleTextGenerationCapability.class,
                mechanisms.get(1).capability());
    }

    @Test void embeddingConfigurationRequiresExplicitSpace() {
        OpenAiCompatibleReasoningProvider provider = new OpenAiCompatibleReasoningProvider();
        ReasoningProviderConfiguration empty = new ReasoningProviderConfiguration(Map.of());
        assertThrows(IllegalArgumentException.class, () -> provider.configure("embedding", Map.of(
                "capability-id", "remote-embedding",
                "endpoint", "http://127.0.0.1:8081/v1/",
                "model", "embed-model",
                "computation", TextEmbeddingCodecs.CONTRACT_ID,
                "privacy", "UNKNOWN",
                "location", "REMOTE"), empty));
    }
}
