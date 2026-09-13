package io.github.didacll.madre.algebra;

/** Machine-executed extent of one bounded Operation variant. */
public enum Autonomy {
    A1(1), A2(2), A3(3), A4(4), A5(5);

    private final int rank;
    Autonomy(int rank) { this.rank = rank; }
    public int rank() { return rank; }
}
