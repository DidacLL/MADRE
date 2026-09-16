package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.identity.ModuleId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Provider-owned owner-facing installation configuration contract for one canonical Module. */
public record ModuleConfigurationDescriptor(
        ModuleId moduleId,
        String displayName,
        String help,
        List<ModuleConfigurationField> fields) {
    public ModuleConfigurationDescriptor {
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        displayName = text(displayName, "displayName");
        help = text(help, "help");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        Set<String> names = new HashSet<>();
        for (ModuleConfigurationField field : fields) {
            ModuleConfigurationField present = Objects.requireNonNull(field, "field");
            if (!names.add(present.name())) {
                throw new IllegalArgumentException(
                        "duplicate Module configuration field: " + present.name());
            }
        }
    }

    public static ModuleConfigurationDescriptor none(ModuleId moduleId) {
        ModuleId id = Objects.requireNonNull(moduleId, "moduleId");
        return new ModuleConfigurationDescriptor(id, id.value(),
                "This Module does not declare owner-configurable installation settings.", List.of());
    }

    private static String text(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
