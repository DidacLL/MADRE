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

    /** Handle a durable wakeup. Persist its semantic effect before returning.
     * Runtime can replay the same ID after interruption; handling must be idempotent.
     */
    default void onWakeup(String wakeupId, String reference) throws Exception {
        throw new UnsupportedOperationException("Module does not handle scheduled wakeups");
    }

    /** Release resources owned by this Module when Runtime stops or unloads it. */
    void close();
}
