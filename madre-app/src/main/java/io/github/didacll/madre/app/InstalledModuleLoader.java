package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Cross-platform JVM discovery of owner-installed Module JARs from installation directories. */
final class InstalledModuleLoader implements AutoCloseable {
    private final URLClassLoader classLoader;
    private final List<ModuleProvider> providers;

    InstalledModuleLoader(Path directory) { this(installationDirectories(directory)); }

    InstalledModuleLoader(List<Path> directories) {
        List<Path> jars = jars(directories, "Module", "modules.directory");
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
            if (parent != null) return parent.resolve("modules");
        } catch (URISyntaxException | RuntimeException ignored) {
            // Development execution can use an explicit modules.directory.
        }
        return Path.of("modules").toAbsolutePath().normalize();
    }

    private static List<Path> installationDirectories(Path directory) {
        Path requested = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Path shipped = defaultDirectory().toAbsolutePath().normalize();
        if (!requested.equals(shipped)) return List.of(requested);
        HostEnvironment host = HostEnvironment.resolve();
        if (!host.hasPackagedDefaults()) return List.of(shipped);
        Path owner = host.ownerModuleDirectory();
        return owner.equals(shipped) ? List.of(shipped) : List.of(shipped, owner);
    }

    private static List<Path> jars(List<Path> directories, String artifactName, String propertyName) {
        Objects.requireNonNull(directories, "directories");
        List<Path> result = new ArrayList<>();
        for (Path directory : directories.stream().map(path -> Objects.requireNonNull(path, "directory")
                .toAbsolutePath().normalize()).distinct().toList()) {
            if (Files.exists(directory) && !Files.isDirectory(directory)) {
                throw new IllegalArgumentException(propertyName + " is not a directory: " + directory);
            }
            if (!Files.exists(directory)) continue;
            try (var entries = Files.list(directory)) {
                result.addAll(entries.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .sorted().toList());
            } catch (IOException exception) {
                throw new IllegalStateException("cannot inspect installed " + artifactName + "s in "
                        + directory, exception);
            }
        }
        return List.copyOf(result);
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
