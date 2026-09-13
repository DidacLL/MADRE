package io.github.didacll.madre.sdk.execution;

/** Physical runtime state; it has no Module-semantic meaning. */
public enum WorkState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
