package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * One executable bounded unit of Module logic that participates in MADRE arbitration.
 *
 * <p>The body may implement any Module-owned behavior: pure computation, file/database/network
 * access, process/script execution, reasoning, or a composition of ordinary Java facilities.
 * MADRE does not prescribe the implementation technique. The surrounding {@link OperationCall}
 * and declared contract provide the typed/trust/security boundary for the bounded invocation.</p>
 */
public abstract class Operation<I, O> {
    /**
     * Creates an Operation from one functional body while preserving the normal
     * {@link #invoke(OperationCall)} validation path.
     */
    public static <I, O> Operation<I, O> of(
            Function<OperationCall<I, O>, CompletionStage<Material<O>>> body) {
        Function<OperationCall<I, O>, CompletionStage<Material<O>>> executable =
                Objects.requireNonNull(body, "body");
        return new Operation<>() {
            @Override
            protected CompletionStage<Material<O>> execute(OperationCall<I, O> call) {
                return executable.apply(call);
            }
        };
    }

    /**
     * Performs Module-owned behavior after the bounded call has satisfied its declared MADRE
     * arbitration. This hook is deliberately not a separate public invocation path.
     */
    protected abstract CompletionStage<Material<O>> execute(OperationCall<I, O> call);

    /** Invokes the behavior and keeps its Material result inside the declared contract. */
    public final CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
        OperationCall<I, O> boundedCall = Objects.requireNonNull(call, "call");
        return Objects.requireNonNull(execute(boundedCall), "Operation result stage")
                .thenApply(boundedCall::acceptOutput);
    }
}
