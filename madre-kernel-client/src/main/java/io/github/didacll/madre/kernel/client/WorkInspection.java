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
        String strategyType,
        String strategyVersion,
        String selectedCapabilityId,
        String selectedBindingId,
        String selectedBindingVersion,
        String checkpointSessionId,
        String checkpointId,
        boolean cancelRequested,
        boolean released,
        String failureCode,
        List<AttemptInspection> attempts) {
}
