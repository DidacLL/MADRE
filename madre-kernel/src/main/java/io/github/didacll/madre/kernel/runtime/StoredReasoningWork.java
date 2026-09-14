package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.CancellationKey;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Persisted scheduler state for one reasoning computation. */
record StoredReasoningWork(WorkId id, ModuleId module, String contractId, byte[] computation,
        Sensitivity sensitivity, int priority, Instant eligibleAt, Duration timeout,
        int maximumAttempts, Duration retryDelay, Optional<CancellationKey> cancellationKey,
        Optional<ReasoningLocation> location, Optional<Duration> maximumLatency, WorkState state,
        int attempts, Optional<ReasoningFailureCategory> failureCategory,
        Optional<byte[]> result, Optional<Instant> completedAt) {
    StoredReasoningWork {
        Objects.requireNonNull(id);
        Objects.requireNonNull(module);
        Objects.requireNonNull(contractId);
        computation = computation == null ? null : computation.clone();
        Objects.requireNonNull(sensitivity);
        Objects.requireNonNull(eligibleAt);
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(retryDelay);
        Objects.requireNonNull(cancellationKey);
        Objects.requireNonNull(location);
        Objects.requireNonNull(maximumLatency);
        Objects.requireNonNull(state);
        Objects.requireNonNull(failureCategory);
        Objects.requireNonNull(result);
        Objects.requireNonNull(completedAt);
        result = result.map(byte[]::clone);
    }

    @Override public byte[] computation() {
        return computation == null ? null : computation.clone();
    }

    @Override public Optional<byte[]> result() { return result.map(byte[]::clone); }
}
