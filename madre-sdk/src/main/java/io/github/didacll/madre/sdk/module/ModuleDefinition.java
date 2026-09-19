package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical immutable language-neutral declaration of one owner-installed Module. */
public final class ModuleDefinition {
    private final ModuleId id;
    private final String version;
    private final String purpose;
    private final Map<MaterialTypeId, MaterialTypeDefinition> materialTypes;
    private final Map<AgentId, AgentDefinition> agents;
    private final Map<SkillId, SkillDefinition> skills;
    private final Map<OperationId, OperationDefinition> operations;
    private final Set<OperationId> exposedOperations;

    public ModuleDefinition(ModuleId id, String version, String purpose,
            Map<MaterialTypeId, MaterialTypeDefinition> materialTypes,
            Map<AgentId, AgentDefinition> agents,
            Map<SkillId, SkillDefinition> skills,
            Map<OperationId, OperationDefinition> operations) {
        this(id, version, purpose, materialTypes, agents, skills,
                operations, Set.of());
    }

    public ModuleDefinition(ModuleId id, String version, String purpose,
            Map<MaterialTypeId, MaterialTypeDefinition> materialTypes,
            Map<AgentId, AgentDefinition> agents,
            Map<SkillId, SkillDefinition> skills,
            Map<OperationId, OperationDefinition> operations,
            Set<OperationId> exposedOperations) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = requireText(version, "version");
        this.purpose = requireText(purpose, "purpose");
        this.materialTypes = Map.copyOf(materialTypes);
        this.agents = Map.copyOf(agents);
        this.skills = Map.copyOf(skills);
        this.operations = Map.copyOf(operations);
        this.exposedOperations = Set.copyOf(exposedOperations);
        validateCanonicalKeys();
        validateOwnership();
        validateReferences();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.strip();
    }

    private void validateCanonicalKeys() {
        if (!materialTypes.entrySet().stream().allMatch(entry -> entry.getKey().equals(entry.getValue().id()))
                || !agents.entrySet().stream().allMatch(entry -> entry.getKey().equals(entry.getValue().id()))
                || !skills.entrySet().stream().allMatch(entry -> entry.getKey().equals(entry.getValue().id()))
                || !operations.entrySet().stream().allMatch(entry -> entry.getKey().equals(entry.getValue().id()))) {
            throw new IllegalArgumentException("canonical declaration map keys must match their values");
        }
    }

    private void validateOwnership() {
        boolean owned = agents.keySet().stream().allMatch(key -> key.moduleId().equals(id))
                && skills.keySet().stream().allMatch(key -> key.moduleId().equals(id))
                && operations.keySet().stream().allMatch(key -> key.moduleId().equals(id));
        if (!owned) {
            throw new IllegalArgumentException("all canonical declarations must be owned by the Module");
        }
        if (!operations.keySet().containsAll(exposedOperations)) {
            throw new IllegalArgumentException(
                    "Module exposure may reference only Operations owned by this Module");
        }
    }

    private void validateReferences() {
        for (OperationDefinition operation : operations.values()) {
            if (!operation.effectProfiles().keySet().stream()
                    .allMatch(key -> key.operationId().equals(operation.id()))) {
                throw new IllegalArgumentException(
                        "Operation contains a foreign EffectProfile: " + operation.id());
            }
        }
        for (AgentDefinition agent : agents.values()) {
            if (!skills.keySet().containsAll(agent.skills())
                    || !operations.keySet().containsAll(agent.operations())) {
                throw new IllegalArgumentException(
                        "Agent contains an unresolved reference: " + agent.id());
            }
        }
    }

    public ModuleId id() { return id; }
    public String version() { return version; }
    public String purpose() { return purpose; }
    public Map<MaterialTypeId, MaterialTypeDefinition> materialTypes() { return materialTypes; }
    public Map<AgentId, AgentDefinition> agents() { return agents; }
    public Map<SkillId, SkillDefinition> skills() { return skills; }
    public Map<OperationId, OperationDefinition> operations() { return operations; }
    public Set<OperationId> exposedOperations() { return exposedOperations; }
}
