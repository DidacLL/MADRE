package io.github.didacll.madre.sdk;

import java.util.Collection;
import java.util.List;

/**
 * Independently installed application/domain boundary. A Module owns its domain
 * meaning, state, persistence, types, UI, integrations and internal behavior.
 * Agents and Skills are optional; an agentless Module may expose Operations.
 * CORE is an installation role for an ordinary Module, not a subtype here.
 */
public interface MADREModule {
    /** Stable identity within one installation. */
    String id();

    default Collection<MADREAgent> agents() { return List.of(); }

    default Collection<ModuleOperation> operations() { return List.of(); }

    default Collection<Skill> skills() { return List.of(); }
}
