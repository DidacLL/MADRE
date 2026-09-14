package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Confidentiality boundary of a receiver. */
public enum Privacy {
    SYSTEM_RESERVED(0), PUBLIC(1), UNKNOWN(2), LOCAL(3), MODULE(4), SECRET(5);

    private final int rank;

    Privacy(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    public Privacy combine(Privacy other) {
        Objects.requireNonNull(other, "other");
        return rank <= other.rank ? this : other;
    }
}
