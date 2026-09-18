package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import java.nio.file.Path;
import java.util.Objects;

/** Runtime services bound to one exact acting Agent for an execution chain. */
public record AgentContext(Agent actor, ReasoningService reasoning,
        ModuleDirectory directory, ModuleInvoker modules, Path stateDirectory) {
    public AgentContext {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(stateDirectory, "stateDirectory");
    }

    void requireActor(Agent candidate) {
        if (actor != Objects.requireNonNull(candidate, "candidate")) {
            throw new IllegalArgumentException("AgentContext belongs to another acting Agent");
        }
    }
}
