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

    /** Receive installation mechanics after discovery. Module state stays in the Module. */
    default void start(ModuleEnvironment environment) throws Exception { }

    /** Release resources owned by this Module when Runtime stops or unloads it. */
    void close();
}
