package io.github.didacll.madre.kernel.client;

import java.time.OffsetDateTime;

public record CapabilityState(
        String capabilityId,
        CapabilityAvailability availability,
        OffsetDateTime observedAt) {
}
