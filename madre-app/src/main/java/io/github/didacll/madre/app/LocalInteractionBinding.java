package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Immutable application-local binding from owner presentation to the selected CORE Module. */
record LocalInteractionBinding(ModuleId moduleId, String defaultOperation,
        String standardOperation, String promptMaterialType, Sensitivity defaultSensitivity,
        Optional<UpdatesBinding> updates) {
    private static final String PREFIX = "interaction.";
    private static final String CORE = "roles.core";
    private static final String DEFAULT_OPERATION = PREFIX + "default-operation";
    private static final String STANDARD_OPERATION = PREFIX + "standard-operation";
    private static final String PROMPT_MATERIAL_TYPE = PREFIX + "prompt-material-type";
    private static final String DEFAULT_SENSITIVITY = PREFIX + "default-sensitivity";
    private static final String UPDATES_OPERATION = PREFIX + "updates-operation";
    private static final String UPDATES_MATERIAL_TYPE = PREFIX + "updates-material-type";
    private static final String UPDATES_PAYLOAD = PREFIX + "updates-payload";
    private static final String UPDATES_SENSITIVITY = PREFIX + "updates-sensitivity";
    private static final Set<String> ALLOWED_KEYS = Set.of(DEFAULT_OPERATION,
            STANDARD_OPERATION, PROMPT_MATERIAL_TYPE, DEFAULT_SENSITIVITY, UPDATES_OPERATION,
            UPDATES_MATERIAL_TYPE, UPDATES_PAYLOAD, UPDATES_SENSITIVITY);
    private static final Set<String> UPDATE_KEYS = Set.of(UPDATES_OPERATION, UPDATES_MATERIAL_TYPE,
            UPDATES_PAYLOAD, UPDATES_SENSITIVITY);

    LocalInteractionBinding {
        Objects.requireNonNull(moduleId, "moduleId");
        defaultOperation = requireText(defaultOperation, "defaultOperation");
        standardOperation = requireText(standardOperation, "standardOperation");
        promptMaterialType = requireText(promptMaterialType, "promptMaterialType");
        Objects.requireNonNull(defaultSensitivity, "defaultSensitivity");
        updates = Objects.requireNonNull(updates, "updates");
    }

    static Optional<LocalInteractionBinding> resolve(Properties properties,
            List<ModuleInstance> installedModules) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(installedModules, "installedModules");
        List<String> configuredKeys = properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith(PREFIX)).sorted().toList();
        if (configuredKeys.isEmpty()) return Optional.empty();
        configuredKeys.stream().filter(name -> !ALLOWED_KEYS.contains(name)).findFirst()
                .ifPresent(name -> {
                    throw new IllegalArgumentException("unknown interaction property " + name);
                });

        ModuleId moduleId = new ModuleId(required(properties, CORE));
        ModuleInstance module = installedModules.stream()
                .filter(item -> item.definition().id().equals(moduleId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "selected CORE interaction Module is not installed: " + moduleId));
        String defaultOperation = required(properties, DEFAULT_OPERATION);
        String standardOperation = required(properties, STANDARD_OPERATION);
        String promptMaterialType = required(properties, PROMPT_MATERIAL_TYPE);
        Sensitivity defaultSensitivity = sensitivity(properties, DEFAULT_SENSITIVITY);

        validateTextOperation(module, DEFAULT_OPERATION, defaultOperation, promptMaterialType,
                defaultSensitivity);
        validateTextOperation(module, STANDARD_OPERATION, standardOperation, promptMaterialType,
                defaultSensitivity);

        boolean anyUpdates = UPDATE_KEYS.stream().anyMatch(properties::containsKey);
        Optional<UpdatesBinding> updates = Optional.empty();
        if (anyUpdates) {
            String updatesOperation = required(properties, UPDATES_OPERATION);
            String updatesMaterialType = required(properties, UPDATES_MATERIAL_TYPE);
            String updatesPayload = required(properties, UPDATES_PAYLOAD);
            Sensitivity updatesSensitivity = sensitivity(properties, UPDATES_SENSITIVITY);
            MadreApplication.ResolvedTextOperation resolved = validateTextOperation(module,
                    UPDATES_OPERATION, updatesOperation, updatesMaterialType, updatesSensitivity);
            try {
                Object decoded = resolved.inputType().codec().decode(
                        updatesPayload.getBytes(StandardCharsets.UTF_8));
                if (!(decoded instanceof String)) {
                    throw new IllegalArgumentException("configured updates payload is not text");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        UPDATES_PAYLOAD + " cannot be decoded by configured Material type", exception);
            }
            updates = Optional.of(new UpdatesBinding(updatesOperation, updatesMaterialType,
                    updatesPayload, updatesSensitivity));
        }
        return Optional.of(new LocalInteractionBinding(moduleId, defaultOperation,
                standardOperation, promptMaterialType, defaultSensitivity, updates));
    }

    private static MadreApplication.ResolvedTextOperation validateTextOperation(
            ModuleInstance module, String property, String operationSpec, String materialType,
            Sensitivity sensitivity) {
        final MadreApplication.ResolvedTextOperation resolved;
        try {
            resolved = MadreApplication.resolveInteractionTextOperation(module, operationSpec,
                    materialType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(property + " is incompatible with installed Module "
                    + module.definition().id() + ": " + exception.getMessage(), exception);
        }
        requireConsoleText(resolved.inputType(), property + " input");
        if (resolved.operation().producedMaterial().isEmpty()) {
            throw new IllegalArgumentException(property + " must produce text Material");
        }
        resolved.operation().producedMaterial().keySet().forEach(typeId -> {
            MaterialType<?> type = module.materialTypes().get(typeId);
            if (type == null) {
                throw new IllegalArgumentException(property
                        + " produces Material without a local Java binding: " + typeId);
            }
            requireConsoleText(type, property + " output");
        });
        Privacy receivingPrivacy = resolved.operation().acceptedMaterial().get(
                resolved.inputType().id());
        if (!sensitivity.canReach(receivingPrivacy)) {
            throw new IllegalArgumentException(property + " cannot accept configured Sensitivity "
                    + sensitivity + " at Privacy " + receivingPrivacy);
        }
        return resolved;
    }

    private static void requireConsoleText(MaterialType<?> type, String label) {
        if (!type.javaType().equals(String.class)
                || !type.contentType().toLowerCase(Locale.ROOT).startsWith("text/")) {
            throw new IllegalArgumentException(label + " Material type is not console text: "
                    + type.id());
        }
    }

    private static Sensitivity sensitivity(Properties properties, String key) {
        final Sensitivity value;
        try {
            value = Sensitivity.valueOf(required(properties, key).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " is not a valid Sensitivity", exception);
        }
        if (value == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException(key + " cannot be SYSTEM_RESERVED");
        }
        return value;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing property " + key);
        }
        return value.strip();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.strip();
    }

    record UpdatesBinding(String operation, String materialType, String payload,
            Sensitivity sensitivity) {
        UpdatesBinding {
            operation = requireText(operation, "operation");
            materialType = requireText(materialType, "materialType");
            payload = requireText(payload, "payload");
            Objects.requireNonNull(sensitivity, "sensitivity");
        }
    }
}
