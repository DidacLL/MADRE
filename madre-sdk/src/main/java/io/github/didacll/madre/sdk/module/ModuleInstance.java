package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.MaterialType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Validated runtime assembly of portable Module contracts and Java execution bindings. */
public final class ModuleInstance {
    private final ModuleDefinition definition;
    private final Map<MaterialTypeId, MaterialType<?>> materialTypes;
    private final Map<OperationId, OperationBinding<?, ?>> operations;
    private final Optional<OwnerInteractionAgent> ownerInteractionAgent;

    /**
     * Adapter-oriented assembly path for an already available portable definition and Java-facing
     * payload/behavior bindings. The assembly is valid by construction. Portable adapters do not
     * imply an executable owner-interaction Agent.
     */
    public ModuleInstance(ModuleDefinition definition,
            Map<MaterialTypeId, MaterialType<?>> materialTypes,
            Map<OperationId, OperationBinding<?, ?>> operations) {
        this(definition, materialTypes, operations, Optional.empty());
    }

    private ModuleInstance(ModuleDefinition definition,
            Map<MaterialTypeId, MaterialType<?>> materialTypes,
            Map<OperationId, OperationBinding<?, ?>> operations,
            Optional<OwnerInteractionAgent> ownerInteractionAgent) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.materialTypes = Map.copyOf(materialTypes);
        this.operations = Map.copyOf(operations);
        this.ownerInteractionAgent = Objects.requireNonNull(ownerInteractionAgent,
                "ownerInteractionAgent");
        validateBindings();
    }

    /** Derives portable descriptions and Java binding maps from one Java Module object. */
    public static ModuleInstance from(Module module) {
        Module executable = Objects.requireNonNull(module, "module");
        Map<MaterialTypeId, MaterialType<?>> types = new LinkedHashMap<>();
        for (MaterialType<?> materialType : executable.materialTypes()) {
            MaterialType<?> value = Objects.requireNonNull(materialType, "materialType");
            if (types.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("duplicate MaterialType identity: " + value.id());
            }
        }
        Map<OperationId, OperationBinding<?, ?>> bindings = new LinkedHashMap<>();
        for (OperationBinding<?, ?> binding : executable.operations()) {
            OperationBinding<?, ?> value = Objects.requireNonNull(binding, "operation");
            OperationId id = value.definition().id();
            if (bindings.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate Operation identity: " + id);
            }
        }
        OwnerInteractionAgent interaction = null;
        for (Agent agent : executable.agents()) {
            if (agent instanceof OwnerInteractionAgent candidate) {
                if (interaction != null) {
                    throw new IllegalArgumentException(
                            "Module declares more than one owner-interaction Agent: " + executable.id());
                }
                interaction = candidate;
            }
        }
        return new ModuleInstance(executable.definition(), types, bindings,
                Optional.ofNullable(interaction));
    }

    public ModuleDefinition definition() { return definition; }
    public Map<MaterialTypeId, MaterialType<?>> materialTypes() { return materialTypes; }
    public Map<OperationId, OperationBinding<?, ?>> operations() { return operations; }
    public Optional<OwnerInteractionAgent> ownerInteractionAgent() { return ownerInteractionAgent; }

    private void validateBindings() {
        if (!materialTypes.keySet().equals(definition.materialTypes().keySet())) {
            throw new IllegalArgumentException(
                    "Java MaterialType bindings must exactly match declared Material types");
        }
        materialTypes.forEach((id, binding) -> {
            if (!id.equals(binding.id())
                    || !binding.definition().equals(definition.materialTypes().get(id))) {
                throw new IllegalArgumentException(
                        "MaterialType binding differs from the Module declaration: " + id);
            }
            if (!id.moduleId().equals(definition.id())) {
                throw new IllegalArgumentException(
                        "MaterialType binding is not owned by the Module: " + id);
            }
        });
        if (!operations.keySet().equals(definition.operations().keySet())) {
            throw new IllegalArgumentException(
                    "executable Operation bindings must exactly match declared Operations");
        }
        operations.forEach((id, binding) -> {
            if (!id.equals(binding.definition().id())) {
                throw new IllegalArgumentException(
                        "Operation binding key does not match its declaration: " + id);
            }
            OperationDefinition declared = definition.operations().get(id);
            if (!declared.equals(binding.definition())) {
                throw new IllegalArgumentException(
                        "Operation binding contract differs from the Module declaration: " + id);
            }
            if (!id.moduleId().equals(definition.id())) {
                throw new IllegalArgumentException(
                        "Operation binding is not owned by the Module: " + id);
            }
        });
        ownerInteractionAgent.ifPresent(agent -> {
            if (!agent.id().moduleId().equals(definition.id())) {
                throw new IllegalArgumentException(
                        "owner-interaction Agent is not owned by the Module: " + agent.id());
            }
            if (!definition.agents().containsKey(agent.id())) {
                throw new IllegalArgumentException(
                        "owner-interaction Agent is absent from the Module declaration: " + agent.id());
            }
        });
    }
}
