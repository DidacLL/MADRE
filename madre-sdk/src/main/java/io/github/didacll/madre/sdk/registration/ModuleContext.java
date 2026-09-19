package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Ordinary SDK services supplied when one exact installed Module is materialized. The directory
 * and invoker are caller-bound by runtime assembly; owner-local and external/PUBLIC authority are
 * deliberately absent.
 */
public record ModuleContext(ModuleDirectory directory, Path stateDirectory) {
    public ModuleContext {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(stateDirectory, "stateDirectory");
    }
}
