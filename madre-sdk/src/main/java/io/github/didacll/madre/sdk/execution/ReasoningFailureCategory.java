package io.github.didacll.madre.sdk.execution;

/** Stable failure categories exposed for reasoning execution and retry diagnostics. */
public enum ReasoningFailureCategory {
    UNAVAILABLE, TIMEOUT, CANCELLED, CONNECTION, PROTOCOL, REMOTE_FAILURE, INTERNAL, INTERRUPTED
}
