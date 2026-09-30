package io.github.didacll.madre.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/** Durable timing and delivery facts. References retain Module-owned meaning. */
final class RuntimeWakeups {
    private final Path directory;

    RuntimeWakeups(Path home) throws IOException {
        directory = home.resolve("wakeups");
        Files.createDirectories(directory);
    }

    synchronized String schedule(String moduleId, Instant due, String reference) throws IOException {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(due, "due");
        Objects.requireNonNull(reference, "reference");
        String id = UUID.randomUUID().toString();
        write(new Wakeup(id, moduleId, due, reference, 0, ""));
        return id;
    }

    synchronized boolean cancel(String moduleId, String id) throws IOException {
        Path path = path(id);
        if (!Files.exists(path)) return false;
        Wakeup wakeup = read(path);
        if (!wakeup.moduleId().equals(moduleId)) return false;
        return Files.deleteIfExists(path);
    }

    synchronized List<Wakeup> pending() throws IOException {
        List<Wakeup> pending = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path file : files) pending.add(read(file));
        }
        pending.sort(Comparator.comparing(Wakeup::due).thenComparing(Wakeup::id));
        return List.copyOf(pending);
    }

    synchronized void delivered(Wakeup wakeup) throws IOException {
        Files.deleteIfExists(path(wakeup.id()));
    }

    synchronized void failed(Wakeup wakeup, Exception failure) throws IOException {
        write(new Wakeup(wakeup.id(), wakeup.moduleId(), wakeup.due(), wakeup.reference(),
                wakeup.attempts() + 1, failure.toString()));
    }

    private Wakeup read(Path file) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        }
        try {
            String id = file.getFileName().toString().replaceFirst("\\.properties$", "");
            return new Wakeup(id, values.getProperty("module"),
                    Instant.parse(values.getProperty("due")), values.getProperty("reference"),
                    Integer.parseInt(values.getProperty("attempts", "0")),
                    values.getProperty("error", ""));
        } catch (RuntimeException failure) {
            throw new IOException("Invalid Runtime wakeup " + file, failure);
        }
    }

    private void write(Wakeup wakeup) throws IOException {
        Properties values = new Properties();
        values.setProperty("module", wakeup.moduleId());
        values.setProperty("due", wakeup.due().toString());
        values.setProperty("reference", wakeup.reference());
        values.setProperty("attempts", Integer.toString(wakeup.attempts()));
        values.setProperty("error", wakeup.error());
        Path temporary = Files.createTempFile(directory, ".wakeup-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "MADRE Runtime wakeup");
            }
            try {
                Files.move(temporary, path(wakeup.id()), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path(wakeup.id()), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(String id) {
        UUID parsed = UUID.fromString(Objects.requireNonNull(id, "id"));
        if (!parsed.toString().equals(id)) throw new IllegalArgumentException("Invalid wakeup ID");
        return directory.resolve(id + ".properties");
    }

    record Wakeup(String id, String moduleId, Instant due, String reference,
                  int attempts, String error) { }
}
