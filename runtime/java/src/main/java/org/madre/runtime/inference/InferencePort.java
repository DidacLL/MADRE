package org.madre.runtime.inference;

import org.madre.runtime.AgentRequest;
import org.madre.runtime.Artifact;

public interface InferencePort {
    Artifact infer(AgentRequest request);
}
