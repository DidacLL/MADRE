package io.github.didacll.madre.sdk.experimental;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.SkillDefinition;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Experimental typed authoring helper for incrementally assembling the stable
 * {@link ModuleDefinition} value.
 *
 * <p>This helper is intentionally only construction syntax. It does not introduce an alternate
 * Module model or hide Material, Operation, Privacy, Sensitivity, Integrity, Risk or Autonomy
 * declarations. Workflows remain Agent-owned exactly as they are in the stable SDK.</p>
 */
public final class ModuleDefinitionBuilder {
    private final ModuleId id;
    private final String version;
    private final String purpose;
    private final Map<MaterialTypeId, MaterialType<?>> materialTypes = new LinkedHashMap<>();
    private final Set<MaterialTypeId> publicMaterialReferences = new LinkedHashSet<>();
    private final Map<AgentId, AgentDefinition> agents = new LinkedHashMap<>();
    private final Map<SkillId, SkillDefinition> skills = new LinkedHashMap<>();
    private final Map<OperationId, OperationDefinition<?, ?>> operations = new LinkedHashMap<>();

    private ModuleDefinitionBuilder(ModuleId id, String version, String purpose) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = requireText(version, "version");
        this.purpose = requireText(purpose, "purpose");
    }

    public static ModuleDefinitionBuilder module(ModuleId id, String version, String purpose) {
        return new ModuleDefinitionBuilder(id, version, purpose);
    }

    public ModuleDefinitionBuilder materialType(MaterialType<?> materialType) {
        MaterialType<?> value = Objects.requireNonNull(materialType, "materialType");
        putUnique(materialTypes, value.id(), value, "MaterialType");
        return this;
    }

    public ModuleDefinitionBuilder publicMaterialReference(MaterialType<?> materialType) {
        return publicMaterialReference(Objects.requireNonNull(materialType, "materialType").id());
    }

    public ModuleDefinitionBuilder publicMaterialReference(MaterialTypeId materialTypeId) {
        MaterialTypeId value = Objects.requireNonNull(materialTypeId, "materialTypeId");
        if (!publicMaterialReferences.add(value)) {
            throw new IllegalArgumentException("duplicate public Material reference: " + value);
        }
        return this;
    }

    public ModuleDefinitionBuilder agent(AgentDefinition agent) {
        AgentDefinition value = Objects.requireNonNull(agent, "agent");
        putUnique(agents, value.id(), value, "Agent");
        return this;
    }

    public ModuleDefinitionBuilder skill(SkillDefinition skill) {
        SkillDefinition value = Objects.requireNonNull(skill, "skill");
        putUnique(skills, value.id(), value, "Skill");
        return this;
    }

    public ModuleDefinitionBuilder operation(OperationDefinition<?, ?> operation) {
        OperationDefinition<?, ?> value = Objects.requireNonNull(operation, "operation");
        putUnique(operations, value.id(), value, "Operation");
        return this;
    }

    /** Creates the existing stable SDK value and delegates all domain invariants to it. */
    public ModuleDefinition build() {
        return new ModuleDefinition(id, version, purpose, materialTypes, publicMaterialReferences,
                agents, skills, operations);
    }

    private static String requireText(String value, String name) {
        String exact = Objects.requireNonNull(value, name).trim();
        if (exact.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return exact;
    }

    private static <K, V> void putUnique(Map<K, V> values, K key, V value, String kind) {
        if (values.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("duplicate " + kind + " identity: " + key);
        }
    }
}
