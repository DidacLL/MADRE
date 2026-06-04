package org.madre.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class MADREAction {
    private final UUID actionId;
    private final String name;
    private final String purpose;
    private final ActionKind actionKind;
    private final UUID ownerAgentId;
    private final BoundaryProfile boundary;
    private final Function<AgentRequest, Artifact> executor;

    public MADREAction(
            UUID actionId,
            String name,
            String purpose,
            ActionKind actionKind,
            UUID ownerAgentId,
            BoundaryProfile boundary,
            Function<AgentRequest, Artifact> executor
    ) {
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.name = Objects.requireNonNull(name, "name");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.actionKind = Objects.requireNonNull(actionKind, "actionKind");
        this.ownerAgentId = Objects.requireNonNull(ownerAgentId, "ownerAgentId");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static MADREAction deterministic(UUID ownerAgentId, String name, Function<AgentRequest, Artifact> executor) {
        return new MADREAction(UUID.randomUUID(), name, "deterministic runtime action", ActionKind.DETERMINISTIC, ownerAgentId, BoundaryProfile.publicProfile(), executor);
    }

    public static MADREAction deterministic(UUID ownerAgentId, String name, BoundaryProfile boundary, Function<AgentRequest, Artifact> executor) {
        return new MADREAction(UUID.randomUUID(), name, "deterministic runtime action", ActionKind.DETERMINISTIC, ownerAgentId, boundary, executor);
    }

    public UUID id() {
        return actionId;
    }

    public String name() {
        return name;
    }

    public String purpose() {
        return purpose;
    }

    public ActionKind actionKind() {
        return actionKind;
    }

    public UUID ownerAgentId() {
        return ownerAgentId;
    }

    public BoundaryProfile boundary() {
        return boundary;
    }

    public Artifact run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.isExecutable()) {
            throw new IllegalArgumentException("agent request is not executable");
        }
        if (!ownerAgentId.equals(request.targetAgentId())) {
            throw new IllegalArgumentException("request target agent does not own action");
        }
        if (request.targetActionRef().isPresent() && !request.targetActionRef().orElseThrow().equals(actionId)) {
            throw new IllegalArgumentException("request target action does not match this action");
        }
        if (!request.boundary().compatibleWith(boundary)) {
            throw new IllegalStateException("request boundary is not compatible with action boundary");
        }
        if (request.policyDecisionRef().isPresent() && !request.policyDecisionRef().orElseThrow().allowsExecution()) {
            return Artifact.failure("blocked by policy: " + request.policyDecisionRef().orElseThrow().reason(), request.boundary());
        }
        return executor.apply(request);
    }
}
