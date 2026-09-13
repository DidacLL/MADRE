package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Confidentiality boundary of a receiver. */
public enum Privacy {
    PUBLIC(1), UNKNOWN(2), P3(3), P4(4), P5(5);

    private final int rank;

    Privacy(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    public Privacy combine(Privacy other) {
        Objects.requireNonNull(other, "other");
        return rank <= other.rank ? this : other;
    }
}
