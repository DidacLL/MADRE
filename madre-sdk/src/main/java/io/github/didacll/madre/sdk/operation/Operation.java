package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Executable behavior for one declared bounded Operation. */
public abstract class Operation<I, O> {
    /**
     * Performs Module-owned behavior after the input and applicable causal values
     * compose. This hook is deliberately not a public invocation path.
     */
    protected abstract CompletionStage<Material<O>> execute(OperationCall<I, O> call);

    /** Invokes the behavior and keeps its Material result inside the declared contract. */
    public final CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
        OperationCall<I, O> boundedCall = Objects.requireNonNull(call, "call");
        return Objects.requireNonNull(execute(boundedCall), "Operation result stage")
                .thenApply(boundedCall::acceptOutput);
    }
}
