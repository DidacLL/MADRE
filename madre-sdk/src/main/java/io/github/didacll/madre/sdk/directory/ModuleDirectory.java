package io.github.didacll.madre.sdk.directory;

import java.util.List;

/** Read-only live directory of exact currently reachable Module behavior. */
public interface ModuleDirectory {
    /** Exact discovery when the caller already knows the nominal Material type it can offer. */
    List<ReachableModule> reachable(ReachabilityQuery query);

    /**
     * Factual discovery for callers that do not know a target Module's nominal Material types.
     * Results remain caller-bound and contain exposed Operations only. No semantic compatibility,
     * ranking or invocation authority is added by discovery.
     *
     * <p>The default keeps lightweight SDK test doubles source-compatible; installed MADRE runtime
     * directories override it with the live structural view.</p>
     */
    default List<ReachableOperation> reachableOperations() {
        return List.of();
    }
}
