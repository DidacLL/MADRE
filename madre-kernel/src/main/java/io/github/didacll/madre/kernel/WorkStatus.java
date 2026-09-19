package io.github.didacll.madre.kernel;

public enum WorkStatus {
    QUEUED, NEEDS_INPUT, RUNNING, RETRY_WAIT, DELIVERING, DELIVERED,
    FAILED, CANCELLED, OUTCOME_UNKNOWN;

    public boolean terminal() {
        return this == DELIVERED || this == FAILED || this == CANCELLED || this == OUTCOME_UNKNOWN;
    }
}
