package io.github.didacll.madre.kernel.client;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkInspection(
        String workId,
        WorkState state,
        OffsetDateTime createdAt,
        OffsetDateTime eligibleAt,
        OffsetDateTime deadline,
        WorkUrgency urgency,
        ExecutionBoundary executionBoundary,
        List<String> eligibleCapabilityIds,
        String selectedCapabilityId,
        String selectedBindingId,
        String selectedBindingVersion,
        boolean cancelRequested,
        boolean released,
        PhysicalFailure failure,
        List<AttemptInspection> attempts) {
}
