package io.github.didacll.madre.kernel.client;

import java.util.List;

public interface KernelClient {
    KernelProtocolInfo protocolInfo();
    WorkId submit(PhysicalInferenceRequest request);
    WorkInspection inspect(WorkId workId);
    WorkResult result(WorkId workId);
    WorkState cancel(WorkId workId);
    boolean release(WorkId workId);
    List<CapabilitySnapshot> capabilities();
    List<CapabilitySnapshot> refreshCapabilities();
}
