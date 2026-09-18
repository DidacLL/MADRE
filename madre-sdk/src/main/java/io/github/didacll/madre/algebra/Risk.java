package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Consequence of one bounded Operation variant. */
public enum Risk {
    SYSTEM_RESERVED(0), READ(1), WRITE(2), DELETE(3), EXECUTE(4), POTENTIALLY_HARMFUL(5);
    private final int rank;
    Risk(int rank) { this.rank = rank; }
    public int rank() { return rank; }
    public boolean isSupportedBy(Integrity integrity) {
        return rank <= Objects.requireNonNull(integrity, "integrity").rank();
    }
}
