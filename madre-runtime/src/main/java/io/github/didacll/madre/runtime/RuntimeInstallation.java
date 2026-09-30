package io.github.didacll.madre.runtime;

import io.github.didacll.madre.kernel.client.EngineDescriptor;
import io.github.didacll.madre.kernel.client.LocalKernelClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Owner-local Runtime installation mechanics that do not presume a Module SDK shape.
 * A staged JAR is an artifact, not yet a discovered or activated Module.
 */
public final class RuntimeInstallation {
    private static final String KERNEL_ENDPOINT = "kernel.endpoint";
    private static final String CORE_ROLE = "role.core";

    private final Path home;
    private final Path artifacts;
    private final Path configuration;

    public RuntimeInstallation(Path home) throws IOException {
        this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        artifacts = this.home.resolve("artifacts");
        configuration = this.home.resolve("runtime.properties");
        Files.createDirectories(artifacts);
    }

    public Path home() { return home; }

    /** Stable location for one Module's own persistence. Runtime never interprets its files. */
    public Path moduleDataDirectory(String moduleId) throws IOException {
        if (Objects.requireNonNull(moduleId, "moduleId").isBlank()) {
            throw new IllegalArgumentException("Module identity must not be blank");
        }
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(moduleId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        Path directory = home.resolve("module-data")
                .resolve("module-" + HexFormat.of().formatHex(digest));
        Files.createDirectories(directory);
        return directory;
    }

    /** Stage one readable Java JAR without assigning Module identity or executing code. */
    public String installArtifact(Path source) throws IOException {
        Path input = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        String name = artifactName(input.getFileName().toString());
        try (JarFile jar = new JarFile(input.toFile())) {
            if (jar.size() == 0) throw new IOException("Empty JAR: " + input);
        }
        Path destination = artifacts.resolve(name);
        if (Files.exists(destination)) {
            throw new IOException("Artifact already staged: " + name);
        }
        Path temporary = Files.createTempFile(artifacts, ".artifact-", ".tmp");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return name;
    }

    public List<String> artifacts() throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(artifacts, "*.jar")) {
            for (Path entry : entries) names.add(entry.getFileName().toString());
        }
        names.sort(Comparator.naturalOrder());
        return List.copyOf(names);
    }

    public void removeArtifact(String name) throws IOException {
        Files.delete(artifacts.resolve(artifactName(name)));
    }

    /** Owner-editable local Kernel endpoint; this does not assert semantic locality or assurance. */
    public void configureKernelEndpoint(Path endpoint) throws IOException {
        String value = Objects.requireNonNull(endpoint, "endpoint").toString();
        if (value.isBlank()) throw new IllegalArgumentException("Kernel endpoint must not be blank");
        Properties properties = readConfiguration();
        properties.setProperty(KERNEL_ENDPOINT, value);
        writeConfiguration(properties);
    }

    public void assignCore(String moduleId) throws IOException {
        if (Objects.requireNonNull(moduleId, "moduleId").isBlank()) {
            throw new IllegalArgumentException("Module identity must not be blank");
        }
        Properties properties = readConfiguration();
        properties.setProperty(CORE_ROLE, moduleId);
        writeConfiguration(properties);
    }

    public Optional<String> core() throws IOException {
        return Optional.ofNullable(readConfiguration().getProperty(CORE_ROLE));
    }

    private void writeConfiguration(Properties properties) throws IOException {
        Path temporary = Files.createTempFile(home, ".runtime-", ".properties");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "MADRE Runtime installation");
            }
            try {
                Files.move(temporary, configuration, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, configuration, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Optional<Path> kernelEndpoint() throws IOException {
        String value = readConfiguration().getProperty(KERNEL_ENDPOINT);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Path.of(value));
    }

    /** Physical facts from the closed Kernel client; no semantic interpretation. */
    public List<EngineDescriptor> engines() throws IOException {
        Path endpoint = kernelEndpoint().orElseThrow(() ->
                new IllegalStateException("Configure kernel.endpoint before inspecting engines"));
        return new LocalKernelClient(endpoint).engines();
    }

    private Properties readConfiguration() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(configuration)) {
            try (InputStream input = Files.newInputStream(configuration)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private static String artifactName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || !name.endsWith(".jar") || name.equals(".jar")
                || name.contains("/") || name.contains("\\") || name.equals("..")) {
            throw new IllegalArgumentException("Expected a JAR filename without directories");
        }
        return name;
    }
}
