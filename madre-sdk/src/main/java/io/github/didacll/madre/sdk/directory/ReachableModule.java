package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.Map;
import java.util.Objects;

/** Module-exposed behavior reachable for one exact directory query. */
public record ReachableModule(ModuleId id, String version, String purpose,
        Map<AgentId, ReachableAgent> agents, Map<OperationId, OperationDefinition> operations) {
    public ReachableModule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(purpose, "purpose");
        agents = Map.copyOf(agents);
        operations = Map.copyOf(operations);
    }
}
