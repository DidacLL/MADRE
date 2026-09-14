package io.github.didacll.madre.sdk.execution;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Module-facing Kernel port limited to reasoning computations. */
public interface ReasoningService {
    /** Executes immediate reasoning and completes with the mechanism result. */
    <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
            ReasoningRequest<R, C> request);

    /** Persists durable reasoning and returns after the request is safely queued. */
    <R, C extends ReasoningComputation<R>> WorkId submit(ReasoningRequest<R, C> request);

    Optional<WorkStatus> inspect(WorkId id);

    boolean cancel(WorkId id);

    /** Collects a completed durable reasoning result. */
    <R> Optional<R> collect(WorkId id, Class<R> resultType);

    /** Acknowledges delivery and removes retained reasoning payloads. */
    boolean acknowledge(WorkId id);
}
