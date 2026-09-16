package io.github.didacll.madre.sdk.directory;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.Map;
import java.util.Objects;

/**
 * One exact exposed Operation structurally reachable from a caller-bound Module directory.
 *
 * <p>The portable Material definitions are limited to nominal contracts owned by the target
 * Module and referenced by this Operation. They describe the target contract; they grant no
 * additional invocation authority and do not imply public disclosure.</p>
 */
public record ReachableOperation(ModuleId moduleId, String modulePurpose,
        OperationDefinition operation,
        Map<MaterialTypeId, MaterialTypeDefinition> materialTypes) {
    public ReachableOperation {
        Objects.requireNonNull(moduleId, "moduleId");
        modulePurpose = Objects.requireNonNull(modulePurpose, "modulePurpose").strip();
        if (modulePurpose.isEmpty()) throw new IllegalArgumentException("modulePurpose must not be blank");
        Objects.requireNonNull(operation, "operation");
        if (!operation.id().moduleId().equals(moduleId)) {
            throw new IllegalArgumentException("Operation is not owned by the reachable Module");
        }
        materialTypes = Map.copyOf(materialTypes);
        materialTypes.forEach((id, definition) -> {
            Objects.requireNonNull(id, "material type id");
            Objects.requireNonNull(definition, "material type definition");
            if (!id.equals(definition.id()) || !id.moduleId().equals(moduleId)) {
                throw new IllegalArgumentException(
                        "reachable Material definition is not owned by the target Module: " + id);
            }
            if (!operation.acceptedMaterial().containsKey(id)
                    && !operation.producedMaterial().containsKey(id)) {
                throw new IllegalArgumentException(
                        "reachable Material definition is unrelated to the Operation: " + id);
            }
        });
    }
}
