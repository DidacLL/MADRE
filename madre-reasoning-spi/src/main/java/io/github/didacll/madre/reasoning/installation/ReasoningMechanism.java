package io.github.didacll.madre.reasoning.installation;

import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.util.Objects;

/** One provider-materialized reasoning mechanism plus ordinary installation preference. */
public record ReasoningMechanism<R, C extends ReasoningComputation<R>>(
        ReasoningCapability<R, C> capability, int preference) {
    public ReasoningMechanism {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(capability.manifest(), "capability.manifest()");
        Objects.requireNonNull(capability.availability(), "capability.availability()");
        if (preference < 0) {
            throw new IllegalArgumentException("preference must not be negative");
        }
    }
}
