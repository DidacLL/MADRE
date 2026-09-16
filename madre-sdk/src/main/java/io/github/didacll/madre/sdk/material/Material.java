package io.github.didacll.madre.sdk.material;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import java.util.Objects;

/** An immutable, typed semantic value owned by exactly one Module. */
public record Material<T>(MaterialId id, MaterialType<T> type, T payload, Sensitivity sensitivity) {
    public Material {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(sensitivity, "sensitivity");
        if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED Sensitivity is not ordinary Material");
        }
        if (!id.moduleId().equals(type.id().moduleId())) {
            throw new IllegalArgumentException("Material identity and type must have the same Module owner");
        }
        if (!type.javaType().isInstance(payload)) {
            throw new IllegalArgumentException("payload does not match Material type");
        }
    }
}
