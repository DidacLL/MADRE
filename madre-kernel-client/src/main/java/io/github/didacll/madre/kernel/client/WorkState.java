package io.github.didacll.madre.kernel.client;

public enum WorkState {
    Queued,
    Running,
    Checkpointed,
    Succeeded,
    Failed,
    Cancelled,
    UnknownCompletion
}
