package io.github.didacll.madre.sdk.testkit;

import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Temporary public {@link ModuleContext} fixture for isolated Module semantic tests. */
public final class ModuleTestContext implements AutoCloseable {
    private final Path stateDirectory;
    private final ModuleContext context;

    private ModuleTestContext(ReasoningService reasoning, ModuleDirectory directory,
            ModuleInvoker invoker) {
        try {
            stateDirectory = Files.createTempDirectory("madre-module-test-");
        } catch (IOException failure) {
            throw new UncheckedIOException("unable to create temporary Module state directory", failure);
        }
        context = new ModuleContext(Objects.requireNonNull(reasoning, "reasoning"),
                Objects.requireNonNull(directory, "directory"),
                Objects.requireNonNull(invoker, "invoker"), stateDirectory);
    }

    /** Creates an isolated context with no reachable foreign Modules. */
    public static ModuleTestContext create(ReasoningService reasoning) {
        ModuleDirectory emptyDirectory = query -> List.of();
        ModuleInvoker unavailableInvoker = new ModuleInvoker() {
            @Override
            public <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException(
                        "Module-to-Module invocation is not configured in this semantic test context"));
            }
        };
        return new ModuleTestContext(reasoning, emptyDirectory, unavailableInvoker);
    }

    /** Creates an isolated context with explicitly supplied caller-bound composition doubles. */
    public static ModuleTestContext create(ReasoningService reasoning, ModuleDirectory directory,
            ModuleInvoker invoker) {
        return new ModuleTestContext(reasoning, directory, invoker);
    }

    public ModuleContext context() { return context; }
    public Path stateDirectory() { return stateDirectory; }

    @Override
    public void close() {
        try (var paths = Files.walk(stateDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("unable to delete temporary Module state directory", failure);
        }
    }
}
