package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.kernel.reasoning.ResourceClaim;
import io.github.didacll.madre.kernel.reasoning.ResourceId;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ReasoningCapabilityRegistryTest {
    private static final ReasoningCodec<TestReasoningRequests.FixtureComputation> INPUT =
            new ReasoningCodec<>() {
                @Override public byte[] encode(TestReasoningRequests.FixtureComputation value) {
                    return value.value().getBytes(StandardCharsets.UTF_8);
                }
                @Override public TestReasoningRequests.FixtureComputation decode(byte[] bytes) {
                    return new TestReasoningRequests.FixtureComputation(
                            new String(bytes, StandardCharsets.UTF_8));
                }
            };
    private static final ReasoningCodec<String> OUTPUT = new ReasoningCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };
    private static final ReasoningContract<String, TestReasoningRequests.FixtureComputation>
            CONTRACT = new ReasoningContract<>("fixture.v1",
                    TestReasoningRequests.FixtureComputation.class, String.class, INPUT, OUTPUT);
    private static final ResourceId SLOT = new ResourceId("model-slot");

    @Test void selectsOnlyReachableAvailableReasoningMechanismsDeterministically() {
        ReasoningCapabilityRegistry registry = new ReasoningCapabilityRegistry(
                new ResourceCoordinator(Map.of(SLOT, 1L)));
        registry.register(capability("unknown", Privacy.SECRET,
                ReasoningAvailability.UNKNOWN), 100);
        registry.register(capability("unavailable", Privacy.SECRET,
                ReasoningAvailability.UNAVAILABLE), 100);
        registry.register(capability("z", Privacy.SECRET,
                ReasoningAvailability.AVAILABLE), 1);
        registry.register(capability("a", Privacy.LOCAL,
                ReasoningAvailability.AVAILABLE), 1);
        var request = TestReasoningRequests.immediate("reason", Sensitivity.S4, 1,
                Duration.ofSeconds(2), ReasoningRetryPolicy.none(),
                ReasoningPreferences.unconstrained());
        try (var selection = registry.select(request).orElseThrow()) {
            assertEquals("z", selection.capability().manifest().id().value());
            assertTrue(registry.select(request).isEmpty(),
                    "reasoning resource is atomically leased");
        }
        assertTrue(registry.select(request).isPresent());
    }

    @Test void manifestRejectsSystemReservedPrivacy() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReasoningCapabilityManifest<>(
                        new ReasoningCapabilityId("reserved"), CONTRACT,
                        Privacy.SYSTEM_RESERVED, ReasoningLocation.LOCAL,
                        Duration.ofMillis(10), List.of()));
    }

    @Test void oneReasoningJavaContractCannotDriftAcrossRegisteredCapabilities() {
        ReasoningCapabilityRegistry registry = new ReasoningCapabilityRegistry(
                new ResourceCoordinator(Map.of(SLOT, 1L)));
        registry.register(capability("first", Privacy.SECRET,
                ReasoningAvailability.AVAILABLE), 1);
        ReasoningContract<String, TestReasoningRequests.FixtureComputation> conflicting =
                new ReasoningContract<>("other.v1",
                        TestReasoningRequests.FixtureComputation.class, String.class,
                        INPUT, OUTPUT);
        assertThrows(IllegalArgumentException.class, () -> registry.register(
                capability("second", conflicting), 1));
    }

    private static ReasoningCapability<String, TestReasoningRequests.FixtureComputation>
            capability(String id, Privacy privacy, ReasoningAvailability availability) {
        return capability(new ReasoningCapabilityManifest<>(
                new ReasoningCapabilityId(id), CONTRACT, privacy,
                ReasoningLocation.LOCAL, Duration.ofMillis(10),
                List.of(new ResourceClaim(SLOT, 1))), availability);
    }

    private static ReasoningCapability<String, TestReasoningRequests.FixtureComputation>
            capability(String id,
                    ReasoningContract<String, TestReasoningRequests.FixtureComputation> contract) {
        return capability(new ReasoningCapabilityManifest<>(new ReasoningCapabilityId(id),
                contract, Privacy.SECRET, ReasoningLocation.LOCAL,
                Duration.ofMillis(10), List.of(new ResourceClaim(SLOT, 1))),
                ReasoningAvailability.AVAILABLE);
    }

    private static ReasoningCapability<String, TestReasoningRequests.FixtureComputation>
            capability(ReasoningCapabilityManifest<String,
                    TestReasoningRequests.FixtureComputation> manifest,
                    ReasoningAvailability availability) {
        return new ReasoningCapability<>() {
            @Override public ReasoningCapabilityManifest<String,
                    TestReasoningRequests.FixtureComputation> manifest() { return manifest; }
            @Override public ReasoningAvailability availability() { return availability; }
            @Override public String execute(TestReasoningRequests.FixtureComputation computation,
                    ReasoningExecutionContext context) { return computation.value(); }
        };
    }
}
