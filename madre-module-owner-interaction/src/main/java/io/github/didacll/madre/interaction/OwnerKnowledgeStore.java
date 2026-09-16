package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Module-owned persistence for explicit owner-controlled semantic knowledge. */
final class OwnerKnowledgeStore {
    private final Path file;
    private final Map<String, OwnerKnowledgeEntry> entries;

    OwnerKnowledgeStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
        this.entries = load();
    }

    synchronized OwnerKnowledgeEntry put(OwnerKnowledgeKind kind, String key, String value,
            Sensitivity sensitivity) {
        OwnerKnowledgeEntry entry = new OwnerKnowledgeEntry(
                new MaterialId(OwnerInteractionModule.ID, "knowledge-" + UUID.randomUUID()),
                Objects.requireNonNull(kind, "kind"), normalizeKey(key),
                requireValue(value), Objects.requireNonNull(sensitivity, "sensitivity"));
        entries.put(entry.key(), entry);
        save();
        return entry;
    }

    synchronized Optional<OwnerKnowledgeEntry> get(String key) {
        return Optional.ofNullable(entries.get(normalizeKey(key)));
    }

    synchronized Optional<OwnerKnowledgeEntry> remove(String key) {
        OwnerKnowledgeEntry removed = entries.remove(normalizeKey(key));
        if (removed != null) save();
        return Optional.ofNullable(removed);
    }

    synchronized List<OwnerKnowledgeEntry> snapshot() {
        return List.copyOf(entries.values());
    }

    private Map<String, OwnerKnowledgeEntry> load() {
        Map<String, OwnerKnowledgeEntry> loaded = new LinkedHashMap<>();
        if (!Files.exists(file)) return loaded;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 5) {
                    throw new IllegalStateException("invalid owner knowledge state entry");
                }
                OwnerKnowledgeEntry entry = new OwnerKnowledgeEntry(
                        new MaterialId(OwnerInteractionModule.ID, fields[2]),
                        OwnerKnowledgeKind.valueOf(fields[0]), decode(fields[3]),
                        decode(fields[4]), Sensitivity.valueOf(fields[1]));
                loaded.put(entry.key(), entry);
            }
            return loaded;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("cannot read owner knowledge state " + file, exception);
        }
    }

    private void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            List<String> lines = new ArrayList<>();
            for (OwnerKnowledgeEntry entry : entries.values()) {
                lines.add(entry.kind().name() + "\t" + entry.sensitivity().name() + "\t"
                        + entry.id().value() + "\t" + encode(entry.key()) + "\t"
                        + encode(entry.value()));
            }
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot persist owner knowledge state " + file,
                    exception);
        }
    }

    private static String normalizeKey(String value) {
        String normalized = Objects.requireNonNull(value, "key").strip()
                .replaceAll("[?.:]+$", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("knowledge key must not be blank");
        return normalized;
    }

    private static String requireValue(String value) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("knowledge value must not be blank");
        }
        return normalized;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
