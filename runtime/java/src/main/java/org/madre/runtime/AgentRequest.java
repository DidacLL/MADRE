package org.madre.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AgentRequest(
        UUID agentRequestId,
        String objective,
        UUID callerId,
        UUID targetAgentId,
        UUID targetActionId,
        UUID contextBundleId,
        Map<String, String> parameters,
        BoundaryProfile boundary,
        PolicyDecision policyDecision,
        Instant createdAt
) {
    public AgentRequest {
        Objects.requireNonNull(agentRequestId, "agentRequestId");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(callerId, "callerId");
        Objects.requireNonNull(targetAgentId, "targetAgentId");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() {
        return agentRequestId;
    }

    public Optional<UUID> targetActionRef() {
        return Optional.ofNullable(targetActionId);
    }

    public Optional<UUID> contextBundleRef() {
        return Optional.ofNullable(contextBundleId);
    }

    public Optional<PolicyDecision> policyDecisionRef() {
        return Optional.ofNullable(policyDecision);
    }

    public boolean isExecutable() {
        return !objective.isBlank() && callerId != null && targetAgentId != null && boundary != null && createdAt != null;
    }
}
