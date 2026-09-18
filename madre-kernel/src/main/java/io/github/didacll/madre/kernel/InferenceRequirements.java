package io.github.didacll.madre.kernel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Engine-independent technical requirements translated by MADRE runtime. */
public record InferenceRequirements(
        Placement placement,
        TechnicalCapabilityRequirement capability,
        Urgency urgency,
        Instant eligibleAt,
        Optional<Instant> deadline,
        Duration timeout,
        RetryPolicy retryPolicy,
        Optional<Duration> maximumExpectedLatency,
        Optional<EngineId> exactEngine,
        Optional<String> exactProvider,
        Optional<String> exactModel,
        List<ResourceClaim> resources) {

    public InferenceRequirements {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(eligibleAt, "eligibleAt");
        deadline = Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        maximumExpectedLatency = Objects.requireNonNull(maximumExpectedLatency, "maximumExpectedLatency");
        maximumExpectedLatency.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) throw new IllegalArgumentException("maximumExpectedLatency must be positive");
        });
        exactEngine = Objects.requireNonNull(exactEngine, "exactEngine");
        exactProvider = normalized(exactProvider, "exactProvider");
        exactModel = normalized(exactModel, "exactModel");
        Objects.requireNonNull(resources, "resources");
        TreeMap<ResourceId, Long> combined = new TreeMap<>();
        for (ResourceClaim claim : resources) {
            combined.merge(claim.resource(), claim.units(), Math::addExact);
        }
        resources = combined.entrySet().stream()
                .map(entry -> new ResourceClaim(entry.getKey(), entry.getValue()))
                .toList();
        deadline.ifPresent(value -> {
            if (value.isBefore(eligibleAt)) throw new IllegalArgumentException("deadline precedes eligibleAt");
        });
    }

    public static InferenceRequirements defaults() {
        return new InferenceRequirements(Placement.LOCAL_OR_REMOTE, new TechnicalCapabilityRequirement.None(),
                Urgency.NORMAL, Instant.now(), Optional.empty(), Duration.ofMinutes(2), RetryPolicy.noRetry(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(item -> {
            if (item.isBlank()) throw new IllegalArgumentException(name + " must not contain blank text");
            return item;
        });
    }
}
