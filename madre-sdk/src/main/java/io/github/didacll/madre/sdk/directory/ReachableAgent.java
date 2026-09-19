package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.OperationId;
import java.util.Objects;
import java.util.Set;

/** Exact currently reachable exposed-Operation portion of an Agent description. */
public record ReachableAgent(AgentId id, String purpose, Set<OperationId> operations) {
    public ReachableAgent { Objects.requireNonNull(id, "id"); Objects.requireNonNull(purpose, "purpose"); operations = Set.copyOf(operations); }
}
