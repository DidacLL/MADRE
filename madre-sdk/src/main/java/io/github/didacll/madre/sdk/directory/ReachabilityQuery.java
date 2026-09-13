package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.util.Objects;

/** Exact current information offered by a caller to reachable public behavior. */
public record ReachabilityQuery(ModuleId caller, MaterialTypeId materialType, Sensitivity sensitivity) {
    public ReachabilityQuery { Objects.requireNonNull(caller, "caller"); Objects.requireNonNull(materialType, "materialType"); Objects.requireNonNull(sensitivity, "sensitivity"); }
}
