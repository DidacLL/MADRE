package io.github.didacll.madre.sdk.execution;

/** Stable physical failure categories exposed to Module behavior and retry diagnostics. */
public enum PhysicalFailureCategory {
    UNAVAILABLE, TIMEOUT, CANCELLED, CONNECTION, PROTOCOL, REMOTE_FAILURE, INTERNAL, INTERRUPTED
}
