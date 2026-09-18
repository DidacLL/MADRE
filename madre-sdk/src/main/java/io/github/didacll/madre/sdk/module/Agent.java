package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletionStage;

/**
 * Java execution-side abstraction for one Module-owned semantic actor.
 *
 * <p>The interface deliberately defines no universal loop or memory model.
 * Implementations are ordinary Module objects and may keep whatever private state and algorithms
 * their domain requires. MADRE only asks them for the semantic facts needed to derive the
 * portable {@link AgentDefinition} used by discovery and adapters.</p>
 *
 * <p>An Agent that has not established a stronger causal-integrity claim is still a valid MADRE
 * Agent. The Java authoring surface therefore defaults to the lowest ordinary Integrity rather
 * than forcing experimental or generated code to invent assurance it does not have. Portable
 * descriptions remain explicit because {@link #definition()} materializes the resulting value.</p>
 */
public interface Agent {
    AgentId id();
    String purpose();

    /** Conservative default for an Agent whose causal integrity has not been established. */
    default Integrity integrity() { return Integrity.I1; }

    default Collection<? extends SkillDefinition> skills() { return Set.of(); }
    default Collection<? extends WorkflowDefinition> workflows() { return Set.of(); }

    /** Operation repertoire implemented or coordinated by this Agent. */
    Set<OperationId> operations();

    /**
     * Executes one bounded Operation as this semantic actor. Runtime always enters Module
     * behavior through this method, including when it selected the installation's fallback
     * CORE Agent. The Agent may execute a foreign Module's exposed Operation; a Module-local
     * Operation must belong to this Agent's declared repertoire.
     */
    default <I, O> CompletionStage<Material<O>> execute(AgentContext context,
            OperationBinding<I, O> operation, OperationCall<I, O> call) {
        AgentContext execution = Objects.requireNonNull(context, "context");
        execution.requireActor(this);
        return Objects.requireNonNull(operation, "operation").invoke(this,
                Objects.requireNonNull(call, "call"));
    }

    /** Executes behavior owned by this Agent rather than interpreting a workflow description. */
    default <I, O> CompletionStage<O> execute(AgentContext context,
            Workflow<I, O> workflow, I input) {
        AgentContext execution = Objects.requireNonNull(context, "context");
        execution.requireActor(this);
        Workflow<I, O> behavior = Objects.requireNonNull(workflow, "workflow");
        if (!behavior.id().agentId().equals(id())) {
            throw new IllegalArgumentException("Workflow is not owned by acting Agent");
        }
        return Objects.requireNonNull(behavior.execute(execution, input),
                "Workflow result stage");
    }

    /** Derives the portable semantic description; it is not a second authoring source. */
    default AgentDefinition definition() {
        Map<WorkflowId, WorkflowDefinition> workflowDefinitions = new LinkedHashMap<>();
        for (WorkflowDefinition workflow : workflows()) {
            WorkflowDefinition value = Objects.requireNonNull(workflow, "workflow");
            if (workflowDefinitions.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("duplicate Workflow identity: " + value.id());
            }
        }
        Set<SkillId> skillIds = skills().stream()
                .map(skill -> Objects.requireNonNull(skill, "skill").id())
                .collect(Collectors.toUnmodifiableSet());
        return new AgentDefinition(id(), purpose(), integrity(), skillIds, workflowDefinitions,
                Set.copyOf(operations()));
    }
}
