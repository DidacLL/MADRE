package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OwnerInteractionAgent;
import io.github.didacll.madre.sdk.module.OwnerMessage;
import io.github.didacll.madre.sdk.module.SkillDefinition;
import io.github.didacll.madre.sdk.module.WorkflowDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.operation.OwnerInteractionInvoker;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Production assembly of the shipped owner-interaction Module with ordinary caller-bound Module
 * composition. The delegate keeps the accepted CORE conversation/knowledge behavior unchanged;
 * the wrapper adds no authority beyond the ModuleContext supplied to every installed Module.
 */
final class OwnerInteractionCompositionModule implements Module {
    private static final OperationId COORDINATE = new OperationId(
            OwnerInteractionModule.ID, "coordinate-installed-module");
    private static final OperationDefinition COORDINATION_OPERATION = new OperationDefinition(
            COORDINATE,
            "Let the CORE Agent semantically select and coordinate one reachable installed Module Operation",
            Map.of(OwnerInteractionModule.OWNER_PROMPT.id(), Privacy.SECRET),
            Map.of(OwnerInteractionModule.IMMEDIATE_ANSWER.id(), Sensitivity.S5), Map.of());
    private static final String NO_COMPOSITION = "__MADRE_NO_MODULE_COMPOSITION__";

    private final OwnerInteractionModule delegate;
    private final OwnerInteractionAgent delegateAgent;
    private final OwnerInteractionAgent interaction;
    private final OperationBinding<String, String> coordinationBinding;

    OwnerInteractionCompositionModule(ModuleContext context, Path stateFile,
            OwnerInteractionSettings settings) {
        java.util.Objects.requireNonNull(context, "context");
        delegate = new OwnerInteractionModule(context.reasoning(), stateFile, settings);
        delegateAgent = delegate.agents().stream()
                .filter(OwnerInteractionAgent.class::isInstance)
                .map(OwnerInteractionAgent.class::cast)
                .findFirst().orElseThrow(() ->
                        new IllegalStateException("owner-interaction Module has no semantic Agent"));
        ModuleCompositionCoordinator coordinator = new ModuleCompositionCoordinator(
                context.reasoning(), context.directory(), context.invoker(), settings,
                COORDINATION_OPERATION);
        Operation<String, String> coordination = Operation.of(call ->
                coordinator.coordinate(call.input(), delegateAgent.integrity()).thenApply(result ->
                        result.orElseGet(() -> noComposition(call.input().sensitivity()))));
        coordinationBinding = OperationBinding.ownerInteractionOperation(
                COORDINATION_OPERATION, coordination);
        interaction = new ComposingAgent();
    }

    @Override public ModuleId id() { return delegate.id(); }
    @Override public String version() { return "1.6.0"; }
    @Override public String purpose() { return delegate.purpose(); }
    @Override public Collection<? extends MaterialType<?>> materialTypes() {
        return delegate.materialTypes();
    }
    @Override public Set<MaterialTypeId> foreignMaterialReferences() {
        return delegate.foreignMaterialReferences();
    }
    @Override public Collection<? extends Agent> agents() { return List.of(interaction); }
    @Override public Set<OperationId> exposedOperations() { return delegate.exposedOperations(); }
    @Override public Collection<? extends OperationBinding<?, ?>> operations() {
        List<OperationBinding<?, ?>> operations = new ArrayList<>(delegate.operations());
        operations.add(coordinationBinding);
        return List.copyOf(operations);
    }

    private static Material<String> noComposition(Sensitivity sensitivity) {
        return new Material<>(new MaterialId(OwnerInteractionModule.ID,
                "no-module-composition-" + UUID.randomUUID()),
                OwnerInteractionModule.IMMEDIATE_ANSWER, NO_COMPOSITION, sensitivity);
    }

    private final class ComposingAgent implements OwnerInteractionAgent {
        @Override public AgentId id() { return delegateAgent.id(); }
        @Override public String purpose() { return delegateAgent.purpose(); }
        @Override public Integrity integrity() { return delegateAgent.integrity(); }
        @Override public Collection<? extends SkillDefinition> skills() {
            return delegateAgent.skills();
        }
        @Override public Collection<? extends WorkflowDefinition> workflows() {
            return delegateAgent.workflows();
        }
        @Override public Set<OperationId> operations() {
            Set<OperationId> operations = new LinkedHashSet<>(delegateAgent.operations());
            operations.add(COORDINATE);
            return Set.copyOf(operations);
        }

        @Override public CompletionStage<OwnerMessage> respond(OwnerInteractionInvoker invoker,
                String ownerText, Sensitivity sensitivity) {
            java.util.Objects.requireNonNull(invoker, "invoker");
            if (ownerText == null || ownerText.isBlank()) {
                throw new IllegalArgumentException("owner text must not be blank");
            }
            if (OwnerKnowledgeIntent.parse(ownerText).isPresent()) {
                return delegateAgent.respond(invoker, ownerText, sensitivity);
            }
            Material<String> prompt = delegate.ownerPrompt(ownerText, sensitivity);
            OperationCall<String, String> call = OperationCall.withoutEffect(
                    COORDINATION_OPERATION, prompt);
            return invoker.invokeOwnerInteraction(call).thenCompose(answer -> {
                if (answer.payload().equals(NO_COMPOSITION)) {
                    return delegateAgent.respond(invoker, ownerText, sensitivity);
                }
                return CompletableFuture.completedFuture(
                        new OwnerMessage(answer.payload(), answer.sensitivity()));
            });
        }

        @Override public CompletionStage<List<OwnerMessage>> followUps(
                OwnerInteractionInvoker invoker) {
            return delegateAgent.followUps(invoker);
        }
    }
}
