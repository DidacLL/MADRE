package io.github.didacll.madre.reasoning.installation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Provider-produced raw-property mutation applied generically by the MADRE host. */
public record ReasoningProviderConfigurationUpdate(
        Map<String, String> values,
        Set<String> removals) {
    public ReasoningProviderConfigurationUpdate {
        Map<String, String> copiedValues = new TreeMap<>();
        Objects.requireNonNull(values, "values").forEach((key, value) -> {
            String normalized = reasoningKey(key);
            copiedValues.put(normalized, Objects.requireNonNull(value, "configuration value"));
        });
        Set<String> copiedRemovals = new TreeSet<>();
        Objects.requireNonNull(removals, "removals").forEach(key -> copiedRemovals.add(reasoningKey(key)));
        for (String key : copiedValues.keySet()) {
            if (copiedRemovals.contains(key)) {
                throw new IllegalArgumentException("configuration update both writes and removes " + key);
            }
        }
        values = Collections.unmodifiableMap(copiedValues);
        removals = Collections.unmodifiableSet(copiedRemovals);
    }

    public static ReasoningProviderConfigurationUpdate empty() {
        return new ReasoningProviderConfigurationUpdate(Map.of(), Set.of());
    }

    private static String reasoningKey(String key) {
        String normalized = Objects.requireNonNull(key, "configuration key").strip();
        if (!normalized.startsWith("reasoning.") || normalized.equals("reasoning.directory")) {
            throw new IllegalArgumentException("provider update may modify only provider-owned reasoning.* keys");
        }
        return normalized;
    }
}
