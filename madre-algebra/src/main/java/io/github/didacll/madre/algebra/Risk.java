package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Consequence of one bounded Operation variant. */
public enum Risk {
    SYSTEM_RESERVED(0),
    READ(1),
    WRITE(2),
    DELETE(3),
    EXECUTE(4),
    POTENTIALLY_HARMFUL(5);

    private final int rank;
    Risk(int rank) { this.rank = rank; }
    public int rank() { return rank; }

    /** Returns whether an actual physical realizer can carry this bounded Risk. */
    public boolean isSupportedBy(Integrity physicalRealizer) {
        return rank <= Objects.requireNonNull(physicalRealizer, "physicalRealizer").rank();
    }
}
