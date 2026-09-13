package io.github.didacll.madre.kernel.transport;

import io.github.didacll.madre.kernel.module.LiveModuleRegistry;
import io.github.didacll.madre.kernel.runtime.KernelExecutionService;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** In-process local transport exposing Kernel ports while enforcing loopback deployment. */
public final class LoopbackKernelTransport {
    private final InetAddress binding;
    private final LiveModuleRegistry modules;
    private final KernelExecutionService execution;

    public LoopbackKernelTransport(InetAddress binding, LiveModuleRegistry modules, KernelExecutionService execution) {
        this.binding = Objects.requireNonNull(binding, "binding");
        if (!binding.isLoopbackAddress()) throw new IllegalArgumentException("Kernel local transport must bind to loopback");
        this.modules = Objects.requireNonNull(modules, "modules"); this.execution = Objects.requireNonNull(execution, "execution");
    }

    public Health health() { return new Health(binding.getHostAddress(), true); }
    public ModuleRegistration.Registration register(ModuleDefinition definition) {
        return modules.register(definition);
    }
    public List<ReachableModule> reachable(ReachabilityQuery query) { return modules.reachable(query); }
    public Optional<ModuleId> core() { return modules.resolvedCore(); }
    public <C, R> CompletionStage<R> execute(WorkRequest<C, R> request) { return execution.execute(request); }
    public <C, R> WorkId submit(WorkRequest<C, R> request) { return execution.submit(request); }
    public Optional<WorkStatus> inspect(WorkId id) { return execution.inspect(id); }
    public boolean cancel(WorkId id) { return execution.cancel(id); }
    public <R> Optional<R> collect(WorkId id, Class<R> resultType) { return execution.collect(id, resultType); }
    public boolean acknowledge(WorkId id) { return execution.acknowledge(id); }

    public record Health(String binding, boolean ready) { }
}
