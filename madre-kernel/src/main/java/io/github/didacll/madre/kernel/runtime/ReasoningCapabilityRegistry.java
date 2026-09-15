package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Live installation registry and deterministic selection of reasoning mechanisms. */
public final class ReasoningCapabilityRegistry {
    private final Map<ReasoningCapabilityId, Installed<?, ?>> installed =
            new ConcurrentHashMap<>();
    private final ResourceCoordinator resources;

    public ReasoningCapabilityRegistry(ResourceCoordinator resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public synchronized <R, C extends ReasoningComputation<R>> Registration register(
            ReasoningCapability<R, C> capability, int installationPreference) {
        Objects.requireNonNull(capability, "capability");
        if (installationPreference < 0) {
            throw new IllegalArgumentException("installationPreference must not be negative");
        }
        ReasoningContract<R, C> contract = capability.manifest().contract();
        for (Installed<?, ?> existing : installed.values()) {
            ReasoningContract<?, ?> registered = existing.capability().manifest().contract();
            boolean sameTypes = registered.computationType().equals(contract.computationType())
                    && registered.resultType().equals(contract.resultType());
            if (sameTypes != registered.id().equals(contract.id())) {
                throw new IllegalArgumentException(
                        "reasoning contract identity and computation/result types disagree");
            }
        }
        Installed<R, C> entry = new Installed<>(capability, installationPreference);
        if (installed.putIfAbsent(capability.manifest().id(), entry) != null) {
            throw new IllegalStateException("ReasoningCapability already registered");
        }
        return () -> installed.remove(capability.manifest().id(), entry);
    }

    /** Stable snapshot of currently materialized reasoning-mechanism identities. */
    public List<ReasoningCapabilityId> installedIds() {
        return installed.keySet().stream().sorted().toList();
    }

    public Optional<ReasoningContract<?, ?>> contract(String id) {
        for (Installed<?, ?> value : installed.values()) {
            ReasoningContract<?, ?> contract = value.capability().manifest().contract();
            if (contract.id().equals(id)) return Optional.of(contract);
        }
        return Optional.empty();
    }

    public Optional<ReasoningContract<?, ?>> contractForComputation(Class<?> computationType,
            Class<?> resultType) {
        ReasoningContract<?, ?> selected = null;
        for (Installed<?, ?> value : installed.values()) {
            ReasoningContract<?, ?> contract = value.capability().manifest().contract();
            if (contract.computationType().equals(computationType)
                    && contract.resultType().equals(resultType)
                    && (selected == null || contract.id().compareTo(selected.id()) < 0)) {
                selected = contract;
            }
        }
        return Optional.ofNullable(selected);
    }

    public <R, C extends ReasoningComputation<R>> Optional<Selection<R, C>> select(
            ReasoningRequest<R, C> request) {
        Objects.requireNonNull(request, "request");
        return select(request.computation(), request.resultType(), request.carriedSensitivity(),
                request.preferences());
    }

    <R, C extends ReasoningComputation<R>> Optional<Selection<R, C>> select(C computation,
            Class<R> resultType, Sensitivity carriedSensitivity,
            ReasoningPreferences preferences) {
        Objects.requireNonNull(computation, "computation");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(carriedSensitivity, "carriedSensitivity");
        Objects.requireNonNull(preferences, "preferences");
        return installed.values().stream()
                .filter(entry -> compatible(entry, computation, resultType, carriedSensitivity,
                        preferences))
                .sorted(Comparator.comparingInt((Installed<?, ?> value) -> value.preference())
                        .reversed().thenComparing(value -> value.capability().manifest().id()))
                .map(this::reserve)
                .flatMap(Optional::stream)
                .findFirst()
                .map(selection -> cast(selection, computation, resultType));
    }

    private boolean compatible(Installed<?, ?> entry, Object computation, Class<?> resultType,
            Sensitivity carriedSensitivity, ReasoningPreferences preferences) {
        ReasoningCapabilityManifest<?, ?> manifest = entry.capability().manifest();
        if (!manifest.contract().computationType().equals(computation.getClass())
                || !manifest.contract().resultType().equals(resultType)) return false;
        if (!supports(entry.capability(), computation)) return false;
        if (!carriedSensitivity.canReach(manifest.receivingPrivacy())) return false;
        if (entry.capability().availability() != ReasoningAvailability.AVAILABLE) return false;
        if (preferences.location().isPresent()
                && preferences.location().orElseThrow() != manifest.location()) return false;
        if (preferences.maximumLatency().isPresent()
                && manifest.expectedLatency().compareTo(
                        preferences.maximumLatency().orElseThrow()) > 0) return false;
        return resources.canReserve(manifest.resources());
    }

    private static boolean supports(ReasoningCapability<?, ?> capability, Object computation) {
        return supportsCaptured(capability, computation);
    }

    private static <R, C extends ReasoningComputation<R>> boolean supportsCaptured(
            ReasoningCapability<R, C> capability, Object computation) {
        C typed = capability.manifest().contract().computationType().cast(computation);
        return capability.supports(typed);
    }

    private Optional<Selection<?, ?>> reserve(Installed<?, ?> entry) {
        return resources.tryReserve(entry.capability().manifest().resources())
                .map(lease -> new Selection<>(entry.capability(), lease));
    }

    @SuppressWarnings("unchecked")
    private static <R, C extends ReasoningComputation<R>> Selection<R, C> cast(
            Selection<?, ?> selection, C computation, Class<R> resultType) {
        ReasoningCapability<?, ?> raw = selection.capability();
        if (!raw.manifest().contract().computationType().isInstance(computation)
                || !raw.manifest().contract().resultType().equals(resultType)) {
            throw new IllegalStateException("reasoning contract changed during selection");
        }
        return (Selection<R, C>) selection;
    }

    private record Installed<R, C extends ReasoningComputation<R>>(
            ReasoningCapability<R, C> capability, int preference) { }

    public record Selection<R, C extends ReasoningComputation<R>>(
            ReasoningCapability<R, C> capability, ResourceCoordinator.Lease lease)
            implements AutoCloseable {
        public Selection {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(lease, "lease");
        }
        @Override public void close() { lease.close(); }
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override void close();
    }
}
