package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Live installation registry and deterministic physical selection. */
public final class CapabilityRegistry {
    private final Map<CapabilityId, Installed<?, ?>> installed = new ConcurrentHashMap<>();
    private final ResourceCoordinator resources;

    public CapabilityRegistry(ResourceCoordinator resources) { this.resources = Objects.requireNonNull(resources, "resources"); }

    public <C, R> Registration register(Capability<C, R> capability, int installationPreference) {
        Objects.requireNonNull(capability, "capability");
        if (installationPreference < 0) throw new IllegalArgumentException("installationPreference must not be negative");
        Installed<C, R> entry = new Installed<>(capability, installationPreference);
        if (installed.putIfAbsent(capability.manifest().id(), entry) != null) throw new IllegalStateException("Capability already registered");
        return () -> installed.remove(capability.manifest().id(), entry);
    }

    public Optional<PhysicalContract<?, ?>> contract(String id) {
        for (Installed<?, ?> value : installed.values()) {
            PhysicalContract<?, ?> contract = value.capability().manifest().contract();
            if (contract.id().equals(id)) return Optional.of(contract);
        }
        return Optional.empty();
    }

    public Optional<PhysicalContract<?, ?>> contractForCommand(Class<?> commandType, Class<?> resultType) {
        PhysicalContract<?, ?> selected = null;
        for (Installed<?, ?> value : installed.values()) {
            PhysicalContract<?, ?> contract = value.capability().manifest().contract();
            if (contract.commandType().equals(commandType) && contract.resultType().equals(resultType)
                    && (selected == null || contract.id().compareTo(selected.id()) < 0)) selected = contract;
        }
        return Optional.ofNullable(selected);
    }

    public <C, R> Optional<Selection<C, R>> select(WorkRequest<C, R> request) {
        Objects.requireNonNull(request, "request");
        return installed.values().stream()
                .filter(entry -> compatible(entry, request))
                .sorted(Comparator.comparingInt((Installed<?, ?> value) -> value.preference()).reversed()
                        .thenComparing(value -> value.capability().manifest().id()))
                .map(entry -> reserve(entry, request))
                .flatMap(Optional::stream).findFirst().map(selection -> cast(selection, request));
    }

    private boolean compatible(Installed<?, ?> entry, WorkRequest<?, ?> request) {
        CapabilityManifest<?, ?> manifest = entry.capability().manifest();
        if (!manifest.contract().commandType().equals(request.command().getClass())
                || !manifest.contract().resultType().equals(request.resultType())) return false;
        if (!request.carriedSensitivity().canReach(manifest.receivingPrivacy())) return false;
        if (request.physicalRisk().isPresent() && (manifest.physicalIntegrity().isEmpty()
                || request.physicalRisk().orElseThrow().rank() > manifest.physicalIntegrity().orElseThrow().rank())) return false;
        if (entry.capability().availability() != CapabilityAvailability.AVAILABLE) return false;
        PhysicalPreferences preferences = request.preferences();
        if (preferences.location().isPresent() && preferences.location().orElseThrow() != manifest.location()) return false;
        if (preferences.maximumLatency().isPresent() && manifest.expectedLatency().compareTo(preferences.maximumLatency().orElseThrow()) > 0) return false;
        return resources.canReserve(manifest.resources());
    }

    private Optional<Selection<?, ?>> reserve(Installed<?, ?> entry, WorkRequest<?, ?> request) {
        return resources.tryReserve(entry.capability().manifest().resources()).map(lease -> new Selection<>(entry.capability(), lease));
    }

    @SuppressWarnings("unchecked")
    private static <C, R> Selection<C, R> cast(Selection<?, ?> selection, WorkRequest<C, R> request) {
        Capability<?, ?> raw = selection.capability();
        if (!raw.manifest().contract().commandType().isInstance(request.command())
                || !raw.manifest().contract().resultType().equals(request.resultType())) throw new IllegalStateException("contract changed during selection");
        return (Selection<C, R>) selection;
    }

    private record Installed<C, R>(Capability<C, R> capability, int preference) { }

    public record Selection<C, R>(Capability<C, R> capability, ResourceCoordinator.Lease lease) implements AutoCloseable {
        public Selection { Objects.requireNonNull(capability, "capability"); Objects.requireNonNull(lease, "lease"); }
        @Override public void close() { lease.close(); }
    }

    @FunctionalInterface public interface Registration extends AutoCloseable { @Override void close(); }
}
