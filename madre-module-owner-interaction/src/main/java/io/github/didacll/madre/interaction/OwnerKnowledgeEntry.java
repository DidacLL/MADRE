package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import java.util.Objects;
import java.util.UUID;

record OwnerKnowledgeEntry(MaterialId id, OwnerKnowledgeKind kind, String key, String value,
        Sensitivity sensitivity) {
    OwnerKnowledgeEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        key = Objects.requireNonNull(key, "key").strip();
        value = Objects.requireNonNull(value, "value").strip();
        Objects.requireNonNull(sensitivity, "sensitivity");
        if (!id.moduleId().equals(OwnerInteractionModule.ID)) {
            throw new IllegalArgumentException("owner knowledge must be owned by CORE Module");
        }
        if (key.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("owner knowledge key/value must not be blank");
        }
        if (sensitivity.rank() < kind.minimumSensitivity().rank()) {
            throw new IllegalArgumentException("owner knowledge Sensitivity is below its semantic minimum");
        }
    }

    Material<String> sourceMaterial(MaterialType<String> type) {
        return new Material<>(id, type, key + ": " + value, sensitivity);
    }

    Material<String> opaqueReference(MaterialType<String> type) {
        return new Material<>(
                new MaterialId(OwnerInteractionModule.ID, "knowledge-reference-" + UUID.randomUUID()),
                type,
                key + ": a highly sensitive owner value is stored inside CORE; raw contents are intentionally omitted",
                Sensitivity.S2);
    }
}
