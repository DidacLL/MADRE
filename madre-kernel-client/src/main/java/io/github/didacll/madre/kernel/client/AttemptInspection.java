package io.github.didacll.madre.kernel.client;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record AttemptInspection(
        int attemptNumber,
        InvocationId invocationId,
        InvocationKind invocationKind,
        Optional<String> targetIdentity,
        AttemptStatus state,
        long startedAtMs,
        OptionalLong endedAtMs,
        OptionalInt processExitCode,
        OptionalInt httpStatus,
        Optional<String> technicalFailure) {
    public AttemptInspection {
        if (attemptNumber < 1) throw new IllegalArgumentException("attemptNumber must be >= 1");
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(invocationKind, "invocationKind");
        Objects.requireNonNull(targetIdentity, "targetIdentity");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(endedAtMs, "endedAtMs");
        Objects.requireNonNull(processExitCode, "processExitCode");
        Objects.requireNonNull(httpStatus, "httpStatus");
        Objects.requireNonNull(technicalFailure, "technicalFailure");
        if (startedAtMs < 0) throw new IllegalArgumentException("startedAtMs must be >= 0");
    }
}
