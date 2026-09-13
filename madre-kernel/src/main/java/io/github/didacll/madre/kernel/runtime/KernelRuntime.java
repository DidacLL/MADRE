package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.module.LiveModuleRegistry;
import io.github.didacll.madre.kernel.transport.LoopbackKernelTransport;
import java.util.Objects;

/** Cohesive owner of one Kernel process and its physical/runtime boundaries. */
public final class KernelRuntime implements AutoCloseable {
    private final SQLiteWorkStore store;
    private final KernelExecutionService execution;
    private final CapabilityRegistry capabilities;
    private final LiveModuleRegistry modules;
    private final LoopbackKernelTransport transport;

    public KernelRuntime(KernelConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        store = new SQLiteWorkStore(configuration.workDatabase());
        capabilities = new CapabilityRegistry(new ResourceCoordinator(configuration.resourceCapacity()));
        execution = new KernelExecutionService(capabilities, store, configuration.resultRetention());
        modules = new LiveModuleRegistry(configuration.coreModule());
        transport = new LoopbackKernelTransport(configuration.bindAddress(), modules, execution);
    }
    public CapabilityRegistry capabilities() { return capabilities; }
    public LiveModuleRegistry modules() { return modules; }
    public KernelExecutionService execution() { return execution; }
    public LoopbackKernelTransport transport() { return transport; }
    @Override public void close() { execution.close(); }
}
