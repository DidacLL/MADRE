package io.github.didacll.madre.reasoning.installation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Stable provider identity plus the small provider-owned surface used by owner configuration. */
public record ReasoningProviderDescriptor(
        ReasoningProviderId id,
        String displayName,
        String help,
        List<ReasoningConfigurationField> fields) {
    public ReasoningProviderDescriptor {
        id = Objects.requireNonNull(id, "id");
        displayName = text(displayName, "displayName");
        help = text(help, "help");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        Set<String> names = new HashSet<>();
        for (ReasoningConfigurationField field : fields) {
            ReasoningConfigurationField value = Objects.requireNonNull(field, "field");
            if (!names.add(value.name())) {
                throw new IllegalArgumentException("duplicate provider configuration field "
                        + value.name() + " for " + id);
            }
        }
    }

    private static String text(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
