package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Module-owned durable storage for the shipped Agent's semantic conversation state. */
final class OwnerConversationStore {
    private final Path file;

    OwnerConversationStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
    }

    OwnerConversationState load() {
        if (!Files.exists(file)) return OwnerConversationState.empty();
        try {
            List<OwnerConversationExchange> exchanges = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4) {
                    throw new IllegalStateException("invalid owner conversation state entry");
                }
                exchanges.add(new OwnerConversationExchange(
                        decode(fields[2]), Sensitivity.valueOf(fields[0]),
                        decode(fields[3]), Sensitivity.valueOf(fields[1])));
            }
            return new OwnerConversationState(exchanges);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("cannot read owner conversation state " + file,
                    exception);
        }
    }

    void save(OwnerConversationState state) {
        Objects.requireNonNull(state, "state");
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            List<String> lines = state.exchanges().stream().map(exchange ->
                    exchange.ownerSensitivity().name() + "\t"
                            + exchange.assistantSensitivity().name() + "\t"
                            + encode(exchange.ownerPrompt()) + "\t"
                            + encode(exchange.assistantAnswer())).toList();
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot persist owner conversation state " + file,
                    exception);
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
