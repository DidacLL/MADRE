package io.github.didacll.madre.runtime;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentContext;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
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
