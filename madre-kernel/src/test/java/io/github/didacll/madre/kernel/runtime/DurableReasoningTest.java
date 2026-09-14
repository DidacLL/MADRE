package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DurableReasoningTest {
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
            CONTRACT = new ReasoningContract<>("durable-test.v1",
                    TestReasoningRequests.FixtureComputation.class, String.class, INPUT, OUTPUT);

    @Test void queuedInputAndPendingResultSurviveSeparateKernelRestarts(@TempDir Path directory)
            throws Exception {
        Path database = directory.resolve("kernel.sqlite");
        WorkId id;
        try (SQLiteReasoningWorkStore store = new SQLiteReasoningWorkStore(database);
                KernelReasoningService service = new KernelReasoningService(
                        registry(ReasoningAvailability.UNAVAILABLE, new AtomicInteger()),
                        store, Duration.ofHours(1))) {
            id = service.submit(request("persist me", 2));
            assertEquals(WorkState.QUEUED, service.inspect(id).orElseThrow().state());
        }
        try (SQLiteReasoningWorkStore store = new SQLiteReasoningWorkStore(database);
                KernelReasoningService service = new KernelReasoningService(
                        registry(ReasoningAvailability.AVAILABLE, new AtomicInteger()),
                        store, Duration.ofHours(1))) {
            await(service, id, WorkState.SUCCEEDED);
        }
        try (SQLiteReasoningWorkStore store = new SQLiteReasoningWorkStore(database);
                KernelReasoningService service = new KernelReasoningService(
                        registry(ReasoningAvailability.AVAILABLE, new AtomicInteger()),
                        store, Duration.ofHours(1))) {
            assertEquals("PERSIST ME", service.collect(id, String.class).orElseThrow());
            assertTrue(service.acknowledge(id));
            assertTrue(service.inspect(id).isEmpty());
        }
    }

    @Test void retriesReasoningFailureAndSupportsCancellation(@TempDir Path directory)
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Path database = directory.resolve("retry.sqlite");
        try (SQLiteReasoningWorkStore store = new SQLiteReasoningWorkStore(database);
                KernelReasoningService service = new KernelReasoningService(
                        registry(ReasoningAvailability.AVAILABLE, calls), store,
                        Duration.ofHours(1))) {
            WorkId retry = service.submit(request("retry", 2));
            await(service, retry, WorkState.SUCCEEDED);
            assertEquals(2, service.inspect(retry).orElseThrow().attempts());
            WorkId cancelled = service.submit(TestReasoningRequests.durable("later",
                    Sensitivity.S1, 0, Instant.now().plusSeconds(60), Duration.ofSeconds(2),
                    ReasoningRetryPolicy.none(), Optional.empty(),
                    ReasoningPreferences.unconstrained()));
            assertTrue(service.cancel(cancelled));
            assertEquals(WorkState.CANCELLED, service.inspect(cancelled).orElseThrow().state());
            assertFalse(service.collect(cancelled, String.class).isPresent());
        }
    }

    @Test void immediateExecutionUsesSameSelectionAndRetryPath(@TempDir Path directory)
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (SQLiteReasoningWorkStore store = new SQLiteReasoningWorkStore(
                    directory.resolve("immediate.sqlite"));
                KernelReasoningService service = new KernelReasoningService(
                        registry(ReasoningAvailability.AVAILABLE, calls), store,
                        Duration.ofHours(1))) {
            var request = TestReasoningRequests.immediate("retry", Sensitivity.S1, 1,
                    Duration.ofSeconds(2), new ReasoningRetryPolicy(2, Duration.ZERO),
                    ReasoningPreferences.unconstrained());
            assertEquals("RETRY", service.execute(request).toCompletableFuture().get());
            assertEquals(2, calls.get());
        }
    }

    private static ReasoningCapabilityRegistry registry(ReasoningAvailability availability,
            AtomicInteger calls) {
        ReasoningCapabilityRegistry registry = new ReasoningCapabilityRegistry(
                new ResourceCoordinator(Map.of()));
        ReasoningCapabilityManifest<String, TestReasoningRequests.FixtureComputation> manifest =
                new ReasoningCapabilityManifest<>(new ReasoningCapabilityId("fixture"),
                        CONTRACT, Privacy.SECRET, ReasoningLocation.LOCAL,
                        Duration.ofMillis(1), List.of());
        registry.register(new ReasoningCapability<String,
                TestReasoningRequests.FixtureComputation>() {
            @Override public ReasoningCapabilityManifest<String,
                    TestReasoningRequests.FixtureComputation> manifest() { return manifest; }
            @Override public ReasoningAvailability availability() { return availability; }
            @Override public String execute(TestReasoningRequests.FixtureComputation computation,
                    ReasoningExecutionContext context) throws ReasoningException {
                if (calls.incrementAndGet() == 1 && computation.value().equals("retry")) {
                    throw new ReasoningException(ReasoningFailureCategory.CONNECTION,
                            "transient fixture failure");
                }
                return computation.value().toUpperCase(java.util.Locale.ROOT);
            }
        }, 0);
        return registry;
    }

    private static ReasoningRequest<String, TestReasoningRequests.FixtureComputation> request(
            String computation, int attempts) {
        return TestReasoningRequests.durable(computation, Sensitivity.S1, 1, Instant.now(),
                Duration.ofSeconds(2), new ReasoningRetryPolicy(attempts,
                        Duration.ofMillis(10)), Optional.empty(),
                ReasoningPreferences.unconstrained());
    }

    private static void await(KernelReasoningService service, WorkId id, WorkState state)
            throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)
                && service.inspect(id).orElseThrow().state() != state) {
            Thread.sleep(20);
        }
        assertEquals(state, service.inspect(id).orElseThrow().state());
    }
}
