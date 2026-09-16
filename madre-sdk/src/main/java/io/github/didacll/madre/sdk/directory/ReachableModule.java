package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.Map;
import java.util.Objects;

/** Module-exposed behavior reachable to one caller through ordinary Module composition. */
public record ReachableModule(ModuleId id, String version, String purpose,
        Map<MaterialTypeId, MaterialTypeDefinition> materialTypes,
        Map<AgentId, ReachableAgent> agents, Map<OperationId, OperationDefinition> operations) {
    public ReachableModule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(purpose, "purpose");
        materialTypes = Map.copyOf(materialTypes);
        agents = Map.copyOf(agents);
        operations = Map.copyOf(operations);
    }

    /** Compatibility constructor for exact-directory implementations without portable type data. */
    public ReachableModule(ModuleId id, String version, String purpose,
            Map<AgentId, ReachableAgent> agents, Map<OperationId, OperationDefinition> operations) {
        this(id, version, purpose, Map.of(), agents, operations);
    }
}
