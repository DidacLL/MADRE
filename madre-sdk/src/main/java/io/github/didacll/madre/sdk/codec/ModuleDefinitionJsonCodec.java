package io.github.didacll.madre.sdk.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
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
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.SkillDefinition;
import io.github.didacll.madre.sdk.module.WorkflowDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit version-four JSON mapping for language-neutral Module descriptions. */
public final class ModuleDefinitionJsonCodec {
    private static final int FORMAT_VERSION = 4;
    private final ObjectMapper mapper = new ObjectMapper();

    public String encode(ModuleDefinition definition) {
        ObjectNode root = mapper.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("module", definition.id().value());
        root.put("version", definition.version());
        root.put("purpose", definition.purpose());

        ArrayNode types = root.putArray("materialTypes");
        definition.materialTypes().values().stream()
                .sorted(java.util.Comparator.comparing(type -> type.id().name()))
                .forEach(type -> {
                    ObjectNode node = types.addObject();
                    node.put("name", type.id().name());
                    node.put("contentType", type.contentType());
                });
        ArrayNode exposed = root.putArray("exposedOperations");
        definition.exposedOperations().stream().map(OperationId::name).sorted()
                .forEach(exposed::add);
        ArrayNode skills = root.putArray("skills");
        definition.skills().values().stream()
                .sorted(java.util.Comparator.comparing(skill -> skill.id().name()))
                .forEach(skill -> {
                    ObjectNode node = skills.addObject();
                    node.put("name", skill.id().name());
                    node.put("purpose", skill.purpose());
                });

        Map<EffectProfileId, EffectProfile> canonicalProfiles = new LinkedHashMap<>();
        definition.operations().values().forEach(operation ->
                operation.effectProfiles().forEach((id, profile) -> {
                    EffectProfile previous = canonicalProfiles.putIfAbsent(id, profile);
                    if (previous != null && !previous.equals(profile)) {
                        throw new CodecException("conflicting EffectProfile declaration: " + id);
                    }
                }));
        ArrayNode profiles = root.putArray("effectProfiles");
        canonicalProfiles.values().stream()
                .sorted(java.util.Comparator
                        .comparing((EffectProfile profile) -> profile.id().operationId().name())
                        .thenComparing(profile -> profile.id().name()))
                .forEach(profile -> {
                    ObjectNode node = profiles.addObject();
                    node.put("operation", profile.id().operationId().name());
                    node.put("name", profile.id().name());
                    node.put("risk", profile.risk().name());
                    node.put("autonomy", profile.autonomy().name());
                });

        ArrayNode operations = root.putArray("operations");
        definition.operations().values().stream()
                .sorted(java.util.Comparator.comparing(operation -> operation.id().name()))
                .forEach(operation -> {
                    ObjectNode node = operations.addObject();
                    node.put("name", operation.id().name());
                    node.put("purpose", operation.purpose());
                    writePrivacyMap(node.putArray("acceptedMaterial"), operation.acceptedMaterial());
                    writeSensitivityMap(node.putArray("producedMaterial"), operation.producedMaterial());
                    ArrayNode effectIds = node.putArray("effectProfiles");
                    operation.effectProfiles().keySet().stream()
                            .map(EffectProfileId::name).sorted().forEach(effectIds::add);
                });

        ArrayNode agents = root.putArray("agents");
        definition.agents().values().stream()
                .sorted(java.util.Comparator.comparing(agent -> agent.id().name()))
                .forEach(agent -> {
                    ObjectNode node = agents.addObject();
                    node.put("name", agent.id().name());
                    node.put("purpose", agent.purpose());
                    node.put("integrity", agent.integrity().name());
                    writeNames(node.putArray("skills"), agent.skills().stream()
                            .map(SkillId::name).collect(java.util.stream.Collectors.toSet()));
                    ArrayNode workflows = node.putArray("workflows");
                    agent.workflows().values().stream()
                            .sorted(java.util.Comparator.comparing(workflow -> workflow.id().name()))
                            .forEach(workflow -> {
                                ObjectNode workflowNode = workflows.addObject();
                                workflowNode.put("name", workflow.id().name());
                                workflowNode.put("purpose", workflow.purpose());
                                writeOperationReferences(workflowNode.putArray("operations"),
                                        workflow.operations(), agent.id().moduleId());
                            });
                    writeNames(node.putArray("operations"), agent.operations().stream()
                            .map(OperationId::name).collect(java.util.stream.Collectors.toSet()));
                });
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new CodecException("cannot encode Module definition", exception);
        }
    }

    public ModuleDefinition decode(String json) {
        try {
            JsonNode parsed = mapper.readTree(json);
            ObjectNode root = object(parsed, "root");
            exactFields(root, Set.of("formatVersion", "module", "version", "purpose",
                    "materialTypes", "exposedOperations", "skills",
                    "effectProfiles", "operations", "agents"));
            if (requiredInt(root, "formatVersion") != FORMAT_VERSION) {
                throw new CodecException("unsupported formatVersion");
            }
            ModuleId module = new ModuleId(requiredText(root, "module"));
            Map<MaterialTypeId, MaterialTypeDefinition> typeDefinitions = decodeTypes(root, module);
            Set<OperationId> exposedOperations = new HashSet<>();
            for (JsonNode node : requiredArray(root, "exposedOperations")) {
                exposedOperations.add(new OperationId(module, requiredTextNode(node)));
            }
            Map<SkillId, SkillDefinition> skills = decodeSkills(root, module);
            Map<EffectProfileId, EffectProfile> profiles = decodeProfiles(root, module);
            Map<OperationId, OperationDefinition> operations = decodeOperations(root, module, profiles);
            Map<AgentId, AgentDefinition> agents = decodeAgents(root, module);
            return new ModuleDefinition(module, requiredText(root, "version"),
                    requiredText(root, "purpose"), typeDefinitions, agents, skills,
                    operations, exposedOperations);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new CodecException("invalid Module definition JSON", exception);
        }
    }

    private static Map<MaterialTypeId, MaterialTypeDefinition> decodeTypes(
            ObjectNode root, ModuleId module) {
        Map<MaterialTypeId, MaterialTypeDefinition> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "materialTypes")) {
            ObjectNode node = object(raw, "material type");
            exactFields(node, Set.of("name", "contentType"));
            MaterialTypeId id = new MaterialTypeId(module, requiredText(node, "name"));
            putUnique(values, id, new MaterialTypeDefinition(id, requiredText(node, "contentType")));
        }
        return values;
    }

    private static Map<SkillId, SkillDefinition> decodeSkills(ObjectNode root, ModuleId module) {
        Map<SkillId, SkillDefinition> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "skills")) {
            ObjectNode node = object(raw, "skill");
            exactFields(node, Set.of("name", "purpose"));
            SkillId id = new SkillId(module, requiredText(node, "name"));
            putUnique(values, id, new SkillDefinition(id, requiredText(node, "purpose")));
        }
        return values;
    }

    private static Map<EffectProfileId, EffectProfile> decodeProfiles(
            ObjectNode root, ModuleId module) {
        Map<EffectProfileId, EffectProfile> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "effectProfiles")) {
            ObjectNode node = object(raw, "effect profile");
            exactFields(node, Set.of("operation", "name", "risk", "autonomy"));
            EffectProfileId id = new EffectProfileId(
                    new OperationId(module, requiredText(node, "operation")),
                    requiredText(node, "name"));
            putUnique(values, id, new EffectProfile(id,
                    Risk.valueOf(requiredText(node, "risk")),
                    Autonomy.valueOf(requiredText(node, "autonomy"))));
        }
        return values;
    }

    private static Map<OperationId, OperationDefinition> decodeOperations(
            ObjectNode root, ModuleId module, Map<EffectProfileId, EffectProfile> profiles) {
        Map<OperationId, OperationDefinition> values = new HashMap<>();
        Set<EffectProfileId> usedProfiles = new HashSet<>();
        for (JsonNode raw : requiredArray(root, "operations")) {
            ObjectNode node = object(raw, "operation");
            exactFields(node, Set.of("name", "purpose", "acceptedMaterial",
                    "producedMaterial", "effectProfiles"));
            OperationId id = new OperationId(module, requiredText(node, "name"));
            Map<EffectProfileId, EffectProfile> selected = new HashMap<>();
            for (JsonNode profileNode : requiredArray(node, "effectProfiles")) {
                EffectProfileId profileId = new EffectProfileId(id,
                        requiredTextNode(profileNode));
                EffectProfile profile = profiles.get(profileId);
                if (profile == null || !usedProfiles.add(profileId)) {
                    throw new CodecException(
                            "unresolved or multiply owned EffectProfile: " + profileId);
                }
                selected.put(profileId, profile);
            }
            OperationDefinition operation = new OperationDefinition(id,
                    requiredText(node, "purpose"), decodePrivacyMap(node, module),
                    decodeSensitivityMap(node, module), selected);
            putUnique(values, id, operation);
        }
        if (!usedProfiles.equals(profiles.keySet())) {
            throw new CodecException("unreferenced EffectProfile declaration");
        }
        return values;
    }

    private static Map<AgentId, AgentDefinition> decodeAgents(ObjectNode root, ModuleId module) {
        Map<AgentId, AgentDefinition> values = new HashMap<>();
        for (JsonNode raw : requiredArray(root, "agents")) {
            ObjectNode node = object(raw, "agent");
            exactFields(node, Set.of("name", "purpose", "integrity", "skills", "workflows",
                    "operations"));
            AgentId id = new AgentId(module, requiredText(node, "name"));
            Map<WorkflowId, WorkflowDefinition> workflows = new HashMap<>();
            for (JsonNode workflowRaw : requiredArray(node, "workflows")) {
                ObjectNode workflowNode = object(workflowRaw, "workflow");
                exactFields(workflowNode, Set.of("name", "purpose", "operations"));
                WorkflowId workflowId = new WorkflowId(id, requiredText(workflowNode, "name"));
                WorkflowDefinition workflow = new WorkflowDefinition(workflowId,
                        requiredText(workflowNode, "purpose"),
                        operationIdList(workflowNode, module));
                putUnique(workflows, workflowId, workflow);
            }
            putUnique(values, id, new AgentDefinition(id, requiredText(node, "purpose"),
                    Integrity.valueOf(requiredText(node, "integrity")), skillIds(node, module),
                    workflows, operationIds(node, module)));
        }
        return values;
    }

    private static Set<SkillId> skillIds(ObjectNode node, ModuleId module) {
        Set<SkillId> ids = new HashSet<>();
        for (JsonNode name : requiredArray(node, "skills")) {
            ids.add(new SkillId(module, requiredTextNode(name)));
        }
        return ids;
    }

    private static Set<OperationId> operationIds(ObjectNode node, ModuleId module) {
        Set<OperationId> ids = new HashSet<>();
        for (JsonNode name : requiredArray(node, "operations")) {
            ids.add(new OperationId(module, requiredTextNode(name)));
        }
        return ids;
    }

    private static List<OperationId> operationIdList(ObjectNode node, ModuleId module) {
        List<OperationId> ids = new ArrayList<>();
        for (JsonNode name : requiredArray(node, "operations")) {
            ids.add(parseOperationId(requiredTextNode(name), module));
        }
        return List.copyOf(ids);
    }

    private static Map<MaterialTypeId, Privacy> decodePrivacyMap(
            ObjectNode operation, ModuleId owner) {
        Map<MaterialTypeId, Privacy> values = new HashMap<>();
        for (JsonNode raw : requiredArray(operation, "acceptedMaterial")) {
            ObjectNode node = object(raw, "accepted material");
            exactFields(node, Set.of("type", "privacy"));
            putUnique(values, parseMaterialTypeId(requiredText(node, "type"), owner),
                    Privacy.valueOf(requiredText(node, "privacy")));
        }
        return values;
    }

    private static Map<MaterialTypeId, Sensitivity> decodeSensitivityMap(
            ObjectNode operation, ModuleId owner) {
        Map<MaterialTypeId, Sensitivity> values = new HashMap<>();
        for (JsonNode raw : requiredArray(operation, "producedMaterial")) {
            ObjectNode node = object(raw, "produced material");
            exactFields(node, Set.of("type", "sensitivity"));
            putUnique(values, parseMaterialTypeId(requiredText(node, "type"), owner),
                    Sensitivity.valueOf(requiredText(node, "sensitivity")));
        }
        return values;
    }

    private static void writePrivacyMap(ArrayNode array, Map<MaterialTypeId, Privacy> values) {
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ModuleDefinitionJsonCodec::qualified)))
                .forEach(entry -> {
                    ObjectNode node = array.addObject();
                    node.put("type", qualified(entry.getKey()));
                    node.put("privacy", entry.getValue().name());
                });
    }

    private static void writeSensitivityMap(
            ArrayNode array, Map<MaterialTypeId, Sensitivity> values) {
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ModuleDefinitionJsonCodec::qualified)))
                .forEach(entry -> {
                    ObjectNode node = array.addObject();
                    node.put("type", qualified(entry.getKey()));
                    node.put("sensitivity", entry.getValue().name());
                });
    }

    private static void writeNames(ArrayNode array, Set<String> values) {
        values.stream().sorted().forEach(array::add);
    }

    private static void writeOperationReferences(
            ArrayNode array, List<OperationId> values, ModuleId owner) {
        values.stream().map(value -> operationReference(value, owner)).forEach(array::add);
    }

    private static String operationReference(OperationId id, ModuleId owner) {
        return id.moduleId().equals(owner) ? id.name()
                : id.moduleId().value() + ":" + id.name();
    }

    private static OperationId parseOperationId(String value, ModuleId owner) {
        int split = value.indexOf(':');
        return split < 0 ? new OperationId(owner, value)
                : new OperationId(new ModuleId(value.substring(0, split)),
                        value.substring(split + 1));
    }

    private static String qualified(MaterialTypeId id) {
        return id.moduleId().value() + ":" + id.name();
    }

    private static MaterialTypeId parseMaterialTypeId(String value, ModuleId owner) {
        int split = value.indexOf(':');
        return split < 0 ? new MaterialTypeId(owner, value)
                : new MaterialTypeId(new ModuleId(value.substring(0, split)),
                        value.substring(split + 1));
    }

    private static String requiredText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new CodecException("missing textual field " + field);
        }
        return value.textValue();
    }

    private static String requiredTextNode(JsonNode node) {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new CodecException("expected nonblank text");
        }
        return node.textValue();
    }

    private static int requiredInt(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt()) {
            throw new CodecException("missing integer field " + field);
        }
        return value.intValue();
    }

    private static ArrayNode requiredArray(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw new CodecException("missing array field " + field);
        }
        return array;
    }

    private static ObjectNode object(JsonNode node, String label) {
        if (!(node instanceof ObjectNode object)) {
            throw new CodecException("expected object for " + label);
        }
        return object;
    }

    private static void exactFields(ObjectNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new CodecException("unexpected fields: " + actual);
        }
    }

    private static <K, V> void putUnique(Map<K, V> values, K key, V value) {
        if (values.putIfAbsent(key, value) != null) {
            throw new CodecException("duplicate declaration: " + key);
        }
    }
}
