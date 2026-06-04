package org.madre.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ReasoningTask {
    private final UUID taskId;
    private final UUID owningPlanId;
    private final UUID responsibleModuleId;
    private final UUID agentId;
    private final UUID agentRequestId;
    private final String objective;
    private final String expectedResult;
    private final List<UUID> dependsOn;
    private final boolean blocking;
    private Status status;
    private UUID resultId;
    private UUID failureId;
    private UUID delegatedRequestId;

    public ReasoningTask(
            UUID taskId,
            UUID owningPlanId,
            UUID responsibleModuleId,
            UUID agentId,
            UUID agentRequestId,
            String objective,
            String expectedResult,
            List<UUID> dependsOn,
            boolean blocking,
            Status status
    ) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.owningPlanId = Objects.requireNonNull(owningPlanId, "owningPlanId");
        this.responsibleModuleId = Objects.requireNonNull(responsibleModuleId, "responsibleModuleId");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.agentRequestId = Objects.requireNonNull(agentRequestId, "agentRequestId");
        this.objective = Objects.requireNonNull(objective, "objective");
        this.expectedResult = Objects.requireNonNull(expectedResult, "expectedResult");
        this.dependsOn = List.copyOf(Objects.requireNonNull(dependsOn, "dependsOn"));
        this.blocking = blocking;
        this.status = Objects.requireNonNull(status, "status");
    }

    public UUID id() {
        return taskId;
    }

    public UUID owningPlanId() {
        return owningPlanId;
    }

    public UUID responsibleModuleId() {
        return responsibleModuleId;
    }

    public UUID agentId() {
        return agentId;
    }

    public UUID agentRequestId() {
        return agentRequestId;
    }

    public String objective() {
        return objective;
    }

    public String expectedResult() {
        return expectedResult;
    }

    public List<UUID> dependsOn() {
        return dependsOn;
    }

    public boolean blocking() {
        return blocking;
    }

    public Status status() {
        return status;
    }

    void markResult(UUID artifactId) {
        this.resultId = artifactId;
        this.status = Status.COMPLETED;
    }

    void markFailure(UUID artifactId) {
        this.failureId = artifactId;
        this.status = Status.FAILED;
    }

    void linkDelegatedRequest(UUID requestId) {
        this.delegatedRequestId = requestId;
        this.status = Status.BLOCKED;
    }

    public Optional<UUID> resultId() {
        return Optional.ofNullable(resultId);
    }

    public Optional<UUID> failureId() {
        return Optional.ofNullable(failureId);
    }

    public Optional<UUID> delegatedRequestId() {
        return Optional.ofNullable(delegatedRequestId);
    }
}
