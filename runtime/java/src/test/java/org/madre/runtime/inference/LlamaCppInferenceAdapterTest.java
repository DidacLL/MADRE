package org.madre.runtime.inference;

import org.junit.jupiter.api.Test;
import org.madre.runtime.AgentRequest;
import org.madre.runtime.Artifact;
import org.madre.runtime.BoundaryProfile;
import org.madre.runtime.PolicyDecision;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LlamaCppInferenceAdapterTest {
    @Test
    void noopInferenceAdapterIsDeterministicForDefaultTests() {
        BoundaryProfile boundary = BoundaryProfile.internalProfile();
        AgentRequest request = request(boundary);

        Artifact artifact = new NoopInferenceAdapter().infer(request);

        assertEquals("noop inference: say hello", artifact.contentRef());
    }

    @Test
    void llamaCppAdapterSmokeTestRunsOnlyWhenModelPathIsConfigured() {
        String modelPath = System.getenv("MADRE_LLAMA_MODEL");
        assumeTrue(modelPath != null && !modelPath.isBlank(), "MADRE_LLAMA_MODEL is not set");

        BoundaryProfile boundary = BoundaryProfile.internalProfile();
        Artifact artifact = new LlamaCppInferenceAdapter(modelPath).infer(request(boundary));

        assertFalse(artifact.contentRef().isBlank());
    }

    private static AgentRequest request(BoundaryProfile boundary) {
        UUID actionId = UUID.randomUUID();
        return new AgentRequest(
                UUID.randomUUID(),
                "say hello",
                UUID.randomUUID(),
                UUID.randomUUID(),
                actionId,
                null,
                Map.of(),
                boundary,
                PolicyDecision.allow(actionId, boundary, "test"),
                Instant.now()
        );
    }
}
