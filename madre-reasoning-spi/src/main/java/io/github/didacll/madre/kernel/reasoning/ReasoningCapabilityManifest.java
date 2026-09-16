package io.github.didacll.madre.kernel.reasoning;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Installed facts used to select one reasoning mechanism. */
public record ReasoningCapabilityManifest<R, C extends ReasoningComputation<R>>(
        ReasoningCapabilityId id, ReasoningContract<R, C> contract,
        Privacy receivingPrivacy, ReasoningLocation location, Duration expectedLatency,
        List<ResourceClaim> resources) {
    public ReasoningCapabilityManifest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(receivingPrivacy, "receivingPrivacy");
        if (receivingPrivacy == Privacy.SYSTEM_RESERVED) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Privacy is not an installed reasoning fact");
        }
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isNegative() || expectedLatency.isZero()) {
            throw new IllegalArgumentException("expectedLatency must be positive");
        }
        resources = List.copyOf(resources);
    }
}
