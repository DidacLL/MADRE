package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningException;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.kernel.reasoning.ResourceClaim;
import io.github.didacll.madre.kernel.reasoning.ResourceId;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ImmediateReasoningReservationTest {
    private static final ResourceId MODEL = new ResourceId("model");
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

    @TempDir Path temporary;

    @Test void immediateReasoningReservesItsMechanismBeforeBackgroundSelection()
            throws Exception {
        ResourceCoordinator resources = new ResourceCoordinator(Map.of(MODEL, 1L));
        ReasoningCapabilityRegistry registry = new ReasoningCapabilityRegistry(resources);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        registry.register(blockingCapability(started, release), 1);
        var request = TestReasoningRequests.immediate("prompt", Sensitivity.S5, 100,
                Duration.ofSeconds(5), ReasoningRetryPolicy.none(),
                ReasoningPreferences.unconstrained());

        try (KernelReasoningService reasoning = new KernelReasoningService(registry,
                new SQLiteReasoningWorkStore(temporary.resolve("work.sqlite")),
                Duration.ofHours(1))) {
            var result = reasoning.execute(request);
            assertTrue(registry.select(request).isEmpty(),
                    "foreground reasoning must own its resource before execute returns");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            release.countDown();
            assertEquals("done", result.toCompletableFuture().get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    private static ReasoningCapability<String, TestReasoningRequests.FixtureComputation>
            blockingCapability(CountDownLatch started, CountDownLatch release) {
        ReasoningContract<String, TestReasoningRequests.FixtureComputation> contract =
                new ReasoningContract<>("blocking.v1",
                        TestReasoningRequests.FixtureComputation.class, String.class,
                        INPUT, OUTPUT);
        ReasoningCapabilityManifest<String, TestReasoningRequests.FixtureComputation> manifest =
                new ReasoningCapabilityManifest<>(new ReasoningCapabilityId("blocking"),
                        contract, Privacy.SECRET, ReasoningLocation.LOCAL,
                        Duration.ofMillis(10), List.of(new ResourceClaim(MODEL, 1)));
        return new ReasoningCapability<>() {
            @Override public ReasoningCapabilityManifest<String,
                    TestReasoningRequests.FixtureComputation> manifest() { return manifest; }
            @Override public ReasoningAvailability availability() {
                return ReasoningAvailability.AVAILABLE;
            }
            @Override public String execute(TestReasoningRequests.FixtureComputation computation,
                    ReasoningExecutionContext context) throws ReasoningException {
                started.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                                "test release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ReasoningException(ReasoningFailureCategory.CANCELLED,
                            "test interrupted", exception);
                }
                return "done";
            }
        };
    }
}
