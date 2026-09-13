package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.CancellationKey;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

record StoredWork(WorkId id, ModuleId module, String contractId, byte[] command,
        Sensitivity sensitivity, Optional<Risk> risk, int priority, Instant eligibleAt,
        Duration timeout, int maximumAttempts, Duration retryDelay,
        Optional<CancellationKey> cancellationKey, Optional<PhysicalLocation> location,
        Optional<Duration> maximumLatency, WorkState state, int attempts,
        Optional<PhysicalFailureCategory> failureCategory, Optional<byte[]> result, Optional<Instant> completedAt) {
    StoredWork {
        Objects.requireNonNull(id); Objects.requireNonNull(module); Objects.requireNonNull(contractId);
        command = command == null ? null : command.clone(); Objects.requireNonNull(sensitivity); Objects.requireNonNull(risk);
        Objects.requireNonNull(eligibleAt); Objects.requireNonNull(timeout); Objects.requireNonNull(retryDelay);
        Objects.requireNonNull(cancellationKey); Objects.requireNonNull(location); Objects.requireNonNull(maximumLatency);
        Objects.requireNonNull(state); Objects.requireNonNull(failureCategory); Objects.requireNonNull(result); Objects.requireNonNull(completedAt);
        result = result.map(byte[]::clone);
    }
    @Override public byte[] command() { return command == null ? null : command.clone(); }
    @Override public Optional<byte[]> result() { return result.map(byte[]::clone); }
}
