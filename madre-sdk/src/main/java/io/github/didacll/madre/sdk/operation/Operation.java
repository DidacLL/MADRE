package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.AgentContext;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

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
     * actor-bound invocation and output-validation path.
     */
    public static <I, O> Operation<I, O> of(
            BiFunction<AgentContext, OperationCall<I, O>, CompletionStage<Material<O>>> body) {
        BiFunction<AgentContext, OperationCall<I, O>, CompletionStage<Material<O>>> executable =
                Objects.requireNonNull(body, "body");
        return new Operation<>() {
            @Override
            protected CompletionStage<Material<O>> execute(AgentContext context,
                    OperationCall<I, O> call) {
                return executable.apply(context, call);
            }
        };
    }

    /**
     * Performs Module-owned behavior after the bounded call has satisfied its declared MADRE
     * arbitration. This hook is deliberately not a separate public invocation path.
     */
    protected abstract CompletionStage<Material<O>> execute(AgentContext context,
            OperationCall<I, O> call);

    /** Invokes the behavior and keeps its Material result inside the declared contract. */
    public final CompletionStage<Material<O>> invoke(AgentContext context,
            OperationCall<I, O> call) {
        AgentContext execution = Objects.requireNonNull(context, "context");
        OperationCall<I, O> boundedCall = Objects.requireNonNull(call, "call");
        return Objects.requireNonNull(execute(execution, boundedCall), "Operation result stage")
                .thenApply(boundedCall::acceptOutput);
    }
}
