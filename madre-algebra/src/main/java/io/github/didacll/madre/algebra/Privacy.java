package io.github.didacll.madre.algebra;

import java.util.Objects;

/** Confidentiality boundary of a receiver. */
public enum Privacy {
    /** Reserved for system-level algebraic use, never an ordinary declared boundary. */
    SYSTEM_RESERVED(0),
    /** Public information may be exposed to any receiver. */
    PUBLIC(1),
    /** Non-public handling outside the owner's control or otherwise not more specifically known. */
    UNKNOWN(2),
    /** Information is confined to the owner's local MADRE environment. */
    LOCAL(3),
    /** Information is confined to the owning Module boundary. */
    MODULE(4),
    /** Strongest ordinary confidentiality boundary. */
    SECRET(5);

    private final int rank;

    Privacy(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    public Privacy combine(Privacy other) {
        Objects.requireNonNull(other, "other");
        return rank <= other.rank ? this : other;
    }
}
