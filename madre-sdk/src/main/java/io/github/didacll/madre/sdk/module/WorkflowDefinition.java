package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import java.util.Objects;
import java.util.Set;

/** Reusable Module-owned semantic behavior declaration, not a Kernel execution language. */
public record WorkflowDefinition(WorkflowId id, String purpose, Set<SkillId> skills, Set<OperationId> operations) {
    public WorkflowDefinition {
        Objects.requireNonNull(id, "id");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose must not be blank");
        purpose = purpose.strip();
        skills = Set.copyOf(skills);
        operations = Set.copyOf(operations);
    }
}
