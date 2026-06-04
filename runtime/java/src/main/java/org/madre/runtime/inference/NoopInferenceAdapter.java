package org.madre.runtime.inference;

import org.madre.runtime.AgentRequest;
import org.madre.runtime.Artifact;

public final class NoopInferenceAdapter implements InferencePort {
    @Override
    public Artifact infer(AgentRequest request) {
        return Artifact.generated("noop inference: " + request.objective(), request.boundary());
    }
}
