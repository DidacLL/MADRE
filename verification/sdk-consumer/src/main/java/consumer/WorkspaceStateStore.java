package consumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Small durable state store owned entirely by the independent application Module. */
final class WorkspaceStateStore {
    private final Path file;

    WorkspaceStateStore(Path stateDirectory) {
        Path directory = Objects.requireNonNull(stateDirectory, "stateDirectory").toAbsolutePath();
        this.file = directory.resolve("workspace-notes.txt");
    }

    synchronized int add(String note) {
        String value = Objects.requireNonNull(note, "note").replaceAll("\\s+", " ").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("workspace note must not be blank");
        List<String> notes = new ArrayList<>(snapshot());
        notes.add(value);
        write(notes);
        return notes.size();
    }

    synchronized List<String> snapshot() {
        if (!Files.exists(file)) return List.of();
        try {
            return List.copyOf(Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank()).toList());
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read independent workspace state", failure);
        }
    }

    synchronized int clear() {
        int count = snapshot().size();
        write(List.of());
        return count;
    }

    private void write(List<String> notes) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, notes, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write independent workspace state", failure);
        }
    }
}
