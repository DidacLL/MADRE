package io.github.didacll.madre.sdk.execution;

import java.util.concurrent.CompletionStage;
import java.util.Optional;

/** Module-facing port for physical execution. */
public interface ExecutionService {
    /** Executes immediate work and completes with its physical result. */
    <C, R> CompletionStage<R> execute(WorkRequest<C, R> request);

    /** Persists durable work and returns after the request is safely queued. */
    <C, R> WorkId submit(WorkRequest<C, R> request);

    Optional<WorkStatus> inspect(WorkId id);

    boolean cancel(WorkId id);

    /** Collects a completed durable result. Collection is non-destructive until acknowledged. */
    <R> Optional<R> collect(WorkId id, Class<R> resultType);

    /** Acknowledges delivery and removes retained physical payloads. */
    boolean acknowledge(WorkId id);
}
