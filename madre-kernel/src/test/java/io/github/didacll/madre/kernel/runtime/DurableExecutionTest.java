package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalCodec;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.sdk.execution.ExecutionMode;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.PhysicalRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.identity.ModuleId;
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

final class DurableExecutionTest {
    private static final PhysicalCodec<String> CODEC = new PhysicalCodec<>() {
        @Override public byte[] encode(String value) { return value.getBytes(StandardCharsets.UTF_8); }
        @Override public String decode(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
    };
    private static final PhysicalContract<String, String> CONTRACT = new PhysicalContract<>("durable-test.v1", String.class, String.class, CODEC, CODEC);

    @Test void queuedInputAndPendingResultSurviveSeparateKernelRestarts(@TempDir Path directory) throws Exception {
        Path database = directory.resolve("kernel.sqlite"); WorkId id;
        try (SQLiteWorkStore store = new SQLiteWorkStore(database);
                KernelExecutionService service = new KernelExecutionService(registry(CapabilityAvailability.UNAVAILABLE, new AtomicInteger()), store, Duration.ofHours(1))) {
            id = service.submit(request("persist me", 2));
            assertEquals(WorkState.QUEUED, service.inspect(id).orElseThrow().state());
        }
        try (SQLiteWorkStore store = new SQLiteWorkStore(database);
                KernelExecutionService service = new KernelExecutionService(registry(CapabilityAvailability.AVAILABLE, new AtomicInteger()), store, Duration.ofHours(1))) {
            await(service, id, WorkState.SUCCEEDED);
        }
        try (SQLiteWorkStore store = new SQLiteWorkStore(database);
                KernelExecutionService service = new KernelExecutionService(registry(CapabilityAvailability.AVAILABLE, new AtomicInteger()), store, Duration.ofHours(1))) {
            assertEquals("PERSIST ME", service.collect(id, String.class).orElseThrow());
            assertTrue(service.acknowledge(id)); assertTrue(service.inspect(id).isEmpty());
        }
    }

    @Test void retriesPhysicalFailureAndSupportsCancellation(@TempDir Path directory) throws Exception {
        AtomicInteger calls = new AtomicInteger(); Path database = directory.resolve("retry.sqlite");
        try (SQLiteWorkStore store = new SQLiteWorkStore(database);
                KernelExecutionService service = new KernelExecutionService(registry(CapabilityAvailability.AVAILABLE, calls), store, Duration.ofHours(1))) {
            WorkId retry = service.submit(request("retry", 2)); await(service, retry, WorkState.SUCCEEDED);
            assertEquals(2, service.inspect(retry).orElseThrow().attempts());
            WorkId cancelled = service.submit(new WorkRequest<>(new ModuleId("test.owner"), "later", String.class, Sensitivity.S1,
                    Optional.empty(), ExecutionMode.DURABLE, 0, Instant.now().plusSeconds(60), Duration.ofSeconds(2),
                    PhysicalRetryPolicy.none(), Optional.empty(), PhysicalPreferences.unconstrained()));
            assertTrue(service.cancel(cancelled)); assertEquals(WorkState.CANCELLED, service.inspect(cancelled).orElseThrow().state());
            assertFalse(service.collect(cancelled, String.class).isPresent());
        }
    }

    @Test void immediateExecutionUsesTheSameSelectionAndPhysicalRetryPath(@TempDir Path directory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (SQLiteWorkStore store = new SQLiteWorkStore(directory.resolve("immediate.sqlite"));
                KernelExecutionService service = new KernelExecutionService(registry(CapabilityAvailability.AVAILABLE, calls), store, Duration.ofHours(1))) {
            WorkRequest<String, String> request = new WorkRequest<>(new ModuleId("test.owner"), "retry", String.class, Sensitivity.S1,
                    Optional.empty(), ExecutionMode.IMMEDIATE, 1, Instant.now(), Duration.ofSeconds(2),
                    new PhysicalRetryPolicy(2, Duration.ZERO), Optional.empty(), PhysicalPreferences.unconstrained());
            assertEquals("RETRY", service.execute(request).toCompletableFuture().get()); assertEquals(2, calls.get());
        }
    }

    private static CapabilityRegistry registry(CapabilityAvailability availability, AtomicInteger calls) {
        CapabilityRegistry registry = new CapabilityRegistry(new ResourceCoordinator(Map.of()));
        CapabilityManifest<String, String> manifest = new CapabilityManifest<>(new CapabilityId("fixture"), CONTRACT, Privacy.P5, Optional.of(Integrity.I5),
                PhysicalLocation.LOCAL, Duration.ofMillis(1), List.of());
        registry.register(new Capability<String, String>() {
            @Override public CapabilityManifest<String, String> manifest() { return manifest; }
            @Override public CapabilityAvailability availability() { return availability; }
            @Override public String execute(String command, ExecutionContext context) throws CapabilityException {
                if (calls.incrementAndGet() == 1 && command.equals("retry"))
                    throw new CapabilityException(PhysicalFailureCategory.CONNECTION, "transient fixture failure");
                return command.toUpperCase(java.util.Locale.ROOT);
            }
        }, 0);
        return registry;
    }

    private static WorkRequest<String, String> request(String command, int attempts) {
        return new WorkRequest<>(new ModuleId("test.owner"), command, String.class, Sensitivity.S1, Optional.empty(), ExecutionMode.DURABLE,
                1, Instant.now(), Duration.ofSeconds(2), new PhysicalRetryPolicy(attempts, Duration.ofMillis(10)), Optional.empty(), PhysicalPreferences.unconstrained());
    }
    private static void await(KernelExecutionService service, WorkId id, WorkState state) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline) && service.inspect(id).orElseThrow().state() != state) Thread.sleep(20);
        assertEquals(state, service.inspect(id).orElseThrow().state());
    }
}
