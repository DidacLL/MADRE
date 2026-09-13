package io.github.didacll.madre.sdk.execution;

import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Physical work derived from a valid Module Operation call, with no semantic identity. */
public final class WorkRequest<C, R> {
    private final ModuleId originatingModule;
    private final C command;
    private final Class<R> resultType;
    private final Sensitivity carriedSensitivity;
    private final Optional<Risk> effectRisk;
    private final ExecutionMode mode;
    private final int priority;
    private final Instant eligibleAt;
    private final Duration timeout;
    private final PhysicalRetryPolicy retryPolicy;
    private final Optional<CancellationKey> cancellationKey;
    private final PhysicalPreferences preferences;

    private WorkRequest(OperationCall<?, ?> call, C command, Class<R> resultType,
            ExecutionMode mode, int priority, Instant eligibleAt, Duration timeout,
            PhysicalRetryPolicy retryPolicy, Optional<CancellationKey> cancellationKey,
            PhysicalPreferences preferences) {
        OperationCall<?, ?> boundedCall = Objects.requireNonNull(call, "call");
        originatingModule = boundedCall.operation().id().moduleId();
        this.command = Objects.requireNonNull(command, "command");
        this.resultType = Objects.requireNonNull(resultType, "resultType");
        carriedSensitivity = boundedCall.input().sensitivity();
        effectRisk = boundedCall.effectProfile().map(profile -> profile.risk());
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

    /** Constructs foreground physical work from the exact Operation input and profile. */
    public static <C, R> WorkRequest<C, R> immediate(OperationCall<?, ?> call,
            C command, Class<R> resultType, int priority, Duration timeout,
            PhysicalRetryPolicy retryPolicy, Optional<CancellationKey> cancellationKey,
            PhysicalPreferences preferences) {
        return new WorkRequest<>(call, command, resultType, ExecutionMode.IMMEDIATE,
                priority, Instant.now(), timeout, retryPolicy, cancellationKey, preferences);
    }

    /** Constructs durable physical work from the exact Operation input and profile. */
    public static <C, R> WorkRequest<C, R> durable(OperationCall<?, ?> call,
            C command, Class<R> resultType, int priority, Instant eligibleAt,
            Duration timeout, PhysicalRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey, PhysicalPreferences preferences) {
        return new WorkRequest<>(call, command, resultType, ExecutionMode.DURABLE,
                priority, eligibleAt, timeout, retryPolicy, cancellationKey, preferences);
    }

    public ModuleId originatingModule() { return originatingModule; }
    public C command() { return command; }
    public Class<R> resultType() { return resultType; }
    public Sensitivity carriedSensitivity() { return carriedSensitivity; }
    public Optional<Risk> effectRisk() { return effectRisk; }
    public ExecutionMode mode() { return mode; }
    public int priority() { return priority; }
    public Instant eligibleAt() { return eligibleAt; }
    public Duration timeout() { return timeout; }
    public PhysicalRetryPolicy retryPolicy() { return retryPolicy; }
    public Optional<CancellationKey> cancellationKey() { return cancellationKey; }
    public PhysicalPreferences preferences() { return preferences; }
}
