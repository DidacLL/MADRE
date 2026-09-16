package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical immutable language-neutral declaration of one owner-installed Module. */
public final class ModuleDefinition {
    private final ModuleId id;
    private final String version;
    private final String purpose;
    private final Map<MaterialTypeId, MaterialTypeDefinition> materialTypes;
    private final Set<MaterialTypeId> publicMaterialReferences;
    private final Map<AgentId, AgentDefinition> agents;
    private final Map<SkillId, SkillDefinition> skills;
    private final Map<OperationId, OperationDefinition> operations;
    private final Set<OperationId> exposedOperations;

    public ModuleDefinition(ModuleId id, String version, String purpose,
            Map<MaterialTypeId, MaterialTypeDefinition> materialTypes,
            Set<MaterialTypeId> publicMaterialReferences,
            Map<AgentId, AgentDefinition> agents,
            Map<SkillId, SkillDefinition> skills,
            Map<OperationId, OperationDefinition> operations) {
        this(id, version, purpose, materialTypes, publicMaterialReferences, agents, skills,
                operations, Set.of());
    }

    public ModuleDefinition(ModuleId id, String version, String purpose,
            Map<MaterialTypeId, MaterialTypeDefinition> materialTypes,
            Set<MaterialTypeId> publicMaterialReferences,
            Map<AgentId, AgentDefinition> agents,
            Map<SkillId, SkillDefinition> skills,
            Map<OperationId, OperationDefinition> operations,
            Set<OperationId> exposedOperations) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = requireText(version, "version");
        this.purpose = requireText(purpose, "purpose");
        this.materialTypes = Map.copyOf(materialTypes);
        this.publicMaterialReferences = Set.copyOf(publicMaterialReferences);
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
        if (!operations.keySet().containsAll(exposedOperations)) {
            throw new IllegalArgumentException(
                    "Module exposure may reference only Operations owned by this Module");
        }
    }

    private void validateReferences() {
        for (OperationDefinition operation : operations.values()) {
            if (!operation.acceptedMaterial().keySet().stream()
                    .allMatch(this::resolvesMaterialType)
                    || !operation.producedMaterial().keySet().stream()
                            .allMatch(this::resolvesMaterialType)) {
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

    private boolean resolvesMaterialType(MaterialTypeId type) {
        return materialTypes.containsKey(type) || publicMaterialReferences.contains(type);
    }

    public ModuleId id() { return id; }
    public String version() { return version; }
    public String purpose() { return purpose; }
    public Map<MaterialTypeId, MaterialTypeDefinition> materialTypes() { return materialTypes; }
    public Set<MaterialTypeId> publicMaterialReferences() { return publicMaterialReferences; }
    public Map<AgentId, AgentDefinition> agents() { return agents; }
    public Map<SkillId, SkillDefinition> skills() { return skills; }
    public Map<OperationId, OperationDefinition> operations() { return operations; }
    public Set<OperationId> exposedOperations() { return exposedOperations; }

    /** Derives exact current sensitivity from owned reachable Material and exposed output promises. */
    public Optional<Sensitivity> effectiveSensitivity(
            Collection<? extends Material<?>> reachableMaterial) {
        Optional<Sensitivity> value = exposedOperations.stream()
                .map(operations::get)
                .flatMap(operation -> operation.producedMaterial().values().stream())
                .reduce(Sensitivity::combine);
        for (Material<?> material : reachableMaterial) {
            if (!material.id().moduleId().equals(id)) {
                throw new IllegalArgumentException("reachable Material value must belong to this Module");
            }
            MaterialTypeDefinition declared = materialTypes.get(material.type().id());
            if (declared != null) {
                if (!declared.equals(material.type().definition())) {
                    throw new IllegalArgumentException(
                            "reachable Material does not match its owned nominal contract");
                }
            } else if (!publicMaterialReferences.contains(material.type().id())) {
                throw new IllegalArgumentException(
                        "reachable Material uses an undeclared foreign nominal contract");
            }
            value = Optional.of(value.map(current -> current.combine(material.sensitivity()))
                    .orElse(material.sensitivity()));
        }
        return value;
    }
}
