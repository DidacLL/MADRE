package io.github.didacll.madre.runtime;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.directory.ReachableOperation;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentContext;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Installed semantic environment that is deliberately outside the inference Kernel. */
public final class MadreRuntime {
    private final RuntimeModuleRegistry modules;
    private final RuntimeInferenceService inference;
    private final Path stateDirectory;

    public MadreRuntime(RuntimeModuleRegistry modules, RuntimeInferenceService inference,
            Path stateDirectory) {
        this.modules = Objects.requireNonNull(modules, "modules");
        this.inference = Objects.requireNonNull(inference, "inference");
        this.stateDirectory = Objects.requireNonNull(stateDirectory, "stateDirectory")
                .toAbsolutePath().normalize();
    }

    public RuntimeModuleRegistry modules() { return modules; }

    /** Installs one ordinary SDK Module using services that remain routed through this runtime. */
    public void install(ModuleProvider provider) {
        ModuleProvider source = Objects.requireNonNull(provider, "provider");
        ModuleDirectory directory = new ModuleDirectory() {
            private ModuleDirectory live() { return modules.directoryFor(source.moduleId()); }
            @Override public List<ReachableModule> reachable(ReachabilityQuery query) {
                return live().reachable(query);
            }
            @Override public List<ReachableOperation> reachableOperations(
                    io.github.didacll.madre.algebra.Sensitivity sensitivity) {
                return live().reachableOperations(sensitivity);
            }
        };
        ModuleInvoker invoker = new ModuleInvoker() {
            @Override public <I, O> CompletionStage<Material<O>> invoke(
                    OperationCall<I, O> call) {
                return MadreRuntime.this.invoke(call);
            }
        };
        Path moduleState = stateDirectory.resolve("modules").resolve(source.moduleId().value());
        modules.install(source.create(new ModuleContext(directory, invoker, moduleState)).instance());
    }

    public <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
        return invoke(call, Optional.empty());
    }

    public <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call,
            Optional<AgentId> explicitAgent) {
        OperationCall<I, O> request = Objects.requireNonNull(call, "call");
        Agent actor = modules.resolveAgent(request.operation().id(),
                Objects.requireNonNull(explicitAgent, "explicitAgent"));
        return invokeAs(actor, modules.typedBinding(request), request);
    }

    private <I, O> CompletionStage<Material<O>> invokeAs(Agent actor,
            OperationBinding<I, O> operation, OperationCall<I, O> call) {
        ModuleInvoker actorBoundInvoker = new ModuleInvoker() {
            @Override
            public <A, B> CompletionStage<Material<B>> invoke(OperationCall<A, B> nested) {
                return invokeAs(actor, modules.typedBinding(nested), nested);
            }
        };
        AgentContext context = new AgentContext(actor, inference.forAgent(actor.id()),
                modules.directoryFor(actor.id().moduleId()), actorBoundInvoker,
                stateDirectory.resolve("agents").resolve(actor.id().moduleId().value())
                        .resolve(actor.id().name()));
        return actor.execute(context, operation, call);
    }
}
