package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.algebra.Sensitivity;
import java.util.List;
import java.util.Objects;

/** Read-only live directory of exact currently reachable Module behavior. */
public interface ModuleDirectory {
    /** Exact discovery when the caller already knows the nominal Material type it can offer. */
    List<ReachableModule> reachable(ReachabilityQuery query);

    /**
     * Structural discovery for earlier-compiled callers that do not know a target Module's nominal
     * Material types. Results remain caller-bound and contain only exposed Operations whose
     * accepted receiver can carry the supplied Sensitivity. No semantic ranking or invocation
     * authority is added by discovery.
     *
     * <p>The default keeps lightweight SDK test doubles source-compatible; installed MADRE runtime
     * directories override it with the live structural view.</p>
     */
    default List<ReachableOperation> reachableOperations(Sensitivity sensitivity) {
        Objects.requireNonNull(sensitivity, "sensitivity");
        return List.of();
    }
}
