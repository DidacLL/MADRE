package io.github.didacll.madre.sdk.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.SkillId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.module.SkillDefinition;
import io.github.didacll.madre.sdk.module.WorkflowDefinition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Explicit version-one JSON mapping for immutable Module declarations. */
public final class ModuleDefinitionJsonCodec {
    private static final int FORMAT_VERSION = 1;
    private final ObjectMapper mapper;
    private final MaterialTypeResolver materialTypes;

    public ModuleDefinitionJsonCodec(MaterialTypeResolver materialTypes) {
        this.mapper = new ObjectMapper();
        this.materialTypes = java.util.Objects.requireNonNull(materialTypes, "materialTypes");
    }

    public String encode(ModuleDefinition definition) {
        ObjectNode root = mapper.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("module", definition.id().value());
        root.put("version", definition.version());
        root.put("purpose", definition.purpose());

        ArrayNode types = root.putArray("materialTypes");
        definition.materialTypes().values().stream().sorted(java.util.Comparator.comparing(t -> t.id().name()))
                .forEach(type -> { ObjectNode node = types.addObject(); node.put("name", type.id().name()); node.put("contentType", type.contentType()); });
        ArrayNode references = root.putArray("publicMaterialReferences");
        definition.publicMaterialReferences().stream().sorted(java.util.Comparator.comparing(ModuleDefinitionJsonCodec::qualified))
                .forEach(id -> references.add(qualified(id)));
        ArrayNode skills = root.putArray("skills");
        definition.skills().values().stream().sorted(java.util.Comparator.comparing(s -> s.id().name()))
                .forEach(skill -> { ObjectNode node = skills.addObject(); node.put("name", skill.id().name()); node.put("purpose", skill.purpose()); });

        Map<EffectProfileId, EffectProfile> canonicalProfiles = new LinkedHashMap<>();
        definition.operations().values().forEach(operation -> operation.effectProfiles().forEach((id, profile) -> {
            EffectProfile previous = canonicalProfiles.putIfAbsent(id, profile);
            if (previous != null && !previous.equals(profile)) throw new CodecException("conflicting EffectProfile declaration: " + id);
        }));
        ArrayNode profiles = root.putArray("effectProfiles");
        canonicalProfiles.values().stream().sorted(java.util.Comparator.comparing(p -> p.id().name())).forEach(profile -> {
            ObjectNode node = profiles.addObject();
            node.put("name", profile.id().name()); node.put("risk", profile.risk().name()); node.put("autonomy", profile.autonomy().name());
        });

        ArrayNode operations = root.putArray("operations");
        definition.operations().values().stream().sorted(java.util.Comparator.comparing(o -> o.id().name())).forEach(operation -> {
            ObjectNode node = operations.addObject();
            node.put("name", operation.id().name()); node.put("purpose", operation.purpose()); node.put("visibility", operation.visibility().name());
            writePrivacyMap(node.putArray("acceptedMaterial"), operation.acceptedMaterial());
            writeSensitivityMap(node.putArray("producedMaterial"), operation.producedMaterial());
            ArrayNode effectIds = node.putArray("effectProfiles");
            operation.effectProfiles().keySet().stream().map(EffectProfileId::name).sorted().forEach(effectIds::add);
        });

        ArrayNode workflows = root.putArray("workflows");
        definition.workflows().values().stream().sorted(java.util.Comparator.comparing(w -> w.id().name())).forEach(workflow -> {
            ObjectNode node = workflows.addObject(); node.put("name", workflow.id().name()); node.put("purpose", workflow.purpose());
            writeNames(node.putArray("skills"), workflow.skills().stream().map(SkillId::name).collect(java.util.stream.Collectors.toSet()));
            writeNames(node.putArray("operations"), workflow.operations().stream().map(OperationId::name).collect(java.util.stream.Collectors.toSet()));
        });
        ArrayNode agents = root.putArray("agents");
        definition.agents().values().stream().sorted(java.util.Comparator.comparing(a -> a.id().name())).forEach(agent -> {
            ObjectNode node = agents.addObject(); node.put("name", agent.id().name()); node.put("purpose", agent.purpose());
            writeNames(node.putArray("skills"), agent.skills().stream().map(SkillId::name).collect(java.util.stream.Collectors.toSet()));
            writeNames(node.putArray("workflows"), agent.workflows().stream().map(WorkflowId::name).collect(java.util.stream.Collectors.toSet()));
            writeNames(node.putArray("operations"), agent.operations().stream().map(OperationId::name).collect(java.util.stream.Collectors.toSet()));
        });
        try { return mapper.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new CodecException("cannot encode Module definition", exception); }
    }

    public ModuleDefinition decode(String json) {
        try {
            JsonNode parsed = mapper.readTree(json);
            ObjectNode root = object(parsed, "root");
            exactFields(root, Set.of("formatVersion", "module", "version", "purpose", "materialTypes", "publicMaterialReferences", "skills", "effectProfiles", "operations", "workflows", "agents"));
            if (requiredInt(root, "formatVersion") != FORMAT_VERSION) throw new CodecException("unsupported formatVersion");
            ModuleId module = new ModuleId(requiredText(root, "module"));
            Map<MaterialTypeId, MaterialType<?>> typeDefinitions = decodeTypes(root, module);
            Set<MaterialTypeId> references = new HashSet<>();
            for (JsonNode node : requiredArray(root, "publicMaterialReferences")) references.add(parseMaterialTypeId(requiredTextNode(node), module));
            Map<SkillId, SkillDefinition> skills = decodeSkills(root, module);
            Map<EffectProfileId, EffectProfile> profiles = decodeProfiles(root, module);
            Map<OperationId, OperationDefinition<?, ?>> operations = decodeOperations(root, module, profiles);
            Map<WorkflowId, WorkflowDefinition> workflows = decodeWorkflows(root, module);
            Map<AgentId, AgentDefinition> agents = decodeAgents(root, module);
            return new ModuleDefinition(module, requiredText(root, "version"), requiredText(root, "purpose"),
                    typeDefinitions, references, agents, skills, workflows, operations);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new CodecException("invalid Module definition JSON", exception);
        }
    }

    private Map<MaterialTypeId, MaterialType<?>> decodeTypes(ObjectNode root, ModuleId module) {
        Map<MaterialTypeId, MaterialType<?>> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "materialTypes")) {
            ObjectNode node = object(raw, "material type"); exactFields(node, Set.of("name", "contentType"));
            MaterialTypeId id = new MaterialTypeId(module, requiredText(node, "name"));
            String contentType = requiredText(node, "contentType");
            MaterialType<?> type = materialTypes.resolve(id, contentType);
            if (type == null || !type.id().equals(id) || !type.contentType().equals(contentType)) {
                throw new CodecException("unresolved or conflicting Material type: " + id);
            }
            putUnique(values, id, type);
        }
        return values;
    }

    private static Map<SkillId, SkillDefinition> decodeSkills(ObjectNode root, ModuleId module) {
        Map<SkillId, SkillDefinition> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "skills")) {
            ObjectNode node = object(raw, "skill"); exactFields(node, Set.of("name", "purpose"));
            SkillId id = new SkillId(module, requiredText(node, "name")); putUnique(values, id, new SkillDefinition(id, requiredText(node, "purpose")));
        }
        return values;
    }

    private static Map<EffectProfileId, EffectProfile> decodeProfiles(ObjectNode root, ModuleId module) {
        Map<EffectProfileId, EffectProfile> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "effectProfiles")) {
            ObjectNode node = object(raw, "effect profile"); exactFields(node, Set.of("name", "risk", "autonomy"));
            EffectProfileId id = new EffectProfileId(module, requiredText(node, "name"));
            putUnique(values, id, new EffectProfile(id, Risk.valueOf(requiredText(node, "risk")), Autonomy.valueOf(requiredText(node, "autonomy"))));
        }
        return values;
    }

    private static Map<OperationId, OperationDefinition<?, ?>> decodeOperations(ObjectNode root, ModuleId module, Map<EffectProfileId, EffectProfile> profiles) {
        Map<OperationId, OperationDefinition<?, ?>> values = new HashMap<>();
        Set<EffectProfileId> usedProfiles = new HashSet<>();
        for (JsonNode raw : requiredArray(root, "operations")) {
            ObjectNode node = object(raw, "operation");
            exactFields(node, Set.of("name", "purpose", "visibility", "acceptedMaterial", "producedMaterial", "effectProfiles"));
            OperationId id = new OperationId(module, requiredText(node, "name"));
            Map<EffectProfileId, EffectProfile> selected = new HashMap<>();
            for (JsonNode profileNode : requiredArray(node, "effectProfiles")) {
                EffectProfileId profileId = new EffectProfileId(module, requiredTextNode(profileNode));
                EffectProfile profile = profiles.get(profileId);
                if (profile == null || !usedProfiles.add(profileId)) throw new CodecException("unresolved or multiply owned EffectProfile: " + profileId);
                selected.put(profileId, profile);
            }
            OperationDefinition<Object, Object> operation = new OperationDefinition<>(id, requiredText(node, "purpose"),
                    OperationVisibility.valueOf(requiredText(node, "visibility")), decodePrivacyMap(node, module), decodeSensitivityMap(node, module), selected);
            putUnique(values, id, operation);
        }
        if (!usedProfiles.equals(profiles.keySet())) throw new CodecException("unreferenced EffectProfile declaration");
        return values;
    }

    private static Map<WorkflowId, WorkflowDefinition> decodeWorkflows(ObjectNode root, ModuleId module) {
        Map<WorkflowId, WorkflowDefinition> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "workflows")) {
            ObjectNode node = object(raw, "workflow"); exactFields(node, Set.of("name", "purpose", "skills", "operations"));
            WorkflowId id = new WorkflowId(module, requiredText(node, "name"));
            putUnique(values, id, new WorkflowDefinition(id, requiredText(node, "purpose"), skillIds(node, module), operationIds(node, module)));
        }
        return values;
    }

    private static Map<AgentId, AgentDefinition> decodeAgents(ObjectNode root, ModuleId module) {
        Map<AgentId, AgentDefinition> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "agents")) {
            ObjectNode node = object(raw, "agent"); exactFields(node, Set.of("name", "purpose", "skills", "workflows", "operations"));
            AgentId id = new AgentId(module, requiredText(node, "name"));
            Set<WorkflowId> workflowIds = new HashSet<>(); for (JsonNode name : requiredArray(node, "workflows")) workflowIds.add(new WorkflowId(module, requiredTextNode(name)));
            putUnique(values, id, new AgentDefinition(id, requiredText(node, "purpose"), skillIds(node, module), workflowIds, operationIds(node, module)));
        }
        return values;
    }

    private static Set<SkillId> skillIds(ObjectNode node, ModuleId module) { Set<SkillId> ids = new HashSet<>(); for (JsonNode name : requiredArray(node, "skills")) ids.add(new SkillId(module, requiredTextNode(name))); return ids; }
    private static Set<OperationId> operationIds(ObjectNode node, ModuleId module) { Set<OperationId> ids = new HashSet<>(); for (JsonNode name : requiredArray(node, "operations")) ids.add(new OperationId(module, requiredTextNode(name))); return ids; }

    private static Map<MaterialTypeId, Privacy> decodePrivacyMap(ObjectNode operation, ModuleId owner) {
        Map<MaterialTypeId, Privacy> values = new HashMap<>();
        for (JsonNode raw : requiredArray(operation, "acceptedMaterial")) { ObjectNode node = object(raw, "accepted material"); exactFields(node, Set.of("type", "privacy")); putUnique(values, parseMaterialTypeId(requiredText(node, "type"), owner), Privacy.valueOf(requiredText(node, "privacy"))); }
        return values;
    }
    private static Map<MaterialTypeId, Sensitivity> decodeSensitivityMap(ObjectNode operation, ModuleId owner) {
        Map<MaterialTypeId, Sensitivity> values = new HashMap<>();
        for (JsonNode raw : requiredArray(operation, "producedMaterial")) { ObjectNode node = object(raw, "produced material"); exactFields(node, Set.of("type", "sensitivity")); putUnique(values, parseMaterialTypeId(requiredText(node, "type"), owner), Sensitivity.valueOf(requiredText(node, "sensitivity"))); }
        return values;
    }
    private static void writePrivacyMap(ArrayNode array, Map<MaterialTypeId, Privacy> values) { values.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ModuleDefinitionJsonCodec::qualified))).forEach(entry -> { ObjectNode node = array.addObject(); node.put("type", qualified(entry.getKey())); node.put("privacy", entry.getValue().name()); }); }
    private static void writeSensitivityMap(ArrayNode array, Map<MaterialTypeId, Sensitivity> values) { values.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ModuleDefinitionJsonCodec::qualified))).forEach(entry -> { ObjectNode node = array.addObject(); node.put("type", qualified(entry.getKey())); node.put("sensitivity", entry.getValue().name()); }); }
    private static void writeNames(ArrayNode array, Set<String> values) { values.stream().sorted().forEach(array::add); }
    private static String qualified(MaterialTypeId id) { return id.moduleId().value() + ":" + id.name(); }
    private static MaterialTypeId parseMaterialTypeId(String value, ModuleId owner) { int split = value.indexOf(':'); return split < 0 ? new MaterialTypeId(owner, value) : new MaterialTypeId(new ModuleId(value.substring(0, split)), value.substring(split + 1)); }
    private static String requiredText(ObjectNode node, String field) { JsonNode value = node.get(field); if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new CodecException("missing textual field " + field); return value.textValue(); }
    private static String requiredTextNode(JsonNode node) { if (!node.isTextual() || node.textValue().isBlank()) throw new CodecException("expected nonblank text"); return node.textValue(); }
    private static int requiredInt(ObjectNode node, String field) { JsonNode value = node.get(field); if (value == null || !value.isInt()) throw new CodecException("missing integer field " + field); return value.intValue(); }
    private static ArrayNode requiredArray(ObjectNode node, String field) { JsonNode value = node.get(field); if (!(value instanceof ArrayNode array)) throw new CodecException("missing array field " + field); return array; }
    private static ObjectNode object(JsonNode node, String label) { if (!(node instanceof ObjectNode object)) throw new CodecException(label + " must be an object"); return object; }
    private static void exactFields(ObjectNode node, Set<String> allowed) { Iterator<String> names = node.fieldNames(); while (names.hasNext()) { String name = names.next(); if (!allowed.contains(name)) throw new CodecException("unknown field " + name); } if (!allowed.stream().allMatch(node::has)) throw new CodecException("missing required field in object"); }
    private static <K, V> void putUnique(Map<K, V> map, K key, V value) { if (map.putIfAbsent(key, value) != null) throw new CodecException("duplicate declaration: " + key); }
}
