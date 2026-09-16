package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Java execution-side abstraction for one owner-installed semantic Module.
 *
 * <p>Implementations are ordinary objects and remain the Java author's source of truth. The
 * portable {@link ModuleDefinition} consumed by discovery, JSON/adapters and runtime contract
 * validation is derived from these objects rather than maintained as a parallel graph.</p>
 */
public interface Module {
    ModuleId id();
    String version();
    String purpose();

    /** Java payload bindings owned by this executable Module. */
    Collection<? extends MaterialType<?>> materialTypes();

    /** Foreign nominal Material contracts structurally referenced by this Module. */
    default Set<MaterialTypeId> foreignMaterialReferences() { return Set.of(); }
    default Collection<? extends Agent> agents() { return Set.of(); }

    /**
     * Operation identities this Module intentionally exposes to other installed Modules. Exposure
     * is a Module-interface fact; it does not alter Operation ontology or Material Privacy.
     */
    default Set<OperationId> exposedOperations() { return Set.of(); }

    /**
     * Module-provided semantic Skills. By default this is the canonical union advertised by its
     * Agents; a Module may override when it deliberately exposes reusable Skills not currently
     * attached to an Agent.
     */
    default Collection<? extends SkillDefinition> skills() {
        Map<SkillId, SkillDefinition> values = new LinkedHashMap<>();
        for (Agent agent : agents()) {
            for (SkillDefinition skill : Objects.requireNonNull(agent, "agent").skills()) {
                SkillDefinition value = Objects.requireNonNull(skill, "skill");
                SkillDefinition previous = values.putIfAbsent(value.id(), value);
                if (previous != null && !previous.equals(value)) {
                    throw new IllegalArgumentException(
                            "conflicting Skill declaration: " + value.id());
                }
            }
        }
        return Set.copyOf(values.values());
    }

    Collection<? extends OperationBinding<?, ?>> operations();

    /** Derives the portable semantic/security contract from executable Java objects. */
    default ModuleDefinition definition() {
        Map<MaterialTypeId, MaterialTypeDefinition> materialDefinitions = new LinkedHashMap<>();
        for (MaterialType<?> materialType : materialTypes()) {
            MaterialType<?> value = Objects.requireNonNull(materialType, "materialType");
            if (materialDefinitions.putIfAbsent(value.id(), value.definition()) != null) {
                throw new IllegalArgumentException(
                        "duplicate MaterialType identity: " + value.id());
            }
        }

        Map<OperationId, OperationDefinition> operationDefinitions = new LinkedHashMap<>();
        for (OperationBinding<?, ?> binding : operations()) {
            OperationBinding<?, ?> value = Objects.requireNonNull(binding, "operation");
            OperationDefinition definition = value.definition();
            if (operationDefinitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException(
                        "duplicate Operation identity: " + definition.id());
            }
        }

        Map<AgentId, AgentDefinition> agentDefinitions = new LinkedHashMap<>();
        for (Agent agent : agents()) {
            AgentDefinition definition = Objects.requireNonNull(agent, "agent").definition();
            if (agentDefinitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate Agent identity: " + definition.id());
            }
        }

        Map<SkillId, SkillDefinition> skillDefinitions = new LinkedHashMap<>();
        for (SkillDefinition skill : skills()) {
            SkillDefinition value = Objects.requireNonNull(skill, "skill");
            if (skillDefinitions.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("duplicate Skill identity: " + value.id());
            }
        }

        return new ModuleDefinition(id(), version(), purpose(), materialDefinitions,
                Set.copyOf(new LinkedHashSet<>(foreignMaterialReferences())), agentDefinitions,
                skillDefinitions, operationDefinitions,
                Set.copyOf(new LinkedHashSet<>(exposedOperations())));
    }

    /** Produces the validated runtime assembly registered by MADRE. */
    default ModuleInstance instance() {
        return ModuleInstance.from(this);
    }
}
