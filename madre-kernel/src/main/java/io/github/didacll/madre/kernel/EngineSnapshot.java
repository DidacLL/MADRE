package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Current factual inspection view of an installed executable inference engine. */
public record EngineSnapshot(
        EngineId id,
        String inferenceType,
        EngineCharacteristics characteristics,
        EngineAvailability availability) {
    public EngineSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inferenceType, "inferenceType");
        Objects.requireNonNull(characteristics, "characteristics");
        Objects.requireNonNull(availability, "availability");
    }
}
