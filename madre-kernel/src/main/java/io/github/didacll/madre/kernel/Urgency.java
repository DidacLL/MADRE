package io.github.didacll.madre.kernel;

/** Queue ordering, independent of the inference family's capability requirements. */
public enum Urgency {
    BACKGROUND(0), NORMAL(1), FAST_LANE(2);
    private final int priority;
    Urgency(int priority) { this.priority = priority; }
    int priority() { return priority; }
}
