package org.madre.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Workflow(
        UUID workflowId,
        String name,
        String purpose,
        BoundaryProfile boundary,
        List<String> taskTemplates,
        Instant createdAt
) {
    public Workflow {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(boundary, "boundary");
        taskTemplates = List.copyOf(Objects.requireNonNull(taskTemplates, "taskTemplates"));
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Workflow singleTask(String name, BoundaryProfile boundary) {
        return new Workflow(UUID.randomUUID(), name, "single task blueprint", boundary, List.of("execute first compatible action"), Instant.now());
    }

    public UUID id() {
        return workflowId;
    }

    public boolean applicableTo(ReasoningRequest request) {
        Objects.requireNonNull(request, "request");
        return boundary.compatibleWith(request.boundary()) && !request.objective().isBlank();
    }
}
