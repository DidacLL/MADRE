package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Confidentiality consequence of exposing information. */
public enum Sensitivity {
    SYSTEM_RESERVED(0), S1(1), S2(2), S3(3), S4(4), S5(5);
    private final int rank;
    Sensitivity(int rank) { this.rank = rank; }
    public int rank() { return rank; }
    public Sensitivity combine(Sensitivity other) {
        return rank >= Objects.requireNonNull(other, "other").rank ? this : other;
    }
    public boolean canReach(Privacy receiver) {
        return rank <= Objects.requireNonNull(receiver, "receiver").rank();
    }
}
