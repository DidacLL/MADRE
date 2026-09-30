package io.github.didacll.madre.sdk;

import java.util.Collection;
import java.util.List;

/** Independently installed Module; application meaning and behavior remain inside it. */
public interface MadreModule {
    /** Stable identity within one installation. */
    String id();

    default Collection<Agent> agents() { return List.of(); }

    default Collection<Operation> operations() { return List.of(); }
}
