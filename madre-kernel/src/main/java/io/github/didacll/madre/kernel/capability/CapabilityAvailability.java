package io.github.didacll.madre.kernel.capability;

/** Observed physical reachability of a Capability. UNKNOWN is never selectable. */
public enum CapabilityAvailability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE
}
