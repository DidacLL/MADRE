package io.github.didacll.madre.runtime;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.directory.ReachableOperation;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentContext;
import io.github.didacll.madre.sdk.module.ConversationMessage;
import io.github.didacll.madre.sdk.module.ConversationalAgent;
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
        Path moduleState = stateDirectory.resolve("modules").resolve(source.moduleId().value());
        modules.install(source.create(new ModuleContext(directory, moduleState)).instance());
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

    /** Enters the configured CORE conversational Agent without exposing Module-private protocol. */
    public CompletionStage<ConversationMessage> respond(ConversationMessage message) {
        AgentId id = modules.defaultCoreAgentId().orElseThrow(() ->
                new AgentResolutionException("No default CORE Agent is configured"));
        Agent actor = modules.requireAgent(id);
        if (!(actor instanceof ConversationalAgent conversational)) {
            throw new AgentResolutionException(
                    "The default CORE Agent is not conversational: " + id);
        }
        return conversational.respond(contextFor(actor, actor.id().moduleId()),
                Objects.requireNonNull(message, "message"));
    }

    private <I, O> CompletionStage<Material<O>> invokeFrom(Agent actor, ModuleId caller,
            OperationCall<I, O> call) {
        OperationCall<I, O> request = Objects.requireNonNull(call, "call");
        ModuleId target = request.operation().id().moduleId();
        if (!target.equals(Objects.requireNonNull(caller, "caller"))
                && !modules.requireModule(target).definition().exposedOperations()
                        .contains(request.operation().id())) {
            throw new IllegalArgumentException(
                    "Operation is not exposed to another Module: " + request.operation().id());
        }
        return invokeAs(actor, modules.typedBinding(request), request);
    }

    private <I, O> CompletionStage<Material<O>> invokeAs(Agent actor,
            OperationBinding<I, O> operation, OperationCall<I, O> call) {
        return actor.execute(contextFor(actor, operation.definition().id().moduleId()),
                operation, call);
    }

    private AgentContext contextFor(Agent actor, ModuleId executingModule) {
        ModuleInvoker actorBoundInvoker = new ModuleInvoker() {
            @Override
            public <A, B> CompletionStage<Material<B>> invoke(OperationCall<A, B> nested) {
                return invokeFrom(actor, executingModule, nested);
            }
        };
        return new AgentContext(actor, inference.forAgent(actor.id()),
                modules.directoryFor(actor.id().moduleId()), actorBoundInvoker,
                stateDirectory.resolve("agents").resolve(actor.id().moduleId().value())
                        .resolve(actor.id().name()));
    }
}
