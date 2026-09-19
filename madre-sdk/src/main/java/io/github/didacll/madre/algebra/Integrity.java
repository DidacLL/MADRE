package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Confidence in one non-user causal participant. */
public enum Integrity {
    SYSTEM_RESERVED(0), I1(1), I2(2), I3(3), I4(4), I5(5);
    private final int rank;
    Integrity(int rank) { this.rank = rank; }
    public int rank() { return rank; }
    public Integrity combine(Integrity other) {
        return rank <= Objects.requireNonNull(other, "other").rank ? this : other;
    }
}
