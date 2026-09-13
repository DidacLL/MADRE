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

/** Module-owned intelligent actor declaration with an explicit repertoire. */
public record AgentDefinition(AgentId id, String purpose, Integrity integrity, Set<SkillId> skills,
        Set<WorkflowId> workflows, Set<OperationId> operations) {
    public AgentDefinition {
        Objects.requireNonNull(id, "id");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose must not be blank");
        purpose = purpose.strip();
        Objects.requireNonNull(integrity, "integrity");
        skills = Set.copyOf(skills);
        workflows = Set.copyOf(workflows);
        operations = Set.copyOf(operations);
        if (operations.isEmpty()) throw new IllegalArgumentException("an Agent must expose at least one Operation");
    }

    public Privacy effectivePrivacy(Map<OperationId, OperationDefinition<?, ?>> definitions) {
        return operations.stream()
                .map(id -> Objects.requireNonNull(definitions.get(id), "unresolved Operation " + id))
                .flatMap(operation -> operation.acceptedMaterial().values().stream())
                .reduce(Privacy.P5, Privacy::combine);
    }

    public java.util.Optional<Sensitivity> effectiveSensitivity(
            Map<OperationId, OperationDefinition<?, ?>> definitions) {
        return operations.stream()
                .map(id -> Objects.requireNonNull(definitions.get(id), "unresolved Operation " + id))
                .flatMap(operation -> operation.producedMaterial().values().stream())
                .reduce(Sensitivity::combine);
    }
}
