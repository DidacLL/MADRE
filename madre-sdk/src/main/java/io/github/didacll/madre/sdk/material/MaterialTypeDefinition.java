package io.github.didacll.madre.sdk.material;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import java.util.Objects;

/** Language-neutral semantic description of one nominal Material content type. */
public record MaterialTypeDefinition(MaterialTypeId id, String contentType) {
    public MaterialTypeDefinition {
        Objects.requireNonNull(id, "id");
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        contentType = contentType.strip();
    }
}
