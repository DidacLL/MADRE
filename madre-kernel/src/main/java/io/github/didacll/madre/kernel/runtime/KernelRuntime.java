package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.module.LiveModuleRegistry;
import java.util.Objects;

/** Cohesive owner of one Kernel process and its reasoning/runtime boundaries. */
public final class KernelRuntime implements AutoCloseable {
    private final SQLiteReasoningWorkStore store;
    private final KernelReasoningService reasoning;
    private final ReasoningCapabilityRegistry reasoningCapabilities;
    private final LiveModuleRegistry modules;

    public KernelRuntime(KernelConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        store = new SQLiteReasoningWorkStore(configuration.workDatabase());
        reasoningCapabilities = new ReasoningCapabilityRegistry(
                new ResourceCoordinator(configuration.resourceCapacity()));
        reasoning = new KernelReasoningService(reasoningCapabilities, store,
                configuration.resultRetention());
        modules = new LiveModuleRegistry(configuration.coreModule());
    }

    public ReasoningCapabilityRegistry reasoningCapabilities() { return reasoningCapabilities; }
    public LiveModuleRegistry modules() { return modules; }
    public KernelReasoningService reasoning() { return reasoning; }

    @Override public void close() { reasoning.close(); }
}
