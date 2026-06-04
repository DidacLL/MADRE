package org.madre.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ReasoningPlan {
    private final UUID planId;
    private final UUID sourceRequestId;
    private final UUID owningModuleId;
    private final BoundaryProfile boundary;
    private final List<ReasoningTask> tasks = new ArrayList<>();
    private final List<UUID> workflowIds = new ArrayList<>();
    private final List<UUID> resultIds = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;
    private Status status;

    public ReasoningPlan(UUID planId, UUID sourceRequestId, UUID owningModuleId, BoundaryProfile boundary) {
        this.planId = Objects.requireNonNull(planId, "planId");
        this.sourceRequestId = Objects.requireNonNull(sourceRequestId, "sourceRequestId");
        this.owningModuleId = Objects.requireNonNull(owningModuleId, "owningModuleId");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = Status.NEW;
    }

    public UUID id() {
        return planId;
    }

    public UUID sourceRequestId() {
        return sourceRequestId;
    }

    public UUID owningModuleId() {
        return owningModuleId;
    }

    public BoundaryProfile boundary() {
        return boundary;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<ReasoningTask> tasks() {
        return List.copyOf(tasks);
    }

    public List<UUID> workflowIds() {
        return List.copyOf(workflowIds);
    }

    public List<UUID> resultIds() {
        return List.copyOf(resultIds);
    }

    public RuntimeEvent addTask(ReasoningTask task) {
        if (!planId.equals(task.owningPlanId())) {
            throw new IllegalArgumentException("task belongs to a different plan");
        }
        tasks.add(task);
        touch();
        return RuntimeEvent.now("plan.task.added", task.id(), planId, "task added to plan", null);
    }

    public void addWorkflow(Workflow workflow) {
        workflowIds.add(workflow.id());
        touch();
    }

    public List<ReasoningTask> readyTasks() {
        return tasks.stream()
                .filter(task -> task.status() == Status.READY || task.status() == Status.NEW)
                .filter(task -> task.dependsOn().stream().allMatch(this::taskCompleted))
                .toList();
    }

    public RuntimeEvent markScheduled() {
        if (status == Status.SCHEDULED || status == Status.COMPLETED || status == Status.FAILED || status == Status.SUPERSEDED) {
            throw new IllegalStateException("plan cannot be scheduled from status " + status);
        }
        if (tasks.isEmpty()) {
            throw new IllegalStateException("plan must contain at least one task before scheduling");
        }
        status = Status.SCHEDULED;
        touch();
        return RuntimeEvent.now("plan.scheduled", id(), id(), "plan accepted for scheduling", null);
    }

    public RuntimeEvent recordTaskResult(UUID taskId, UUID artifactId) {
        ReasoningTask task = task(taskId).orElseThrow();
        task.markResult(artifactId);
        resultIds.add(artifactId);
        touch();
        return RuntimeEvent.now("plan.task.result", taskId, planId, "task result recorded", artifactId);
    }

    public RuntimeEvent recordTaskFailure(UUID taskId, UUID failureId) {
        ReasoningTask task = task(taskId).orElseThrow();
        task.markFailure(failureId);
        touch();
        return RuntimeEvent.now("plan.task.failure", taskId, planId, "task failure recorded", failureId);
    }

    public RuntimeEvent linkDelegatedRequest(UUID taskId, UUID requestId) {
        ReasoningTask task = task(taskId).orElseThrow();
        task.linkDelegatedRequest(requestId);
        touch();
        return RuntimeEvent.now("plan.task.delegated", taskId, planId, "delegated request linked", requestId);
    }

    public RuntimeEvent complete(UUID resultId) {
        boolean blockingIncomplete = tasks.stream().anyMatch(task -> task.blocking() && task.status() != Status.COMPLETED);
        if (blockingIncomplete) {
            throw new IllegalStateException("blocking tasks are incomplete");
        }
        resultIds.add(resultId);
        status = Status.COMPLETED;
        touch();
        return RuntimeEvent.now("plan.completed", this.id(), this.id(), "plan completed", resultId);
    }

    private Optional<ReasoningTask> task(UUID taskId) {
        return tasks.stream().filter(task -> task.id().equals(taskId)).findFirst();
    }

    private boolean taskCompleted(UUID taskId) {
        return task(taskId).map(task -> task.status() == Status.COMPLETED).orElse(false);
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
