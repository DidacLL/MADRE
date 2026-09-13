package io.github.didacll.madre.sdk.execution;

import java.util.concurrent.CompletionStage;

/** Module-facing port for physical execution. */
public interface ExecutionService {
    <C, R> CompletionStage<R> execute(WorkRequest<C, R> request);
}
