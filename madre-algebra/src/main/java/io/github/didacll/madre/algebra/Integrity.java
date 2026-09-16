package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Bounded causal or physical-realization responsibility. */
public enum Integrity {
    SYSTEM_RESERVED(0), I1(1), I2(2), I3(3), I4(4), I5(5);

    private final int rank;

    Integrity(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    public Integrity combine(Integrity other) {
        Objects.requireNonNull(other, "other");
        return rank <= other.rank ? this : other;
    }
}
