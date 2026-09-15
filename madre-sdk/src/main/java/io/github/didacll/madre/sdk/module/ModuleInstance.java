package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.MaterialType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Validated runtime assembly of portable Module contracts and Java execution bindings. */
public final class ModuleInstance {
    private final ModuleDefinition definition;
    private final Map<MaterialTypeId, MaterialType<?>> materialTypes;
    private final Map<OperationId, OperationBinding<?, ?>> operations;

    /**
     * Adapter-oriented assembly path for an already available portable definition and Java-facing
     * payload/behavior bindings. The assembly is valid by construction.
     */
    public ModuleInstance(ModuleDefinition definition,
            Map<MaterialTypeId, MaterialType<?>> materialTypes,
            Map<OperationId, OperationBinding<?, ?>> operations) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.materialTypes = Map.copyOf(materialTypes);
        this.operations = Map.copyOf(operations);
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
        return new ModuleInstance(executable.definition(), types, bindings);
    }

    public ModuleDefinition definition() { return definition; }
    public Map<MaterialTypeId, MaterialType<?>> materialTypes() { return materialTypes; }
    public Map<OperationId, OperationBinding<?, ?>> operations() { return operations; }

    /** Confirms that Java bindings exactly realize the canonical portable contracts. */
    public void validateBindings() {
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
    }
}
