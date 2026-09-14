package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Cross-platform JVM discovery of owner-installed Module JARs from one installation directory. */
final class InstalledModuleLoader implements AutoCloseable {
    private final URLClassLoader classLoader;
    private final List<ModuleProvider> providers;

    InstalledModuleLoader(Path directory) {
        Path modules = directory.toAbsolutePath().normalize();
        if (Files.exists(modules) && !Files.isDirectory(modules)) {
            throw new IllegalArgumentException("modules.directory is not a directory: " + modules);
        }
        List<Path> jars;
        if (!Files.exists(modules)) {
            jars = List.of();
        } else {
            try (var entries = Files.list(modules)) {
                jars = entries.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .sorted().toList();
            } catch (IOException exception) {
                throw new IllegalStateException("cannot inspect installed Modules in " + modules,
                        exception);
            }
        }
        URL[] urls = jars.stream().map(InstalledModuleLoader::url).toArray(URL[]::new);
        classLoader = new URLClassLoader(urls, ModuleProvider.class.getClassLoader());
        try {
            providers = ServiceLoader.load(ModuleProvider.class, classLoader).stream()
                    .map(ServiceLoader.Provider::get).toList();
        } catch (ServiceConfigurationError error) {
            try {
                classLoader.close();
            } catch (IOException closeFailure) {
                error.addSuppressed(closeFailure);
            }
            throw new IllegalStateException("cannot load installed Module provider", error);
        }
    }

    List<ModuleProvider> providers() { return providers; }

    static Path defaultDirectory() {
        try {
            Path location = Path.of(MadreApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path parent = Files.isDirectory(location) ? location : location.getParent();
            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equals("lib")
                    && parent.getParent() != null) {
                return parent.getParent().resolve("modules");
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // Development execution can use an explicit modules.directory.
        }
        return Path.of("modules").toAbsolutePath().normalize();
    }

    private static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalArgumentException("invalid Module JAR path: " + path, exception);
        }
    }

    @Override public void close() {
        try {
            classLoader.close();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot close installed Module loader", exception);
        }
    }
}
