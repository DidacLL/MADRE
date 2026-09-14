package io.github.didacll.madre.sdk.execution;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Reasoning work derived from a bounded Module Operation call.
 *
 * <p>The computation is nominally restricted to {@link ReasoningComputation}; this
 * request is not a generic Kernel command/action envelope. Operation Risk is not
 * propagated because a reasoning mechanism does not realize the Module Operation's
 * external effect.</p>
 */
public final class ReasoningRequest<R, C extends ReasoningComputation<R>> {
    private final ModuleId originatingModule;
    private final C computation;
    private final Sensitivity carriedSensitivity;
    private final ExecutionMode mode;
    private final int priority;
    private final Instant eligibleAt;
    private final Duration timeout;
    private final ReasoningRetryPolicy retryPolicy;
    private final Optional<CancellationKey> cancellationKey;
    private final ReasoningPreferences preferences;

    private ReasoningRequest(OperationCall<?, ?> call, C computation, ExecutionMode mode,
            int priority, Instant eligibleAt, Duration timeout,
            ReasoningRetryPolicy retryPolicy, Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        OperationCall<?, ?> boundedCall = Objects.requireNonNull(call, "call");
        originatingModule = boundedCall.operation().id().moduleId();
        this.computation = Objects.requireNonNull(computation, "computation");
        Objects.requireNonNull(computation.resultType(), "computation.resultType()");
        carriedSensitivity = boundedCall.input().sensitivity();
        this.mode = Objects.requireNonNull(mode, "mode");
        if (priority < 0) throw new IllegalArgumentException("priority must not be negative");
        this.priority = priority;
        this.eligibleAt = Objects.requireNonNull(eligibleAt, "eligibleAt");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.cancellationKey = Objects.requireNonNull(cancellationKey, "cancellationKey");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> immediate(
            OperationCall<?, ?> call, C computation, int priority, Duration timeout,
            ReasoningRetryPolicy retryPolicy, Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return new ReasoningRequest<>(call, computation, ExecutionMode.IMMEDIATE, priority,
                Instant.now(), timeout, retryPolicy, cancellationKey, preferences);
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> durable(
            OperationCall<?, ?> call, C computation, int priority, Instant eligibleAt,
            Duration timeout, ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey, ReasoningPreferences preferences) {
        return new ReasoningRequest<>(call, computation, ExecutionMode.DURABLE, priority,
                eligibleAt, timeout, retryPolicy, cancellationKey, preferences);
    }

    public ModuleId originatingModule() { return originatingModule; }
    public C computation() { return computation; }
    public Class<R> resultType() { return computation.resultType(); }
    public Sensitivity carriedSensitivity() { return carriedSensitivity; }
    public ExecutionMode mode() { return mode; }
    public int priority() { return priority; }
    public Instant eligibleAt() { return eligibleAt; }
    public Duration timeout() { return timeout; }
    public ReasoningRetryPolicy retryPolicy() { return retryPolicy; }
    public Optional<CancellationKey> cancellationKey() { return cancellationKey; }
    public ReasoningPreferences preferences() { return preferences; }
}
