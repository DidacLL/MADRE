package io.github.didacll.madre.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InferenceKernelTest {
    private static final URI ENDPOINT_A = URI.create("http://engine-a.example/v1/");
    private static final URI ENDPOINT_B = URI.create("http://engine-b.example/v1/");

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactPhysicalConstraintsAreHardFilters() throws Exception {
        AtomicReference<ChatCompletionOutput> accepted = new AtomicReference<>();
        TestEngine wrongProvider = engine(
                "a-wrong-provider", "other", "wanted-model", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 4096, 10, List.of());
        TestEngine wrongModel = engine(
                "b-wrong-model", "wanted-provider", "other", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 4096, 10, List.of());
        TestEngine wrongEndpoint = engine(
                "c-wrong-endpoint", "wanted-provider", "wanted-model", ENDPOINT_B,
                EngineAvailability.AVAILABLE, 4096, 10, List.of());
        TestEngine exact = engine(
                "wanted", "wanted-provider", "wanted-model", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 4096, 10, List.of());

        try (InferenceKernel kernel = kernel("exact", Map.of())) {
            kernel.register(wrongProvider);
            kernel.register(wrongModel);
            kernel.register(wrongEndpoint);
            kernel.register(exact);
            InferenceRequirements requirements = requirements(
                    new TechnicalCapabilityRequirement.ChatCompletion(2048),
                    Optional.of(exact.id()),
                    Optional.of("wanted-provider"),
                    Optional.of("wanted-model"),
                    Optional.of(ENDPOINT_A),
                    Optional.of(Duration.ofMillis(50)),
                    List.of());
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work =
                    InferenceWork.create(InferenceTypes.CHAT_COMPLETION, requirements);

            kernel.submit(work, input("exact"), accepting(accepted));

            assertEquals(WorkStatus.DELIVERED,
                    kernel.await(work.id(), Duration.ofSeconds(3)).status());
            assertEquals(exact.id(), kernel.snapshot(work.id()).lastEngine().orElseThrow());
            assertEquals("wanted-result", accepted.get().text());
            assertEquals(0, wrongProvider.executions.get());
            assertEquals(0, wrongModel.executions.get());
            assertEquals(0, wrongEndpoint.executions.get());
            assertEquals(1, exact.executions.get());
        }
    }

    @Test
    void unavailableIncompatibleAndTooSlowEnginesAreRejected() throws Exception {
        AtomicReference<ChatCompletionOutput> accepted = new AtomicReference<>();
        TestEngine unavailable = engine(
                "a-offline", "p", "m", ENDPOINT_A,
                EngineAvailability.OFFLINE, 8192, 5, List.of());
        TestEngine insufficientContext = engine(
                "b-small", "p", "m", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 1024, 5, List.of());
        TestEngine tooSlow = engine(
                "c-slow", "p", "m", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 8192, 100, List.of());
        TestEngine feasible = engine(
                "d-feasible", "p", "m", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 8192, 10, List.of());

        try (InferenceKernel kernel = kernel("feasible", Map.of())) {
            kernel.register(unavailable);
            kernel.register(insufficientContext);
            kernel.register(tooSlow);
            kernel.register(feasible);
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work =
                    InferenceWork.create(
                            InferenceTypes.CHAT_COMPLETION,
                            requirements(
                                    new TechnicalCapabilityRequirement.ChatCompletion(4096),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.of(Duration.ofMillis(50)),
                                    List.of()));

            kernel.submit(work, input("constraints"), accepting(accepted));

            assertEquals(WorkStatus.DELIVERED,
                    kernel.await(work.id(), Duration.ofSeconds(3)).status());
            assertEquals(feasible.id(), kernel.snapshot(work.id()).lastEngine().orElseThrow());
        }
    }

    @Test
    void resourceReservationIsFeasibilityAndFinalChoiceIsEngineIdOrder() throws Exception {
        ResourceId accelerator = new ResourceId("accelerator");
        TestEngine firstButTooLarge = engine(
                "a", "p", "m", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 4096, 10,
                List.of(new ResourceClaim(accelerator, 2)));
        TestEngine secondFeasible = engine(
                "b", "p", "m", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 4096, 10,
                List.of(new ResourceClaim(accelerator, 1)));
        TestEngine laterFeasible = engine(
                "c", "p", "m", ENDPOINT_A,
                EngineAvailability.AVAILABLE, 4096, 1,
                List.of(new ResourceClaim(accelerator, 1)));

        try (InferenceKernel kernel = kernel("resources", Map.of(accelerator, 2L))) {
            kernel.register(laterFeasible);
            kernel.register(secondFeasible);
            kernel.register(firstButTooLarge);
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work =
                    InferenceWork.create(
                            InferenceTypes.CHAT_COMPLETION,
                            requirements(
                                    new TechnicalCapabilityRequirement.None(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    List.of(new ResourceClaim(accelerator, 1))));

            kernel.submit(work, input("resources"), accepting(new AtomicReference<>()));

            assertEquals(WorkStatus.DELIVERED,
                    kernel.await(work.id(), Duration.ofSeconds(3)).status());
            assertEquals(secondFeasible.id(),
                    kernel.snapshot(work.id()).lastEngine().orElseThrow());
            assertEquals(0, firstButTooLarge.executions.get());
            assertEquals(1, secondFeasible.executions.get());
            assertEquals(0, laterFeasible.executions.get());
        }
    }

    @Test
    void inspectionReportsFactualEngineState() {
        TestEngine engine = engine(
                "inspect", "provider-x", "model-y",
                URI.create("https://configured.example/inference/"),
                EngineAvailability.BUSY, 32768, 25, List.of());
        try (InferenceKernel kernel = kernel("inspect", Map.of())) {
            kernel.register(engine);

            EngineSnapshot snapshot = kernel.engineSnapshots().getFirst();

            assertEquals(engine.id(), snapshot.id());
            assertEquals(InferenceTypes.CHAT_COMPLETION.id(), snapshot.inferenceType());
            assertEquals("provider-x", snapshot.characteristics().provider());
            assertEquals("model-y", snapshot.characteristics().model());
            assertEquals(URI.create("https://configured.example/inference/"),
                    snapshot.characteristics().endpoint());
            assertEquals(EngineAvailability.BUSY, snapshot.availability());
        }
    }

    @Test
    void exactEndpointAndCurrentCapabilitySurviveRestartValidation() {
        Path database = temporaryDirectory.resolve("restart.db");
        Instant eligibleAt = Instant.now().plusSeconds(60);
        InferenceRequirements requirements = new InferenceRequirements(
                new TechnicalCapabilityRequirement.ChatCompletion(8192),
                Urgency.NORMAL,
                eligibleAt,
                Optional.of(eligibleAt.plusSeconds(30)),
                Duration.ofSeconds(2),
                RetryPolicy.noRetry(),
                Optional.of(Duration.ofMillis(500)),
                Optional.of(new EngineId("chosen")),
                Optional.of("provider"),
                Optional.of("model"),
                Optional.of(ENDPOINT_A),
                List.of(new ResourceClaim(new ResourceId("ram"), 2)));
        InferenceWork<ChatCompletionInput, ChatCompletionOutput> work =
                InferenceWork.create(InferenceTypes.CHAT_COMPLETION, requirements);

        try (InferenceKernel kernel = new InferenceKernel(database, 1, Map.of())) {
            kernel.submit(work, input("transient"), accepting(new AtomicReference<>()));
        }
        try (InferenceKernel restarted = new InferenceKernel(database, 1, Map.of())) {
            assertEquals(List.of(work.id()), restarted.recoverableWork());
            assertEquals(WorkStatus.NEEDS_INPUT, restarted.snapshot(work.id()).status());
            restarted.reattach(work, input("transient"), accepting(new AtomicReference<>()));

            InferenceRequirements changed = new InferenceRequirements(
                    requirements.capability(),
                    requirements.urgency(),
                    requirements.eligibleAt(),
                    requirements.deadline(),
                    requirements.timeout(),
                    requirements.retryPolicy(),
                    requirements.maximumExpectedLatency(),
                    requirements.exactEngine(),
                    requirements.exactProvider(),
                    requirements.exactModel(),
                    Optional.of(ENDPOINT_B),
                    requirements.resources());
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> mismatch =
                    new InferenceWork<>(
                            work.id(), work.type(), changed, work.createdAt());
            assertThrows(KernelException.class,
                    () -> restarted.reattach(
                            mismatch, input("transient"), accepting(new AtomicReference<>())));
        }
    }

    @Test
    void ledgerContainsOnlyTechnicalStateAndNoInferenceContent() throws Exception {
        Path database = temporaryDirectory.resolve("ledger.db");
        String secret = "PROMPT-MUST-NOT-BE-PERSISTED-72f3";
        try (InferenceKernel kernel = new InferenceKernel(database, 1, Map.of())) {
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work =
                    InferenceWork.create(
                            InferenceTypes.CHAT_COMPLETION,
                            requirements(
                                    new TechnicalCapabilityRequirement.None(),
                                    Optional.of(new EngineId("missing")),
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.of(ENDPOINT_A),
                                    Optional.empty(),
                                    List.of()));
            kernel.submit(work, input(secret), accepting(new AtomicReference<>()));
            kernel.await(work.id(), Duration.ofSeconds(3));
        }

        String bytes = new String(
                Files.readAllBytes(database), StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains(secret));
        try (var connection = DriverManager.getConnection(
                     "jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(inference_work)")) {
            List<String> columns = new ArrayList<>();
            while (result.next()) {
                columns.add(result.getString("name"));
            }
            assertFalse(columns.contains("placement"));
            assertFalse(columns.contains("input"));
            assertFalse(columns.contains("output"));
            assertFalse(columns.contains("agent"));
            assertFalse(columns.contains("module"));
            assertFalse(columns.contains("material"));
        }
    }

    private InferenceKernel kernel(String name, Map<ResourceId, Long> capacity) {
        return new InferenceKernel(
                temporaryDirectory.resolve(name + ".db"), 1, capacity);
    }

    private static InferenceRequirements requirements(
            TechnicalCapabilityRequirement capability,
            Optional<EngineId> exactEngine,
            Optional<String> exactProvider,
            Optional<String> exactModel,
            Optional<URI> exactEndpoint,
            Optional<Duration> maximumLatency,
            List<ResourceClaim> resources) {
        return new InferenceRequirements(
                capability,
                Urgency.NORMAL,
                Instant.now(),
                Optional.empty(),
                Duration.ofSeconds(2),
                RetryPolicy.noRetry(),
                maximumLatency,
                exactEngine,
                exactProvider,
                exactModel,
                exactEndpoint,
                resources);
    }

    private static ChatCompletionInput input(String text) {
        return new ChatCompletionInput(
                List.of(new ChatMessage(ChatMessage.Role.USER, text)),
                OptionalInt.empty(),
                List.of());
    }

    private static InferenceResultReceiver<ChatCompletionOutput> accepting(
            AtomicReference<ChatCompletionOutput> target) {
        return new InferenceResultReceiver<>() {
            @Override
            public DeliveryAcknowledgement accept(
                    WorkId ignored, ChatCompletionOutput result) {
                target.set(result);
                return DeliveryAcknowledgement.DURABLY_ACCEPTED;
            }

            @Override
            public boolean alreadyAccepted(WorkId ignored) {
                return target.get() != null;
            }
        };
    }

    private static TestEngine engine(
            String id,
            String provider,
            String model,
            URI endpoint,
            EngineAvailability availability,
            int contextTokens,
            long latencyMillis,
            List<ResourceClaim> resourceClaims) {
        return new TestEngine(
                new EngineId(id),
                new EngineCharacteristics(
                        provider,
                        model,
                        endpoint,
                        Duration.ofMillis(latencyMillis),
                        Optional.of(
                                new EngineCharacteristics.ChatCompletionCapability(
                                        contextTokens))),
                availability,
                resourceClaims);
    }

    private static final class TestEngine
            implements InferenceEngine<ChatCompletionInput, ChatCompletionOutput> {
        private final EngineId id;
        private final EngineCharacteristics characteristics;
        private final EngineAvailability availability;
        private final List<ResourceClaim> resourceClaims;
        private final AtomicInteger executions = new AtomicInteger();

        private TestEngine(
                EngineId id,
                EngineCharacteristics characteristics,
                EngineAvailability availability,
                List<ResourceClaim> resourceClaims) {
            this.id = id;
            this.characteristics = characteristics;
            this.availability = availability;
            this.resourceClaims = List.copyOf(resourceClaims);
        }

        @Override
        public EngineId id() {
            return id;
        }

        @Override
        public InferenceType<ChatCompletionInput, ChatCompletionOutput> type() {
            return InferenceTypes.CHAT_COMPLETION;
        }

        @Override
        public EngineCharacteristics characteristics() {
            return characteristics;
        }

        @Override
        public EngineAvailability availability() {
            return availability;
        }

        @Override
        public List<ResourceClaim> resourceClaims(
                InferenceWork<ChatCompletionInput, ChatCompletionOutput> work) {
            return resourceClaims;
        }

        @Override
        public ChatCompletionOutput execute(
                ChatCompletionInput input, EngineExecution execution) {
            executions.incrementAndGet();
            return new ChatCompletionOutput(
                    id.value() + "-result",
                    ChatCompletionOutput.FinishReason.COMPLETE,
                    ChatCompletionOutput.TokenUsage.unavailable());
        }
    }
}
