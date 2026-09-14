package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.material.Material;
import java.util.Objects;
import java.util.Optional;

/** Terminal interpretation of one durable owner-interaction reasoning request. */
public record BackgroundUpdate(WorkId workId, WorkState reasoningState,
        Optional<Material<String>> backgroundAnalysis,
        Optional<Material<String>> visibleFollowUp,
        Optional<ReasoningFailureCategory> reasoningFailure) {
    public BackgroundUpdate {
        Objects.requireNonNull(workId, "workId");
        Objects.requireNonNull(reasoningState, "reasoningState");
        backgroundAnalysis = Objects.requireNonNull(backgroundAnalysis, "backgroundAnalysis");
        visibleFollowUp = Objects.requireNonNull(visibleFollowUp, "visibleFollowUp");
        reasoningFailure = Objects.requireNonNull(reasoningFailure, "reasoningFailure");
        if (reasoningState != WorkState.SUCCEEDED && reasoningState != WorkState.FAILED
                && reasoningState != WorkState.CANCELLED) {
            throw new IllegalArgumentException("a background update must be terminal");
        }
    }
}
