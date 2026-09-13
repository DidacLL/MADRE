package io.github.didacll.madre.sdk.execution;

import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Physical work constructed by Module behavior, with no semantic or Capability identity. */
public record WorkRequest<C, R>(ModuleId originatingModule, C command, Class<R> resultType,
        Sensitivity carriedSensitivity, Optional<Risk> physicalRisk, ExecutionMode mode,
        int priority, Instant eligibleAt, Duration timeout, PhysicalRetryPolicy retryPolicy,
        Optional<CancellationKey> cancellationKey) {
    public WorkRequest {
        Objects.requireNonNull(originatingModule, "originatingModule");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(carriedSensitivity, "carriedSensitivity");
        physicalRisk = Objects.requireNonNull(physicalRisk, "physicalRisk");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(eligibleAt, "eligibleAt");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        cancellationKey = Objects.requireNonNull(cancellationKey, "cancellationKey");
        if (priority < 0) throw new IllegalArgumentException("priority must not be negative");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
    }
}
