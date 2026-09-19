package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import java.util.List;
import java.util.Objects;

/** Agent-owned semantic behavior represented minimally as an ordered Operation sequence. */
public record WorkflowDefinition(WorkflowId id, String purpose, List<OperationId> operations) {
    public WorkflowDefinition {
        Objects.requireNonNull(id, "id");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        purpose = purpose.strip();
        operations = List.copyOf(operations);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("a Workflow must contain at least one Operation");
        }
    }
}
