package io.github.didacll.madre.kernel;

/** Concrete physical inference contracts implemented by installed engines. */
public final class InferenceTypes {
    public static final InferenceType<ChatCompletionInput, ChatCompletionOutput> CHAT_COMPLETION =
            new InferenceType<>("chat-completion/v1", ChatCompletionInput.class, ChatCompletionOutput.class);

    private InferenceTypes() { }
}
