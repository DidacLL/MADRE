package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.capability.ResourceClaim;
import io.github.didacll.madre.kernel.capability.ResourceId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Atomic coordinator for arbitrary quantified physical resources. */
public final class ResourceCoordinator {
    private final Map<ResourceId, Long> capacity;
    private final Map<ResourceId, Long> used = new HashMap<>();

    public ResourceCoordinator(Map<ResourceId, Long> capacity) {
        Objects.requireNonNull(capacity, "capacity");
        Map<ResourceId, Long> copy = new HashMap<>();
        capacity.forEach((id, units) -> {
            Objects.requireNonNull(id, "resource"); Objects.requireNonNull(units, "units");
            if (units < 0) throw new IllegalArgumentException("capacity must not be negative");
            copy.put(id, units);
        });
        this.capacity = Map.copyOf(copy);
    }

    public synchronized boolean canReserve(List<ResourceClaim> claims) {
        return totals(claims).entrySet().stream().allMatch(entry ->
                used.getOrDefault(entry.getKey(), 0L) + entry.getValue() <= capacity.getOrDefault(entry.getKey(), 0L));
    }

    public synchronized Optional<Lease> tryReserve(List<ResourceClaim> claims) {
        Map<ResourceId, Long> required = totals(claims);
        if (!required.entrySet().stream().allMatch(entry ->
                used.getOrDefault(entry.getKey(), 0L) + entry.getValue() <= capacity.getOrDefault(entry.getKey(), 0L))) return Optional.empty();
        required.forEach((id, units) -> used.merge(id, units, Long::sum));
        return Optional.of(new Lease(required));
    }

    private static Map<ResourceId, Long> totals(List<ResourceClaim> claims) {
        Objects.requireNonNull(claims, "claims");
        Map<ResourceId, Long> result = new HashMap<>();
        claims.forEach(claim -> result.merge(claim.resource(), claim.units(), Long::sum));
        return result;
    }

    public final class Lease implements AutoCloseable {
        private final Map<ResourceId, Long> claims;
        private boolean open = true;
        private Lease(Map<ResourceId, Long> claims) { this.claims = Map.copyOf(claims); }
        @Override public synchronized void close() {
            if (!open) return;
            synchronized (ResourceCoordinator.this) {
                claims.forEach((id, units) -> used.compute(id, (ignored, value) -> Objects.requireNonNull(value) - units));
            }
            open = false;
        }
    }
}
