package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ImmediateReservationTest {
    private static final ResourceId MODEL = new ResourceId("model");
    private static final PhysicalCodec<String> CODEC = new PhysicalCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    @TempDir Path temporary;

    @Test void immediateWorkReservesItsMechanismBeforeTheCallerSubmitsBackgroundWork()
            throws Exception {
        ResourceCoordinator resources = new ResourceCoordinator(Map.of(MODEL, 1L));
        CapabilityRegistry registry = new CapabilityRegistry(resources);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        registry.register(blockingCapability(started, release), 1);
        WorkRequest<String, String> request = request();

        try (KernelExecutionService execution = new KernelExecutionService(registry,
                new SQLiteWorkStore(temporary.resolve("work.sqlite")), Duration.ofHours(1))) {
            var result = execution.execute(request);

            assertTrue(registry.select(request).isEmpty(),
                    "foreground work must own the physical resource before execute returns");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            release.countDown();
            assertEquals("done", result.toCompletableFuture().get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    private static Capability<String, String> blockingCapability(CountDownLatch started,
            CountDownLatch release) {
        PhysicalContract<String, String> contract = new PhysicalContract<>("blocking.v1",
                String.class, String.class, CODEC, CODEC);
        CapabilityManifest<String, String> manifest = new CapabilityManifest<>(
                new CapabilityId("blocking"), contract, Privacy.SECRET, Optional.of(Integrity.I5),
                PhysicalLocation.LOCAL, Duration.ofMillis(10),
                List.of(new ResourceClaim(MODEL, 1)));
        return new Capability<>() {
            @Override public CapabilityManifest<String, String> manifest() { return manifest; }
            @Override public CapabilityAvailability availability() {
                return CapabilityAvailability.AVAILABLE;
            }
            @Override public String execute(String command, ExecutionContext context)
                    throws CapabilityException {
                started.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new CapabilityException(
                                io.github.didacll.madre.sdk.execution.PhysicalFailureCategory.TIMEOUT,
                                "test release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CapabilityException(
                            io.github.didacll.madre.sdk.execution.PhysicalFailureCategory.CANCELLED,
                            "test interrupted", exception);
                }
                return "done";
            }
        };
    }

    private static WorkRequest<String, String> request() {
        return TestWorkRequests.immediate("prompt", String.class, Sensitivity.S5,
                Optional.empty(), 100, Duration.ofSeconds(5), PhysicalRetryPolicy.none(),
                PhysicalPreferences.unconstrained());
    }
}
