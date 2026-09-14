package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.execution.ExecutionService;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import java.nio.file.Path;
import java.util.Objects;

/** Ordinary SDK services supplied when an installed Module is materialized. */
public record ModuleContext(ExecutionService execution, ModuleDirectory directory,
        ModuleInvoker invoker, Path stateDirectory) {
    public ModuleContext {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(invoker, "invoker");
        Objects.requireNonNull(stateDirectory, "stateDirectory");
    }
}
