package io.github.didacll.madre.kernel;

import java.time.Instant;
import java.util.Objects;

/** Per-attempt technical context supplied to an inference engine. */
public record EngineExecution(WorkId workId, int attempt, Instant deadline) {
    public EngineExecution {
        Objects.requireNonNull(workId, "workId");
        if (attempt <= 0) throw new IllegalArgumentException("attempt must be positive");
        Objects.requireNonNull(deadline, "deadline");
    }
}
