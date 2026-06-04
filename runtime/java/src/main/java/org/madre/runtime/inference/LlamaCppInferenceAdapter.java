package org.madre.runtime.inference;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import org.madre.runtime.AgentRequest;
import org.madre.runtime.Artifact;

import java.util.Objects;

public final class LlamaCppInferenceAdapter implements InferencePort {
    private final String modelPath;

    public LlamaCppInferenceAdapter(String modelPath) {
        this.modelPath = Objects.requireNonNull(modelPath, "modelPath");
        if (modelPath.isBlank()) {
            throw new IllegalArgumentException("modelPath must not be blank");
        }
    }

    @Override
    public Artifact infer(AgentRequest request) {
        ModelParameters modelParameters = new ModelParameters().setModel(modelPath);
        InferenceParameters inferenceParameters = new InferenceParameters(request.objective());
        try (LlamaModel model = new LlamaModel(modelParameters)) {
            return Artifact.generated(model.complete(inferenceParameters), request.boundary());
        }
    }
}
