package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.OperationId;
import java.util.Objects;
import java.util.Set;

/** Exact currently reachable public portion of an Agent. */
public record ReachableAgent(AgentId id, String purpose, Set<OperationId> operations, Privacy effectivePrivacy) {
    public ReachableAgent { Objects.requireNonNull(id, "id"); Objects.requireNonNull(purpose, "purpose"); operations = Set.copyOf(operations); Objects.requireNonNull(effectivePrivacy, "effectivePrivacy"); }
}
