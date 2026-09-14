package io.github.didacll.madre.algebra;

/** Machine-executed extent of one bounded Operation variant. */
public enum Autonomy {
    SYSTEM_RESERVED(0),
    LIVE_INTERACTION(1),
    ASK_ALWAYS(2),
    ASK_ONCE(3),
    ACKNOWLEDGE(4),
    AUTONOMOUS(5);

    private final int rank;
    Autonomy(int rank) { this.rank = rank; }
    public int rank() { return rank; }
}
