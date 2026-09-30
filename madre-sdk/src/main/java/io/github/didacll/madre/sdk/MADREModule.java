package io.github.didacll.madre.sdk;

import java.util.Collection;
import java.util.List;

/** Independently installed application/domain boundary. Its meaning and state remain its own. */
public interface MADREModule {
    /** Stable identity within one installation. */
    String id();

    default Collection<MADREAgent> agents() { return List.of(); }

    default Collection<ModuleOperation> operations() { return List.of(); }

    default Collection<Skill> skills() { return List.of(); }
}
