package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.ReasoningExecutionException;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReasoningInstallationFailureTest {
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
            CONTRACT = new ReasoningContract<>("installation-failure.v1",
                    TestReasoningRequests.FixtureComputation.class, String.class, INPUT, OUTPUT);

    @Test void duplicateCapabilityIdentityIsRejected() {
        ReasoningCapabilityRegistry registry = new ReasoningCapabilityRegistry(
                new ResourceCoordinator(Map.of()));
        registry.register(capability("duplicate", ReasoningAvailability.AVAILABLE), 0);
        assertThrows(IllegalStateException.class,
                () -> registry.register(capability("duplicate", ReasoningAvailability.AVAILABLE), 0));
    }

    @Test void unavailableMechanismFailsImmediateExecution(@TempDir Path directory)
            throws Exception {
        ReasoningCapabilityRegistry registry = new ReasoningCapabilityRegistry(
                new ResourceCoordinator(Map.of()));
        registry.register(capability("unavailable", ReasoningAvailability.UNAVAILABLE), 0);
        try (SQLiteReasoningWorkStore store = new SQLiteReasoningWorkStore(
                    directory.resolve("unavailable.sqlite"));
                KernelReasoningService service = new KernelReasoningService(registry, store,
                        Duration.ofHours(1))) {
            var request = TestReasoningRequests.immediate("cannot-run", Sensitivity.S1, 0,
                    Duration.ofSeconds(1), ReasoningRetryPolicy.none(),
                    ReasoningPreferences.unconstrained());
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> service.execute(request).toCompletableFuture().get());
            ReasoningExecutionException reasoning =
                    (ReasoningExecutionException) failure.getCause();
            assertEquals(ReasoningFailureCategory.UNAVAILABLE, reasoning.category());
        }
    }

    private static ReasoningCapability<String, TestReasoningRequests.FixtureComputation>
            capability(String id, ReasoningAvailability availability) {
        ReasoningCapabilityManifest<String, TestReasoningRequests.FixtureComputation> manifest =
                new ReasoningCapabilityManifest<>(new ReasoningCapabilityId(id), CONTRACT,
                        Privacy.SECRET, ReasoningLocation.LOCAL, Duration.ofMillis(1), List.of());
        return new ReasoningCapability<>() {
            @Override public ReasoningCapabilityManifest<String,
                    TestReasoningRequests.FixtureComputation> manifest() {
                return manifest;
            }
            @Override public ReasoningAvailability availability() { return availability; }
            @Override public String execute(TestReasoningRequests.FixtureComputation computation,
                    ReasoningExecutionContext context) {
                return computation.value();
            }
        };
    }
}
