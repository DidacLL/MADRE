package io.github.didacll.madre.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InferenceKernelTest {
    @TempDir Path temporaryDirectory;

    @Test
    void kernelSelectsLocalDemandingEngineAndHandsResultToRuntime() throws Exception {
        Path database = temporaryDirectory.resolve("kernel.db");
        TestEngine remote = new TestEngine("remote-fast", EngineLocation.REMOTE, true, 100, 1);
        TestEngine localEasy = new TestEngine("local-easy", EngineLocation.LOCAL, false, 100, 20);
        TestEngine localHard = new TestEngine("local-hard", EngineLocation.LOCAL, true, 100, 5);
        AtomicReference<TextInferenceOutput> accepted = new AtomicReference<>();
        try (InferenceKernel kernel = new InferenceKernel(database, 1, Map.of())) {
            kernel.register(remote); kernel.register(localEasy); kernel.register(localHard);
            InferenceRequirements requirements = requirements(Placement.LOCAL_ONLY,
                    new TechnicalCapabilityRequirement.Text(true, 32), Optional.empty(), Instant.now());
            InferenceWork<TextInferenceInput, TextInferenceOutput> work =
                    InferenceWork.create(InferenceTypes.TEXT_GENERATION, requirements);
            kernel.submit(work, input("private-prompt"), accepting(accepted));

            WorkSnapshot done = kernel.await(work.id(), Duration.ofSeconds(3));
            assertEquals(WorkStatus.DELIVERED, done.status());
            assertEquals(new EngineId("local-hard"), done.lastEngine().orElseThrow());
            assertEquals("local-hard-result", accepted.get().text());
            assertEquals(0, remote.executions.get());
            assertEquals(0, localEasy.executions.get());
            assertEquals(1, localHard.executions.get());
        }
    }

    @Test
    void exactUnavailableEngineFailsHonestly() throws Exception {
        try (InferenceKernel kernel = new InferenceKernel(temporaryDirectory.resolve("exact.db"), 1, Map.of())) {
            InferenceRequirements requirements = requirements(Placement.LOCAL_OR_REMOTE,
                    new TechnicalCapabilityRequirement.None(), Optional.of(new EngineId("not-installed")), Instant.now());
            InferenceWork<TextInferenceInput, TextInferenceOutput> work =
                    InferenceWork.create(InferenceTypes.TEXT_GENERATION, requirements);
            kernel.submit(work, input("content"), accepting(new AtomicReference<>()));
            WorkSnapshot failed = kernel.await(work.id(), Duration.ofSeconds(3));
            assertEquals(WorkStatus.FAILED, failed.status());
            assertEquals(TechnicalFailure.Category.NO_ENGINE, failed.failure().orElseThrow().category());
        }
    }

    @Test
    void restartRequiresRuntimeToReattachTransientInput() throws Exception {
        Path database = temporaryDirectory.resolve("restart.db");
        InferenceRequirements requirements = requirements(Placement.LOCAL_OR_REMOTE,
                new TechnicalCapabilityRequirement.Text(false, 1), Optional.empty(), Instant.now().plusMillis(500));
        InferenceWork<TextInferenceInput, TextInferenceOutput> work =
                InferenceWork.create(InferenceTypes.TEXT_GENERATION, requirements);
        try (InferenceKernel first = new InferenceKernel(database, 1, Map.of())) {
            first.submit(work, input("reattach-secret"), accepting(new AtomicReference<>()));
        }

        AtomicReference<TextInferenceOutput> accepted = new AtomicReference<>();
        try (InferenceKernel restarted = new InferenceKernel(database, 1, Map.of())) {
            assertEquals(List.of(work.id()), restarted.recoverableWork());
            assertEquals(WorkStatus.NEEDS_INPUT, restarted.snapshot(work.id()).status());
            restarted.register(new TestEngine("local", EngineLocation.LOCAL, true, 10, 1));
            restarted.reattach(work, input("reattach-secret"), accepting(accepted));
            assertEquals(WorkStatus.DELIVERED,
                    restarted.await(work.id(), Duration.ofSeconds(3)).status());
            assertEquals("local-result", accepted.get().text());
        }
    }

    @Test
    void uncertainDeliveryIsNeverSilentlyRepeatedAndCanBeReconciled() throws Exception {
        Path database = temporaryDirectory.resolve("delivery.db");
        InferenceWork<TextInferenceInput, TextInferenceOutput> work = InferenceWork.create(
                InferenceTypes.TEXT_GENERATION,
                requirements(Placement.LOCAL_OR_REMOTE, new TechnicalCapabilityRequirement.None(),
                        Optional.empty(), Instant.now()));
        TestEngine engine = new TestEngine("engine", EngineLocation.LOCAL, true, 10, 1);
        InferenceResultReceiver<TextInferenceOutput> uncertain = new InferenceResultReceiver<>() {
            @Override public DeliveryAcknowledgement accept(WorkId ignored, TextInferenceOutput result) {
                throw new IllegalStateException("connection lost after possible commit");
            }
            @Override public boolean alreadyAccepted(WorkId ignored) { return false; }
        };
        try (InferenceKernel kernel = new InferenceKernel(database, 1, Map.of())) {
            kernel.register(engine);
            kernel.submit(work, input("do-not-repeat"), uncertain);
            assertEquals(WorkStatus.OUTCOME_UNKNOWN,
                    kernel.await(work.id(), Duration.ofSeconds(3)).status());
            assertEquals(1, engine.executions.get());
        }

        try (InferenceKernel restarted = new InferenceKernel(database, 1, Map.of())) {
            InferenceResultReceiver<TextInferenceOutput> confirmed = new InferenceResultReceiver<>() {
                @Override public DeliveryAcknowledgement accept(WorkId ignored, TextInferenceOutput result) {
                    throw new AssertionError("Result must not be executed and delivered again");
                }
                @Override public boolean alreadyAccepted(WorkId ignored) { return true; }
            };
            assertEquals(WorkStatus.DELIVERED,
                    restarted.reattach(work, input("do-not-repeat"), confirmed).status());
        }
    }

    @Test
    void ledgerContainsOnlyTechnicalColumnsAndNoInferenceContent() throws Exception {
        Path database = temporaryDirectory.resolve("privacy.db");
        String secret = "PROMPT-MUST-NOT-BE-PERSISTED-72f3";
        try (InferenceKernel kernel = new InferenceKernel(database, 1, Map.of())) {
            InferenceWork<TextInferenceInput, TextInferenceOutput> work = InferenceWork.create(
                    InferenceTypes.TEXT_GENERATION,
                    requirements(Placement.LOCAL_OR_REMOTE, new TechnicalCapabilityRequirement.None(),
                            Optional.of(new EngineId("missing")), Instant.now()));
            kernel.submit(work, input(secret), accepting(new AtomicReference<>()));
            kernel.await(work.id(), Duration.ofSeconds(3));
        }
        String bytes = new String(Files.readAllBytes(database), StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains(secret));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(inference_work)")) {
            List<String> columns = new ArrayList<>();
            while (result.next()) columns.add(result.getString("name"));
            assertFalse(columns.contains("input"));
            assertFalse(columns.contains("output"));
            assertFalse(columns.contains("prompt"));
            assertFalse(columns.contains("agent"));
            assertFalse(columns.contains("module"));
            assertFalse(columns.contains("operation"));
            assertFalse(columns.contains("material"));
        }
    }

    @Test
    void reattachmentRejectsChangedTechnicalRequirements() throws Exception {
        Path database = temporaryDirectory.resolve("mismatch.db");
        InferenceWork<TextInferenceInput, TextInferenceOutput> original = InferenceWork.create(
                InferenceTypes.TEXT_GENERATION,
                requirements(Placement.LOCAL_ONLY, new TechnicalCapabilityRequirement.None(),
                        Optional.empty(), Instant.now().plusSeconds(5)));
        try (InferenceKernel kernel = new InferenceKernel(database, 1, Map.of())) {
            kernel.submit(original, input("x"), accepting(new AtomicReference<>()));
        }
        InferenceWork<TextInferenceInput, TextInferenceOutput> changed = new InferenceWork<>(original.id(),
                original.type(), requirements(Placement.LOCAL_OR_REMOTE, new TechnicalCapabilityRequirement.None(),
                        Optional.empty(), original.requirements().eligibleAt()), original.createdAt());
        try (InferenceKernel restarted = new InferenceKernel(database, 1, Map.of())) {
            assertThrows(KernelException.class,
                    () -> restarted.reattach(changed, input("x"), accepting(new AtomicReference<>())));
        }
    }

    private static InferenceRequirements requirements(Placement placement,
            TechnicalCapabilityRequirement capability, Optional<EngineId> engine, Instant eligibleAt) {
        return new InferenceRequirements(placement, capability, Urgency.NORMAL, eligibleAt, Optional.empty(),
                Duration.ofSeconds(2), RetryPolicy.noRetry(), Optional.of(Duration.ofMillis(500)), engine,
                Optional.empty(), Optional.empty(), List.of());
    }

    private static TextInferenceInput input(String text) {
        return new TextInferenceInput(List.of(new TextMessage(TextMessage.Role.USER, text)),
                Optional.empty(), OptionalInt.empty());
    }

    private static InferenceResultReceiver<TextInferenceOutput> accepting(
            AtomicReference<TextInferenceOutput> target) {
        return new InferenceResultReceiver<>() {
            @Override public DeliveryAcknowledgement accept(WorkId ignored, TextInferenceOutput result) {
                target.set(result); return DeliveryAcknowledgement.DURABLY_ACCEPTED;
            }
            @Override public boolean alreadyAccepted(WorkId ignored) { return target.get() != null; }
        };
    }

    private static final class TestEngine implements InferenceEngine<TextInferenceInput, TextInferenceOutput> {
        private final EngineId id;
        private final EngineCharacteristics characteristics;
        private final AtomicInteger executions = new AtomicInteger();

        private TestEngine(String id, EngineLocation location, boolean demanding, long latencyMillis, int preference) {
            this.id = new EngineId(id);
            characteristics = new EngineCharacteristics(location, "test-provider", id, Duration.ofMillis(latencyMillis),
                    preference, Optional.of(new EngineCharacteristics.TextCapability(demanding, 4096)),
                    Optional.empty(), Optional.empty());
        }

        @Override public EngineId id() { return id; }
        @Override public InferenceType<TextInferenceInput, TextInferenceOutput> type() { return InferenceTypes.TEXT_GENERATION; }
        @Override public EngineCharacteristics characteristics() { return characteristics; }
        @Override public EngineAvailability availability() { return EngineAvailability.AVAILABLE; }
        @Override public TextInferenceOutput execute(TextInferenceInput input, EngineExecution execution) {
            executions.incrementAndGet();
            return new TextInferenceOutput(id.value() + "-result", TextInferenceOutput.FinishReason.COMPLETE,
                    TextInferenceOutput.TokenUsage.unavailable());
        }
    }
}
