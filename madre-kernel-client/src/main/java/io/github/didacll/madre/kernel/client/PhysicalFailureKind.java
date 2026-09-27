package io.github.didacll.madre.kernel.client;

public enum PhysicalFailureKind {
    NoAdmissibleCapability,
    DeadlineExpired,
    Cancelled,
    LaunchFailed,
    ProcessExited,
    PayloadLimitExceeded,
    IoFailure,
    InvalidBindingResult,
    CompletionUnknown
}
