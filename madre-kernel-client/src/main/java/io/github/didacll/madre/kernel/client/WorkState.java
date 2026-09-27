package io.github.didacll.madre.kernel.client;

public enum WorkState {
    Queued,
    Running,
    Succeeded,
    Failed,
    Cancelled,
    UnknownCompletion
}
