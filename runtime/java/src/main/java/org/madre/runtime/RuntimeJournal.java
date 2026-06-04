package org.madre.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RuntimeJournal {
    private final UUID journalId;
    private final List<RuntimeEvent> events = new ArrayList<>();

    public RuntimeJournal() {
        this(UUID.randomUUID());
    }

    public RuntimeJournal(UUID journalId) {
        this.journalId = Objects.requireNonNull(journalId, "journalId");
    }

    public UUID id() {
        return journalId;
    }

    public RuntimeEvent append(RuntimeEvent event) {
        Objects.requireNonNull(event, "event");
        boolean duplicate = events.stream().anyMatch(existing -> existing.id().equals(event.id()));
        if (duplicate) {
            throw new IllegalArgumentException("event already appended: " + event.id());
        }
        events.add(event);
        return event;
    }

    public List<RuntimeEvent> trace(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return events.stream()
                .filter(event -> subjectId.equals(event.subjectId()) || subjectId.equals(event.payloadId()))
                .toList();
    }

    public List<RuntimeEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
