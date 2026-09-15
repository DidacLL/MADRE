package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.OperationId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Validated runtime assembly of a Module's portable contract and executable Operation bindings. */
public final class ModuleInstance {
    private final ModuleDefinition definition;
    private final Map<OperationId, OperationBinding<?, ?>> operations;

    /**
     * Adapter-oriented assembly path for an already available portable definition.
     * Native Java Modules should normally implement {@link Module} and use {@link #from(Module)}.
     */
    public ModuleInstance(ModuleDefinition definition,
            Map<OperationId, OperationBinding<?, ?>> operations) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.operations = Map.copyOf(operations);
    }

    /** Derives the portable description and canonical binding map from one Java Module object. */
    public static ModuleInstance from(Module module) {
        Module executable = Objects.requireNonNull(module, "module");
        Map<OperationId, OperationBinding<?, ?>> bindings = new LinkedHashMap<>();
        for (OperationBinding<?, ?> binding : executable.operations()) {
            OperationBinding<?, ?> value = Objects.requireNonNull(binding, "operation");
            OperationId id = value.definition().id();
            if (bindings.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate Operation identity: " + id);
            }
        }
        ModuleInstance instance = new ModuleInstance(executable.definition(), bindings);
        instance.validateBindings();
        return instance;
    }

    public ModuleDefinition definition() { return definition; }
    public Map<OperationId, OperationBinding<?, ?>> operations() { return operations; }

    /**
     * Validates the executable surface against the canonical portable contracts. Runtime
     * registration invokes this before the Module becomes reachable.
     */
    public void validateBindings() {
        if (!operations.keySet().equals(definition.operations().keySet())) {
            throw new IllegalArgumentException(
                    "executable Operation bindings must exactly match declared Operations");
        }
        operations.forEach((id, binding) -> {
            if (!id.equals(binding.definition().id())) {
                throw new IllegalArgumentException(
                        "Operation binding key does not match its declaration: " + id);
            }
            OperationDefinition<?, ?> declared = definition.operations().get(id);
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
