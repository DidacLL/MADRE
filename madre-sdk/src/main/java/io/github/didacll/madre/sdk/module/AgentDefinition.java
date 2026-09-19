package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Portable semantic description of one Module-owned intelligent actor. */
public record AgentDefinition(AgentId id, String purpose, Integrity integrity, Set<SkillId> skills,
        Map<WorkflowId, WorkflowDefinition> workflows, Set<OperationId> operations) {
    public AgentDefinition {
        Objects.requireNonNull(id, "id");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        purpose = purpose.strip();
        Objects.requireNonNull(integrity, "integrity");
        if (integrity == Integrity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED Integrity is not an ordinary Agent value");
        }
        Set<SkillId> copiedSkills = Set.copyOf(skills);
        Map<WorkflowId, WorkflowDefinition> copiedWorkflows = Map.copyOf(workflows);
        Set<OperationId> copiedOperations = Set.copyOf(operations);
        if (copiedOperations.isEmpty()) {
            throw new IllegalArgumentException("an Agent must expose at least one Operation");
        }
        if (!copiedWorkflows.entrySet().stream().allMatch(entry ->
                entry.getKey().equals(entry.getValue().id())
                        && entry.getKey().agentId().equals(id))) {
            throw new IllegalArgumentException("Workflow declarations must be owned by this Agent");
        }
        skills = copiedSkills;
        workflows = copiedWorkflows;
        operations = copiedOperations;
    }

}
