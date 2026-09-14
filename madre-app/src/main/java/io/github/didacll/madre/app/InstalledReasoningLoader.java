package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
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

/** Cross-platform JVM discovery of owner-installed reasoning-mechanism JARs. */
final class InstalledReasoningLoader implements AutoCloseable {
    private final URLClassLoader classLoader;
    private final List<ReasoningMechanismProvider> providers;

    InstalledReasoningLoader(Path directory) {
        Path reasoning = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (Files.exists(reasoning) && !Files.isDirectory(reasoning)) {
            throw new IllegalArgumentException("reasoning.directory is not a directory: " + reasoning);
        }
        List<Path> jars;
        if (!Files.exists(reasoning)) {
            jars = List.of();
        } else {
            try (var entries = Files.list(reasoning)) {
                jars = entries.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .sorted().toList();
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "cannot inspect installed reasoning mechanisms in " + reasoning, exception);
            }
        }
        URL[] urls = jars.stream().map(InstalledReasoningLoader::url).toArray(URL[]::new);
        classLoader = new URLClassLoader(urls, ReasoningMechanismProvider.class.getClassLoader());
        try {
            providers = ServiceLoader.load(ReasoningMechanismProvider.class, classLoader).stream()
                    .map(ServiceLoader.Provider::get).toList();
        } catch (ServiceConfigurationError | RuntimeException failure) {
            try {
                classLoader.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw new IllegalStateException("cannot load installed reasoning provider", failure);
        }
    }

    List<ReasoningMechanism<?, ?>> materialize(ReasoningProviderConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (ReasoningMechanismProvider provider : providers) {
            List<ReasoningMechanism<?, ?>> provided = provider.materialize(configuration);
            if (provided == null) {
                throw new IllegalStateException(provider.getClass().getName()
                        + " returned null reasoning materialization");
            }
            for (ReasoningMechanism<?, ?> mechanism : provided) {
                mechanisms.add(Objects.requireNonNull(mechanism,
                        provider.getClass().getName() + " returned a null reasoning mechanism"));
            }
        }
        return List.copyOf(mechanisms);
    }

    static Path defaultDirectory() {
        try {
            Path location = Path.of(MadreApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path parent = Files.isDirectory(location) ? location : location.getParent();
            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equals("lib")
                    && parent.getParent() != null) {
                return parent.getParent().resolve("reasoning");
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // Development execution can use an explicit reasoning.directory.
        }
        return Path.of("reasoning").toAbsolutePath().normalize();
    }

    private static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalArgumentException("invalid reasoning-mechanism JAR path: " + path,
                    exception);
        }
    }

    @Override public void close() {
        RuntimeException failure = null;
        for (int index = providers.size() - 1; index >= 0; index--) {
            try {
                providers.get(index).close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        try {
            classLoader.close();
        } catch (IOException exception) {
            IllegalStateException wrapped = new IllegalStateException(
                    "cannot close installed reasoning loader", exception);
            if (failure == null) failure = wrapped;
            else failure.addSuppressed(wrapped);
        }
        if (failure != null) throw failure;
    }
}
