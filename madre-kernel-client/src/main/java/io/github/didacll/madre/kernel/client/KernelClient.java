package io.github.didacll.madre.kernel.client;

import java.util.Optional;

public interface KernelClient {
    WorkId submit(WorkRequest request);
    WorkStatus status(WorkId id);
    WorkInspection inspect(WorkId id);
    Optional<WorkResult> result(WorkId id);
    WorkStatus cancel(WorkId id);
    void release(WorkId id);
}
