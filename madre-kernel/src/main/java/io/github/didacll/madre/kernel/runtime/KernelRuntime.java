package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.module.LiveModuleRegistry;
import java.util.Objects;

/** Cohesive owner of one Kernel process and its physical/runtime boundaries. */
public final class KernelRuntime implements AutoCloseable {
    private final SQLiteWorkStore store;
    private final KernelExecutionService execution;
    private final CapabilityRegistry capabilities;
    private final LiveModuleRegistry modules;

    public KernelRuntime(KernelConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        store = new SQLiteWorkStore(configuration.workDatabase());
        capabilities = new CapabilityRegistry(
                new ResourceCoordinator(configuration.resourceCapacity()));
        execution = new KernelExecutionService(capabilities, store,
                configuration.resultRetention());
        modules = new LiveModuleRegistry(configuration.coreModule());
    }
    public CapabilityRegistry capabilities() { return capabilities; }
    public LiveModuleRegistry modules() { return modules; }
    public KernelExecutionService execution() { return execution; }
    @Override public void close() { execution.close(); }
}
