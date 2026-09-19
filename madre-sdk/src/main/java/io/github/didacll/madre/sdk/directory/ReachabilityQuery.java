package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import java.util.Objects;

/**
 * Exact information a caller offers to discover exposed Module Operations.
 * The caller identity is deliberately absent: the runtime binds it into the supplied
 * {@link ModuleDirectory} when the installed Module is materialized.
 */
public record ReachabilityQuery(MaterialTypeId materialType) {
    public ReachabilityQuery {
        Objects.requireNonNull(materialType, "materialType");
    }
}
