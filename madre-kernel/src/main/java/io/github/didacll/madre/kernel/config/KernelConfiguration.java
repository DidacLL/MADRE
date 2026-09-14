package io.github.didacll.madre.kernel.config;

import io.github.didacll.madre.kernel.reasoning.ResourceId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Explicit installation settings for the local Kernel reasoning runtime. */
public record KernelConfiguration(Optional<ModuleId> coreModule, Path workDatabase,
        Duration resultRetention, Map<ResourceId, Long> resourceCapacity) {
    public KernelConfiguration {
        Objects.requireNonNull(coreModule, "coreModule");
        Objects.requireNonNull(workDatabase, "workDatabase");
        Objects.requireNonNull(resultRetention, "resultRetention");
        if (resultRetention.isNegative() || resultRetention.isZero()) {
            throw new IllegalArgumentException("resultRetention must be positive");
        }
        resourceCapacity = Map.copyOf(resourceCapacity);
    }

    public KernelConfiguration(ModuleId coreModule, Path workDatabase,
            Duration resultRetention, Map<ResourceId, Long> resourceCapacity) {
        this(Optional.of(Objects.requireNonNull(coreModule, "coreModule")), workDatabase,
                resultRetention, resourceCapacity);
    }

    public KernelConfiguration(Path workDatabase, Duration resultRetention,
            Map<ResourceId, Long> resourceCapacity) {
        this(Optional.empty(), workDatabase, resultRetention, resourceCapacity);
    }

    public static KernelConfiguration from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        Map<ResourceId, Long> capacities = new HashMap<>();
        properties.stringPropertyNames().stream().filter(name -> name.startsWith("resources."))
                .forEach(name -> capacities.put(
                        new ResourceId(name.substring("resources.".length())),
                        Long.parseLong(properties.getProperty(name))));
        String core = properties.getProperty("roles.core");
        Optional<ModuleId> coreModule = core == null || core.isBlank()
                ? Optional.empty() : Optional.of(new ModuleId(core.strip()));
        return new KernelConfiguration(coreModule,
                Path.of(required(properties, "kernel.database")),
                Duration.ofSeconds(Long.parseLong(properties.getProperty(
                        "kernel.result-retention-seconds", "86400"))), capacities);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing property " + key);
        }
        return value.strip();
    }
}
