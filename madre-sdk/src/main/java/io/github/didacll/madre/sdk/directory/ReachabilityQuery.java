package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import java.util.Objects;

/**
 * Exact information a Module offers to discover reachable Module-exposed behavior for one nominal
 * Material type. Module exposure is an installed-Module composition fact and is distinct from an
 * Operation's explicit external/public disclosure behavior.
 *
 * <p>The caller identity is deliberately absent: the runtime binds it into the supplied
 * {@link ModuleDirectory} when the installed Module is materialized.</p>
 */
public record ReachabilityQuery(MaterialTypeId materialType, Sensitivity sensitivity) {
    public ReachabilityQuery {
        Objects.requireNonNull(materialType, "materialType");
        Objects.requireNonNull(sensitivity, "sensitivity");
    }
}
