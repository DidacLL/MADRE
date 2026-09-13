package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Consequence of one bounded Operation variant. */
public enum Risk {
    R1(1), R2(2), R3(3), R4(4), R5(5);

    private final int rank;
    Risk(int rank) { this.rank = rank; }
    public int rank() { return rank; }

    /** Returns whether an actual physical realizer can carry this bounded Risk. */
    public boolean isSupportedBy(Integrity physicalRealizer) {
        return rank <= Objects.requireNonNull(physicalRealizer, "physicalRealizer").rank();
    }
}
