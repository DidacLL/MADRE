package io.github.didacll.madre.kernel;

/** Standard provider-neutral physical inference type identities. */
public final class InferenceTypes {
    public static final InferenceType<TextInferenceInput, TextInferenceOutput> TEXT_GENERATION =
            new InferenceType<>("text-generation/v1", TextInferenceInput.class, TextInferenceOutput.class);

    private InferenceTypes() { }
}
