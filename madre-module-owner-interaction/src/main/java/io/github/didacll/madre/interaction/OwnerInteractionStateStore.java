package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.WorkId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Small Module-owned persistence for its outstanding semantic background requests. */
final class OwnerInteractionStateStore {
    private final Path file;
    private final Map<WorkId, Sensitivity> pending = new LinkedHashMap<>();

    OwnerInteractionStateStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
        load();
    }

    synchronized void add(WorkId id, Sensitivity sensitivity) {
        pending.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(sensitivity, "sensitivity"));
        save();
    }

    synchronized void remove(WorkId id) {
        if (pending.remove(id) != null) save();
    }

    synchronized Map<WorkId, Sensitivity> snapshot() { return Map.copyOf(pending); }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 2) {
                    throw new IllegalStateException("invalid owner-interaction state entry");
                }
                pending.put(new WorkId(fields[0]), Sensitivity.valueOf(fields[1]));
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "cannot read owner-interaction Module state " + file, exception);
        }
    }

    private void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            List<String> lines = pending.entrySet().stream()
                    .map(entry -> entry.getKey().value() + "\t" + entry.getValue().name()).toList();
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "cannot persist owner-interaction Module state " + file, exception);
        }
    }
}
