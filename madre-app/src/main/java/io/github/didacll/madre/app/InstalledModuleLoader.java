package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/** Cross-platform JVM discovery of installed Module JARs from installation directories. */
final class InstalledModuleLoader implements AutoCloseable {
    private final URLClassLoader classLoader;
    private final List<ModuleProvider> providers;
    private final List<Path> jars;

    InstalledModuleLoader(Path directory) { this(installationDirectories(directory)); }

    InstalledModuleLoader(List<Path> directories) {
        this(new JarSelection(jars(directories, "Module", "modules.directory")));
    }

    private InstalledModuleLoader(JarSelection selection) {
        jars = selection.jars();
        URL[] urls = jars.stream().map(InstalledModuleLoader::url).toArray(URL[]::new);
        Set<Path> selected = new HashSet<>(jars);
        classLoader = new URLClassLoader(urls, ModuleProvider.class.getClassLoader());
        try {
            providers = ServiceLoader.load(ModuleProvider.class, classLoader).stream()
                    .filter(provider -> selected.contains(sourceJar(provider.type())))
                    .map(ServiceLoader.Provider::get).toList();
        } catch (ServiceConfigurationError | RuntimeException error) {
            try {
                classLoader.close();
            } catch (IOException closeFailure) {
                error.addSuppressed(closeFailure);
            }
            throw new IllegalStateException("cannot load installed Module provider", error);
        }
    }

    static InstalledModuleLoader forJar(Path jar) {
        Path selected = Objects.requireNonNull(jar, "jar").toAbsolutePath().normalize();
        if (!Files.isRegularFile(selected)) {
            throw new IllegalArgumentException("Module JAR is not a regular file: " + selected);
        }
        return new InstalledModuleLoader(new JarSelection(List.of(selected)));
    }

    List<ModuleProvider> providers() { return providers; }

    List<DiscoveredProvider> discoveredProviders() {
        return providers.stream().map(provider -> new DiscoveredProvider(provider,
                sourceJar(provider.getClass()))).toList();
    }

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
                        .map(path -> path.toAbsolutePath().normalize())
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

    private static Path sourceJar(Class<?> type) {
        try {
            URL location = Objects.requireNonNull(type.getProtectionDomain().getCodeSource(),
                    "provider code source").getLocation();
            return Path.of(location.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            throw new IllegalStateException("cannot resolve Module provider source for "
                    + type.getName(), exception);
        }
    }

    @Override public void close() {
        try {
            classLoader.close();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot close installed Module loader", exception);
        }
    }

    record DiscoveredProvider(ModuleProvider provider, Path sourceJar) {
        DiscoveredProvider {
            Objects.requireNonNull(provider, "provider");
            sourceJar = Objects.requireNonNull(sourceJar, "sourceJar").toAbsolutePath().normalize();
        }
    }

    private record JarSelection(List<Path> jars) {
        JarSelection {
            jars = List.copyOf(Objects.requireNonNull(jars, "jars"));
        }
    }
}
