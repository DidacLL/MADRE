package org.madre.runtime;

public enum BoundaryValue {
    PUBLIC(0),
    INTERNAL(1),
    RESTRICTED(2),
    PRIVATE(3);

    private final int rank;

    BoundaryValue(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static BoundaryValue mostRestrictive(BoundaryValue left, BoundaryValue right) {
        return left.rank >= right.rank ? left : right;
    }
}
