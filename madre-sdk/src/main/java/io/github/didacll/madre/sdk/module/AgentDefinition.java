package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Module-owned intelligent actor with learned Skills, owned Workflows and Operations. */
public record AgentDefinition(AgentId id, String purpose, Integrity integrity, Set<SkillId> skills,
        Map<WorkflowId, WorkflowDefinition> workflows, Set<OperationId> operations) {
    public AgentDefinition {
        Objects.requireNonNull(id, "id");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        purpose = purpose.strip();
        Objects.requireNonNull(integrity, "integrity");
        skills = Set.copyOf(skills);
        workflows = Map.copyOf(workflows);
        operations = Set.copyOf(operations);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("an Agent must expose at least one Operation");
        }
        if (!workflows.entrySet().stream().allMatch(entry ->
                entry.getKey().equals(entry.getValue().id())
                        && entry.getKey().agentId().equals(id))) {
            throw new IllegalArgumentException("Workflow declarations must be owned by this Agent");
        }
        if (workflows.values().stream()
                .flatMap(workflow -> workflow.operations().stream())
                .anyMatch(operation -> !operations.contains(operation))) {
            throw new IllegalArgumentException("Workflow Operations must belong to the Agent repertoire");
        }
    }

    public Privacy effectivePrivacy(Map<OperationId, OperationDefinition<?, ?>> definitions) {
        return operations.stream()
                .map(operationId -> Objects.requireNonNull(definitions.get(operationId),
                        "unresolved Operation " + operationId))
                .flatMap(operation -> operation.acceptedMaterial().values().stream())
                .reduce(Privacy.P5, Privacy::combine);
    }

    public java.util.Optional<Sensitivity> effectiveSensitivity(
            Map<OperationId, OperationDefinition<?, ?>> definitions) {
        return operations.stream()
                .map(operationId -> Objects.requireNonNull(definitions.get(operationId),
                        "unresolved Operation " + operationId))
                .flatMap(operation -> operation.producedMaterial().values().stream())
                .reduce(Sensitivity::combine);
    }
}
