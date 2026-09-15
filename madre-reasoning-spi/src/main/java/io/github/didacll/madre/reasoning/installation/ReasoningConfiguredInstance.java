package io.github.didacll.madre.reasoning.installation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One configured provider instance expressed only through provider-declared owner field names. */
public record ReasoningConfiguredInstance(String name, boolean enabled, Map<String, String> values) {
    public ReasoningConfiguredInstance {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) throw new IllegalArgumentException("instance name must not be blank");
        Map<String, String> copy = new TreeMap<>();
        Objects.requireNonNull(values, "values").forEach((key, value) -> {
            String normalized = Objects.requireNonNull(key, "field name").strip();
            if (normalized.isEmpty()) throw new IllegalArgumentException("field name must not be blank");
            copy.put(normalized, Objects.requireNonNull(value, "field value"));
        });
        values = Collections.unmodifiableMap(copy);
    }
}
