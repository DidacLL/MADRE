package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical immutable portable declaration of one owner-installed Module. */
public final class ModuleDefinition {
    private final ModuleId id;
    private final String version;
    private final String purpose;
    private final Map<MaterialTypeId, MaterialType<?>> materialTypes;
    private final Set<MaterialTypeId> publicMaterialReferences;
    private final Map<AgentId, AgentDefinition> agents;
    private final Map<SkillId, SkillDefinition> skills;
    private final Map<OperationId, OperationDefinition> operations;

    public ModuleDefinition(ModuleId id, String version, String purpose,
            Map<MaterialTypeId, MaterialType<?>> materialTypes,
            Set<MaterialTypeId> publicMaterialReferences,
            Map<AgentId, AgentDefinition> agents,
            Map<SkillId, SkillDefinition> skills,
            Map<OperationId, OperationDefinition> operations) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = requireText(version, "version");
        this.purpose = requireText(purpose, "purpose");
        this.materialTypes = Map.copyOf(materialTypes);
        this.publicMaterialReferences = Set.copyOf(publicMaterialReferences);
        this.agents = Map.copyOf(agents);
        this.skills = Map.copyOf(skills);
        this.operations = Map.copyOf(operations);
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
        boolean owned = materialTypes.keySet().stream().allMatch(key -> key.moduleId().equals(id))
                && agents.keySet().stream().allMatch(key -> key.moduleId().equals(id))
                && skills.keySet().stream().allMatch(key -> key.moduleId().equals(id))
                && operations.keySet().stream().allMatch(key -> key.moduleId().equals(id));
        if (!owned) {
            throw new IllegalArgumentException("all canonical declarations must be owned by the Module");
        }
        if (publicMaterialReferences.stream().anyMatch(reference -> reference.moduleId().equals(id))) {
            throw new IllegalArgumentException(
                    "a public Material reference cannot duplicate an owned declaration");
        }
    }

    private void validateReferences() {
        for (OperationDefinition operation : operations.values()) {
            if (!operation.acceptedMaterial().keySet().stream()
                    .allMatch(type -> materialTypes.containsKey(type)
                            || publicMaterialReferences.contains(type))
                    || !materialTypes.keySet().containsAll(operation.producedMaterial().keySet())) {
                throw new IllegalArgumentException(
                        "Operation contains an unresolved Material type reference: " + operation.id());
            }
            if (!operation.effectProfiles().keySet().stream()
                    .allMatch(key -> key.operationId().equals(operation.id()))) {
                throw new IllegalArgumentException(
                        "Operation contains a foreign EffectProfile: " + operation.id());
            }
        }
        for (AgentDefinition agent : agents.values()) {
            if (!skills.keySet().containsAll(agent.skills())
                    || !operations.keySet().containsAll(agent.operations())
                    || agent.workflows().values().stream()
                            .flatMap(workflow -> workflow.operations().stream())
                            .anyMatch(operation -> !operations.containsKey(operation))) {
                throw new IllegalArgumentException(
                        "Agent contains an unresolved reference: " + agent.id());
            }
        }
    }

    public ModuleId id() { return id; }
    public String version() { return version; }
    public String purpose() { return purpose; }
    public Map<MaterialTypeId, MaterialType<?>> materialTypes() { return materialTypes; }
    public Set<MaterialTypeId> publicMaterialReferences() { return publicMaterialReferences; }
    public Map<AgentId, AgentDefinition> agents() { return agents; }
    public Map<SkillId, SkillDefinition> skills() { return skills; }
    public Map<OperationId, OperationDefinition> operations() { return operations; }

    /** Derives exact current sensitivity from reachable Material and public promised outputs. */
    public Optional<Sensitivity> effectiveSensitivity(
            Collection<? extends Material<?>> reachableMaterial) {
        Optional<Sensitivity> value = operations.values().stream()
                .filter(operation -> operation.visibility() == OperationVisibility.PUBLIC)
                .flatMap(operation -> operation.producedMaterial().values().stream())
                .reduce(Sensitivity::combine);
        for (Material<?> material : reachableMaterial) {
            if (!material.id().moduleId().equals(id)
                    || !materialTypes.containsKey(material.type().id())) {
                throw new IllegalArgumentException("reachable Material must belong to this Module");
            }
            value = Optional.of(value.map(current -> current.combine(material.sensitivity()))
                    .orElse(material.sensitivity()));
        }
        return value;
    }
}
