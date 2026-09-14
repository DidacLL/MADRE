package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.identity.ModuleId;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable owner installation configuration scoped to one canonical Module identity. */
public final class ModuleProviderConfiguration {
    private final ModuleId moduleId;
    private final Map<String, String> values;

    public ModuleProviderConfiguration(ModuleId moduleId, Map<String, String> values) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(values, "values");
        Map<String, String> copy = new TreeMap<>();
        values.forEach((key, value) -> {
            String normalized = Objects.requireNonNull(key, "configuration key").strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("configuration key must not be blank");
            }
            if (copy.containsKey(normalized)) {
                throw new IllegalArgumentException(
                        "configuration keys collide after normalization: " + normalized);
            }
            copy.put(normalized, Objects.requireNonNull(value, "configuration value"));
        });
        this.values = Collections.unmodifiableMap(copy);
    }

    /** Canonical identity whose installation these values configure. */
    public ModuleId moduleId() { return moduleId; }

    /** Returns one Module-owned setting without imposing any application-level schema. */
    public Optional<String> value(String key) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(key, "key")));
    }

    public Set<String> keys() { return values.keySet(); }
    public boolean isEmpty() { return values.isEmpty(); }
}
