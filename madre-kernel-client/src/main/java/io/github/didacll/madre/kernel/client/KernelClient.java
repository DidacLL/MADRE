package io.github.didacll.madre.kernel.client;

import java.util.List;
import java.util.Optional;

public interface KernelClient {
    WorkId submit(WorkRequest request);

    WorkStatus status(WorkId id);

    Optional<WorkResult> result(WorkId id);

    WorkStatus cancel(WorkId id);

    void acknowledge(WorkId id);

    List<EngineDescriptor> engines();

    EngineDescriptor engineStatus(String engineId);
}
