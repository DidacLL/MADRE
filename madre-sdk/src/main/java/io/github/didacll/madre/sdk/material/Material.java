package io.github.didacll.madre.sdk.material;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import java.util.Objects;

/**
 * An immutable, typed semantic representation carrying its applicable Sensitivity.
 *
 * <p>The current identifier and nominal type support the working authoring and persistence paths.
 * Their shape does not establish universal Module ownership or a final Material lifecycle.</p>
 */
public record Material<T>(MaterialId id, MaterialType<T> type, T payload, Sensitivity sensitivity) {
    public Material {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(sensitivity, "sensitivity");
        if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED Sensitivity is not ordinary Material");
        }
        if (!type.javaType().isInstance(payload)) {
            throw new IllegalArgumentException("payload does not match Material type");
        }
    }
}
