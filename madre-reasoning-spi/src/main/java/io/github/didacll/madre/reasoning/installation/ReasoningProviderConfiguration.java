package io.github.didacll.madre.reasoning.installation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable owner configuration exposed read-only to an installed reasoning provider. */
public final class ReasoningProviderConfiguration {
    private final Map<String, String> values;

    public ReasoningProviderConfiguration(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        Map<String, String> copy = new TreeMap<>();
        values.forEach((key, value) -> {
            if (Objects.requireNonNull(key, "configuration key").isBlank()) {
                throw new IllegalArgumentException("configuration key must not be blank");
            }
            copy.put(key.strip(), Objects.requireNonNull(value, "configuration value"));
        });
        this.values = Collections.unmodifiableMap(copy);
    }

    public Optional<String> value(String key) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(key, "key")));
    }

    public Set<String> keys() { return values.keySet(); }
}
