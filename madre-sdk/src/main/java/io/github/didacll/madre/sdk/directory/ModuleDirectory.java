package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.algebra.Sensitivity;
import java.util.List;
import java.util.Objects;

/** Read-only live directory of currently reachable Module-exposed behavior. */
public interface ModuleDirectory {
    /** Exact reachability for one already known nominal Material type. */
    List<ReachableModule> reachable(ReachabilityQuery query);

    /**
     * Returns the currently Module-exposed Operations whose declared input receiver can accept
     * Material at the supplied carried Sensitivity. This is structural caller-bound discovery only:
     * it does not rank semantic relevance and does not imply external/public disclosure.
     *
     * <p>The default empty result preserves compatibility for directory implementations that only
     * support exact nominal-type reachability.</p>
     */
    default List<ReachableModule> exposed(Sensitivity sensitivity) {
        Objects.requireNonNull(sensitivity, "sensitivity");
        return List.of();
    }
}
