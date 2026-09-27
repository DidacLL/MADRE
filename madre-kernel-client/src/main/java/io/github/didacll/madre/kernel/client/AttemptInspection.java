package io.github.didacll.madre.kernel.client;

import java.time.OffsetDateTime;

public record AttemptInspection(
        int attemptNumber,
        String capabilityId,
        String bindingId,
        String bindingVersion,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Long latencyMs,
        PhysicalAttemptOutcome outcome,
        PhysicalFailure failure) {
}
