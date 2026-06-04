package org.madre.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MADREAgent {
    private final UUID agentId;
    private final String name;
    private final String purpose;
    private final AgentProfile profile;
    private final BoundaryProfile boundary;
    private final List<MADREAction> actions = new ArrayList<>();

    public MADREAgent(UUID agentId, String name, String purpose, AgentProfile profile, BoundaryProfile boundary) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.name = Objects.requireNonNull(name, "name");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
    }

    public static MADREAgent simple(String name, BoundaryProfile boundary) {
        return new MADREAgent(UUID.randomUUID(), name, "simple module-owned runtime actor", AgentProfile.SIMPLE, boundary);
    }

    public UUID id() {
        return agentId;
    }

    public String name() {
        return name;
    }

    public String purpose() {
        return purpose;
    }

    public AgentProfile profile() {
        return profile;
    }

    public BoundaryProfile boundary() {
        return boundary;
    }

    public void addAction(MADREAction action) {
        if (!agentId.equals(action.ownerAgentId())) {
            throw new IllegalArgumentException("action owner does not match agent");
        }
        actions.add(action);
    }

    public List<MADREAction> actions() {
        return List.copyOf(actions);
    }

    public Artifact execute(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        if (!agentId.equals(request.targetAgentId())) {
            throw new IllegalArgumentException("request target agent does not match agent");
        }
        if (!request.boundary().compatibleWith(effectiveBoundary())) {
            throw new IllegalStateException("request boundary is not compatible with agent boundary");
        }
        MADREAction selected = request.targetActionRef()
                .flatMap(actionId -> actions.stream().filter(action -> action.id().equals(actionId)).findFirst())
                .orElseGet(() -> actions.stream().findFirst().orElseThrow(() -> new IllegalStateException("agent has no actions")));
        return selected.run(request);
    }

    public BoundaryProfile effectiveBoundary() {
        BoundaryProfile effective = boundary;
        for (MADREAction action : actions) {
            effective = effective.join(action.boundary());
        }
        return effective;
    }
}
