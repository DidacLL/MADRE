package io.github.didacll.madre.kernel.config;

import io.github.didacll.madre.kernel.capability.ResourceId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Explicit installation settings for the local physical runtime. */
public record KernelConfiguration(ModuleId coreModule, InetAddress bindAddress,
        Path workDatabase, Duration resultRetention, Map<ResourceId, Long> resourceCapacity) {
    public KernelConfiguration {
        Objects.requireNonNull(coreModule); Objects.requireNonNull(bindAddress); Objects.requireNonNull(workDatabase); Objects.requireNonNull(resultRetention);
        if (!bindAddress.isLoopbackAddress()) throw new IllegalArgumentException("initial MADRE transport must bind to loopback");
        if (resultRetention.isNegative() || resultRetention.isZero()) throw new IllegalArgumentException("resultRetention must be positive");
        resourceCapacity = Map.copyOf(resourceCapacity);
    }

    public static KernelConfiguration from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        try {
            Map<ResourceId, Long> capacities = new HashMap<>();
            properties.stringPropertyNames().stream().filter(name -> name.startsWith("resources.")).forEach(name ->
                    capacities.put(new ResourceId(name.substring("resources.".length())), Long.parseLong(properties.getProperty(name))));
            return new KernelConfiguration(new ModuleId(required(properties, "roles.core")),
                    InetAddress.getByName(properties.getProperty("kernel.bind", "127.0.0.1")),
                    Path.of(required(properties, "kernel.database")),
                    Duration.ofSeconds(Long.parseLong(properties.getProperty("kernel.result-retention-seconds", "86400"))), capacities);
        } catch (UnknownHostException exception) { throw new IllegalArgumentException("invalid kernel.bind", exception); }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("missing property " + key); return value.strip();
    }
}
