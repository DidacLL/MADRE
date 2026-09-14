package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.OperationId;
import java.util.Map;
import java.util.Objects;

/** One installed executable Module: its canonical definition plus all Operation bindings. */
public final class ModuleInstance {
    private final ModuleDefinition definition;
    private final Map<OperationId, OperationBinding<?, ?>> operations;

    public ModuleInstance(ModuleDefinition definition,
            Map<OperationId, OperationBinding<?, ?>> operations) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.operations = Map.copyOf(operations);
    }

    public ModuleDefinition definition() { return definition; }
    public Map<OperationId, OperationBinding<?, ?>> operations() { return operations; }

    /**
     * Validates the executable surface against the exact canonical declarations. Runtime
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
            if (declared != binding.definition()) {
                throw new IllegalArgumentException(
                        "Operation binding must use the Module's exact canonical declaration: " + id);
            }
            if (!id.moduleId().equals(definition.id())) {
                throw new IllegalArgumentException(
                        "Operation binding is not owned by the Module: " + id);
            }
        });
    }
}
