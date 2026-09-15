package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java execution-side abstraction for one Module-owned semantic actor.
 *
 * <p>The interface deliberately defines no universal loop, memory model or execution context.
 * Implementations are ordinary Module objects and may keep whatever private state and algorithms
 * their domain requires. MADRE only asks them for the semantic facts needed to derive the
 * portable {@link AgentDefinition} used by discovery and adapters.</p>
 */
public interface Agent {
    AgentId id();
    String purpose();
    Integrity integrity();

    default Collection<? extends SkillDefinition> skills() { return Set.of(); }
    default Collection<? extends WorkflowDefinition> workflows() { return Set.of(); }

    /** Operation repertoire implemented or coordinated by this Agent. */
    Set<OperationId> operations();

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
