package io.github.didacll.madre.core;

import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.material.Material;
import java.util.Objects;
import java.util.Optional;

/** Terminal interpretation of one durable background request by the CORE Module. */
public record BackgroundUpdate(WorkId workId, WorkState physicalState,
        Optional<Material<String>> backgroundAnalysis,
        Optional<Material<String>> visibleFollowUp,
        Optional<PhysicalFailureCategory> physicalFailure) {
    public BackgroundUpdate {
        Objects.requireNonNull(workId, "workId");
        Objects.requireNonNull(physicalState, "physicalState");
        backgroundAnalysis = Objects.requireNonNull(backgroundAnalysis, "backgroundAnalysis");
        visibleFollowUp = Objects.requireNonNull(visibleFollowUp, "visibleFollowUp");
        physicalFailure = Objects.requireNonNull(physicalFailure, "physicalFailure");
        if (physicalState != WorkState.SUCCEEDED && physicalState != WorkState.FAILED
                && physicalState != WorkState.CANCELLED) {
            throw new IllegalArgumentException("a background update must be terminal");
        }
    }
}
