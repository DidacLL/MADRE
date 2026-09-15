package io.github.didacll.madre.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Host-owned product locations and safe first-run/configuration persistence. */
final class HostEnvironment {
    private static final String PRODUCT = "MADRE";
    private static final String UNIX_PRODUCT = "madre";

    private final Path programDirectory;
    private final Path configurationFile;
    private final Path dataDirectory;
    private final Path stateDirectory;
    private final Path ownerModuleDirectory;
    private final Path ownerReasoningDirectory;
    private final Path shippedModuleDirectory;
    private final Path shippedReasoningDirectory;
    private final Path packagedDefaultsFile;

    private HostEnvironment(Path programDirectory, Path configurationFile, Path dataDirectory,
            Path stateDirectory, Path ownerModuleDirectory, Path ownerReasoningDirectory,
            Path shippedModuleDirectory, Path shippedReasoningDirectory,
            Path packagedDefaultsFile) {
        this.programDirectory = normalize(programDirectory);
        this.configurationFile = normalize(configurationFile);
        this.dataDirectory = normalize(dataDirectory);
        this.stateDirectory = normalize(stateDirectory);
        this.ownerModuleDirectory = normalize(ownerModuleDirectory);
        this.ownerReasoningDirectory = normalize(ownerReasoningDirectory);
        this.shippedModuleDirectory = normalize(shippedModuleDirectory);
        this.shippedReasoningDirectory = normalize(shippedReasoningDirectory);
        this.packagedDefaultsFile = normalize(packagedDefaultsFile);
    }

    static HostEnvironment resolve() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw new IllegalStateException("user.home is unavailable");
        }
        return resolve(System.getenv(), System.getProperty("os.name", ""), Path.of(userHome),
                locateProgramDirectory());
    }

    static HostEnvironment resolve(Map<String, String> environment, String operatingSystem,
            Path userHome, Path programDirectory) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        Path home = normalize(Objects.requireNonNull(userHome, "userHome"));
        Path program = normalize(Objects.requireNonNull(programDirectory, "programDirectory"));
        boolean windows = operatingSystem.toLowerCase(Locale.ROOT).contains("win");

        final Path configurationDirectory;
        final Path dataDirectory;
        final Path stateDirectory;
        if (windows) {
            Path roaming = environmentPath(environment, "APPDATA",
                    home.resolve("AppData").resolve("Roaming"));
            Path local = environmentPath(environment, "LOCALAPPDATA",
                    home.resolve("AppData").resolve("Local"));
            configurationDirectory = roaming.resolve(PRODUCT);
            dataDirectory = local.resolve(PRODUCT);
            stateDirectory = dataDirectory.resolve("state");
        } else {
            Path configRoot = environmentPath(environment, "XDG_CONFIG_HOME",
                    home.resolve(".config"));
            Path dataRoot = environmentPath(environment, "XDG_DATA_HOME",
                    home.resolve(".local").resolve("share"));
            Path stateRoot = environmentPath(environment, "XDG_STATE_HOME",
                    home.resolve(".local").resolve("state"));
            configurationDirectory = configRoot.resolve(UNIX_PRODUCT);
            dataDirectory = dataRoot.resolve(UNIX_PRODUCT);
            stateDirectory = stateRoot.resolve(UNIX_PRODUCT);
        }

        return new HostEnvironment(program, configurationDirectory.resolve("madre.properties"),
                dataDirectory, stateDirectory, dataDirectory.resolve("modules"),
                dataDirectory.resolve("reasoning"), program.resolve("modules"),
                program.resolve("reasoning"), program.resolve("defaults").resolve("madre.properties"));
    }

    LoadedConfiguration loadConfiguration(Optional<Path> explicitConfiguration) throws IOException {
        Objects.requireNonNull(explicitConfiguration, "explicitConfiguration");
        boolean bootstrapped = false;
        Path selected;
        if (explicitConfiguration.isPresent()) {
            selected = normalize(explicitConfiguration.orElseThrow());
        } else {
            bootstrapped = bootstrapIfMissing();
            selected = configurationFile;
        }
        if (!Files.isRegularFile(selected)) {
            throw new IOException("MADRE configuration file does not exist: " + selected);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(selected)) {
            properties.load(input);
        }
        return new LoadedConfiguration(selected, properties, bootstrapped,
                explicitConfiguration.isPresent());
    }

    boolean bootstrapIfMissing() throws IOException {
        Files.createDirectories(configurationFile.getParent());
        Files.createDirectories(dataDirectory);
        Files.createDirectories(stateDirectory);
        Files.createDirectories(ownerModuleDirectory);
        Files.createDirectories(ownerReasoningDirectory);
        if (Files.exists(configurationFile)) return false;

        Properties defaults = new Properties();
        if (Files.isRegularFile(packagedDefaultsFile)) {
            try (InputStream input = Files.newInputStream(packagedDefaultsFile)) {
                defaults.load(input);
            }
        }
        defaults.setProperty("kernel.database", stateDirectory.resolve("kernel-work.sqlite").toString());
        defaults.setProperty("modules.state-directory", stateDirectory.resolve("module-state").toString());

        Path temporary = Files.createTempFile(configurationFile.getParent(), "madre-", ".properties.tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                defaults.store(output,
                        "MADRE owner configuration. Provider-specific settings remain provider-owned.");
            }
            try {
                Files.move(temporary, configurationFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, configurationFile);
                } catch (FileAlreadyExistsException race) {
                    return false;
                }
            } catch (FileAlreadyExistsException exception) {
                return false;
            }
            moved = true;
            return true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    static void replaceConfiguration(Path path, Properties properties) throws IOException {
        Path destination = normalize(Objects.requireNonNull(path, "path"));
        Properties values = Objects.requireNonNull(properties, "properties");
        Path parent = destination.getParent();
        if (parent == null) throw new IOException("configuration has no parent directory: " + destination);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "madre-", ".properties.tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, deterministicProperties(values), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static String deterministicProperties(Properties properties) throws IOException {
        Properties copy = new Properties();
        properties.stringPropertyNames().stream().sorted()
                .forEach(name -> copy.setProperty(name, properties.getProperty(name)));
        StringWriter encoded = new StringWriter();
        copy.store(encoded, null);
        List<String> lines = encoded.toString().lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .sorted().toList();
        return "# MADRE owner configuration. Provider-specific settings remain provider-owned.\n"
                + String.join("\n", lines) + "\n";
    }

    boolean hasPackagedDefaults() { return Files.isRegularFile(packagedDefaultsFile); }

    Path configurationFile() { return configurationFile; }
    Path dataDirectory() { return dataDirectory; }
    Path stateDirectory() { return stateDirectory; }
    Path programDirectory() { return programDirectory; }
    Path ownerModuleDirectory() { return ownerModuleDirectory; }
    Path ownerReasoningDirectory() { return ownerReasoningDirectory; }
    List<Path> moduleDirectories() { return List.of(shippedModuleDirectory, ownerModuleDirectory); }
    List<Path> reasoningDirectories() { return List.of(shippedReasoningDirectory, ownerReasoningDirectory); }

    private static Path locateProgramDirectory() {
        String configured = System.getProperty("madre.program-directory");
        if (configured != null && !configured.isBlank()) return normalize(Path.of(configured));
        try {
            Path location = normalize(Path.of(MadreApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()));
            Path container = Files.isDirectory(location) ? location : location.getParent();
            if (container != null && container.getFileName() != null
                    && container.getFileName().toString().equals("lib")
                    && container.getParent() != null) {
                return container.getParent();
            }
            if (container != null) return container;
        } catch (URISyntaxException | RuntimeException ignored) {
            // Fall through to the process directory for source/development execution.
        }
        return normalize(Path.of(""));
    }

    private static Path environmentPath(Map<String, String> environment, String name, Path fallback) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? normalize(fallback) : normalize(Path.of(value));
    }

    private static Path normalize(Path path) { return path.toAbsolutePath().normalize(); }

    record LoadedConfiguration(Path path, Properties properties, boolean bootstrapped,
            boolean explicit) {
        LoadedConfiguration {
            path = normalize(Objects.requireNonNull(path, "path"));
            properties = Objects.requireNonNull(properties, "properties");
        }
    }
}
