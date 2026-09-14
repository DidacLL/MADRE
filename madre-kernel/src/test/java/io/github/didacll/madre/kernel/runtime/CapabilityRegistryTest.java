package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalCodec;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.kernel.capability.ResourceClaim;
import io.github.didacll.madre.kernel.capability.ResourceId;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.PhysicalRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CapabilityRegistryTest {
    private static final PhysicalCodec<String> CODEC = new PhysicalCodec<>() {
        @Override public byte[] encode(String value) { return value.getBytes(StandardCharsets.UTF_8); }
        @Override public String decode(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
    };
    private static final PhysicalContract<String, String> CONTRACT = new PhysicalContract<>("test.v1", String.class, String.class, CODEC, CODEC);
    private static final ResourceId SLOT = new ResourceId("model-slot");

    @Test void selectsOnlyComposableAndAvailablePhysicalMechanismsDeterministically() {
        CapabilityRegistry registry = new CapabilityRegistry(new ResourceCoordinator(Map.of(SLOT, 1L)));
        registry.register(capability("unknown", Privacy.SECRET, Integrity.I5,
                CapabilityAvailability.UNKNOWN), 100);
        registry.register(capability("unavailable", Privacy.SECRET, Integrity.I5,
                CapabilityAvailability.UNAVAILABLE), 100);
        registry.register(capability("z", Privacy.SECRET, Integrity.I5, CapabilityAvailability.AVAILABLE), 1);
        registry.register(capability("a", Privacy.LOCAL, Integrity.I3, CapabilityAvailability.AVAILABLE), 1);
        WorkRequest<String, String> request = request(Sensitivity.S4, Optional.of(Risk.R4));
        try (CapabilityRegistry.Selection<String, String> selection = registry.select(request).orElseThrow()) {
            assertEquals("z", selection.capability().manifest().id().value());
            assertTrue(registry.select(request).isEmpty(), "resource is atomically leased");
        }
        assertTrue(registry.select(request).isPresent());
    }

    @Test void onePhysicalJavaContractCannotDriftAcrossRegisteredCapabilities() {
        CapabilityRegistry registry = new CapabilityRegistry(
                new ResourceCoordinator(Map.of(SLOT, 1L)));
        registry.register(capability("first", Privacy.SECRET, Integrity.I5,
                CapabilityAvailability.AVAILABLE), 1);
        PhysicalContract<String, String> conflicting = new PhysicalContract<>(
                "other.v1", String.class, String.class, CODEC, CODEC);

        assertThrows(IllegalArgumentException.class, () -> registry.register(
                capability("second", conflicting), 1));
    }

    private static Capability<String, String> capability(String id, Privacy privacy, Integrity integrity, CapabilityAvailability availability) {
        CapabilityManifest<String, String> manifest = new CapabilityManifest<>(new CapabilityId(id), CONTRACT, privacy, Optional.of(integrity),
                PhysicalLocation.LOCAL, Duration.ofMillis(10), List.of(new ResourceClaim(SLOT, 1)));
        return capability(manifest, availability);
    }

    private static Capability<String, String> capability(String id,
            PhysicalContract<String, String> contract) {
        CapabilityManifest<String, String> manifest = new CapabilityManifest<>(
                new CapabilityId(id), contract, Privacy.SECRET, Optional.of(Integrity.I5),
                PhysicalLocation.LOCAL, Duration.ofMillis(10),
                List.of(new ResourceClaim(SLOT, 1)));
        return capability(manifest, CapabilityAvailability.AVAILABLE);
    }

    private static Capability<String, String> capability(
            CapabilityManifest<String, String> manifest,
            CapabilityAvailability availability) {
        return new Capability<>() {
            @Override public CapabilityManifest<String, String> manifest() { return manifest; }
            @Override public CapabilityAvailability availability() { return availability; }
            @Override public String execute(String command, ExecutionContext context) { return command; }
        };
    }

    static WorkRequest<String, String> request(Sensitivity sensitivity, Optional<Risk> risk) {
        return TestWorkRequests.immediate("physical", String.class, sensitivity, risk, 1,
                Duration.ofSeconds(2), PhysicalRetryPolicy.none(),
                PhysicalPreferences.unconstrained());
    }
}
