package io.github.didacll.madre.kernel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ResourceCoordinator {
    private final Map<ResourceId, Long> capacity;
    private final Map<ResourceId, Long> reserved = new HashMap<>();

    ResourceCoordinator(Map<ResourceId, Long> capacity) {
        this.capacity = Map.copyOf(capacity);
        this.capacity.forEach((id, units) -> {
            if (units < 0) throw new IllegalArgumentException("Resource capacity must not be negative: " + id);
        });
    }

    synchronized Optional<Lease> tryAcquire(List<ResourceClaim> claims) {
        Map<ResourceId, Long> requested = new HashMap<>();
        for (ResourceClaim claim : claims) requested.merge(claim.resource(), claim.units(), Math::addExact);
        for (Map.Entry<ResourceId, Long> entry : requested.entrySet()) {
            long available = capacity.getOrDefault(entry.getKey(), 0L) - reserved.getOrDefault(entry.getKey(), 0L);
            if (available < entry.getValue()) return Optional.empty();
        }
        requested.forEach((id, units) -> reserved.merge(id, units, Math::addExact));
        return Optional.of(new Lease(requested));
    }

    final class Lease implements AutoCloseable {
        private final Map<ResourceId, Long> claims;
        private boolean closed;
        private Lease(Map<ResourceId, Long> claims) { this.claims = Map.copyOf(claims); }

        @Override public void close() {
            synchronized (ResourceCoordinator.this) {
                if (closed) return;
                claims.forEach((id, units) -> reserved.compute(id, (ignored, current) -> {
                    long next = current - units;
                    return next == 0 ? null : next;
                }));
                closed = true;
            }
        }
    }
}
