package io.github.didacll.madre.runtime;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationMessage;
import io.github.didacll.madre.generation.TextGenerationResult;
import io.github.didacll.madre.kernel.ChatCompletionInput;
import io.github.didacll.madre.kernel.ChatCompletionOutput;
import io.github.didacll.madre.kernel.ChatMessage;
import io.github.didacll.madre.kernel.DeliveryAcknowledgement;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.EngineSnapshot;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.kernel.InferenceRequirements;
import io.github.didacll.madre.kernel.InferenceResultReceiver;
import io.github.didacll.madre.kernel.InferenceTypes;
import io.github.didacll.madre.kernel.InferenceWork;
import io.github.didacll.madre.kernel.ResourceClaim;
import io.github.didacll.madre.kernel.ResourceId;
import io.github.didacll.madre.kernel.RetryPolicy;
import io.github.didacll.madre.kernel.TechnicalCapabilityRequirement;
import io.github.didacll.madre.kernel.Urgency;
import io.github.didacll.madre.kernel.WorkSnapshot;
import io.github.didacll.madre.sdk.execution.InferenceSelection;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic inference persistence and semantic-to-physical translation owned by MADRE runtime.
 *
 * <p>Security Algebra remains on this side of the boundary. The accumulated values are retained
 * with the semantic request history but are never copied into Kernel Work. Physical selection is
 * resolved from concrete computation requirements, explicit Owner selection or configured default,
 * and factual installed-engine characteristics. No provider/privacy policy is invented here.</p>
 */
public final class RuntimeInferenceService {
    private static final int FORMAT_VERSION = 3;

    private final InferenceKernel kernel;
    private final Path requests;
    private final Path results;
    private final Optional<InferenceSelection> defaultSelection;
    private final ConcurrentHashMap<io.github.didacll.madre.kernel.WorkId,
            CompletableFuture<TextGenerationResult>> waiting = new ConcurrentHashMap<>();

    public RuntimeInferenceService(InferenceKernel kernel, Path semanticStateDirectory) {
        this(kernel, semanticStateDirectory, Optional.empty());
    }

    public RuntimeInferenceService(
            InferenceKernel kernel,
            Path semanticStateDirectory,
            Optional<InferenceSelection> defaultSelection) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.defaultSelection = Objects.requireNonNull(defaultSelection, "defaultSelection");
        Path state = Objects.requireNonNull(semanticStateDirectory, "semanticStateDirectory")
                .toAbsolutePath().normalize();
        requests = state.resolve("requests");
        results = state.resolve("results");
        try {
            Files.createDirectories(requests);
            Files.createDirectories(results);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize semantic inference state", exception);
        }
    }

    public Optional<InferenceSelection> defaultSelection() {
        return defaultSelection;
    }

    public ReasoningService forAgent(AgentId actor) {
        return new BoundService(Objects.requireNonNull(actor, "actor"));
    }

    /** Reattaches every recoverable Kernel Work whose semantic request remains available. */
    public void recoverPending() {
        for (io.github.didacll.madre.kernel.WorkId id : kernel.recoverableWork()) {
            StoredChatRequest stored = readRequest(id);
            kernel.reattach(stored.work(), stored.input(), receiver(id));
        }
    }

    private final class BoundService implements ReasoningService {
        private final AgentId actor;

        private BoundService(AgentId actor) {
            this.actor = actor;
        }

        @Override
        public <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
                ReasoningRequest<R, C> request) {
            PreparedChat prepared = prepare(actor, request);
            CompletableFuture<TextGenerationResult> future = new CompletableFuture<>();
            waiting.put(prepared.work().id(), future);
            try {
                persist(prepared);
                kernel.submit(prepared.work(), prepared.input(), receiver(prepared.work().id()));
                monitorImmediate(prepared.work(), future);
            } catch (RuntimeException exception) {
                waiting.remove(prepared.work().id());
                future.completeExceptionally(exception);
            }
            return future.thenApply(request.resultType()::cast);
        }

        @Override
        public <R, C extends ReasoningComputation<R>> io.github.didacll.madre.sdk.execution.WorkId submit(
                ReasoningRequest<R, C> request) {
            PreparedChat prepared = prepare(actor, request);
            persist(prepared);
            kernel.submit(prepared.work(), prepared.input(), receiver(prepared.work().id()));
            return sdkId(prepared.work().id());
        }

        @Override
        public Optional<WorkStatus> inspect(io.github.didacll.madre.sdk.execution.WorkId id) {
            try {
                return Optional.of(toSemantic(kernel.snapshot(kernelId(id))));
            } catch (RuntimeException unavailable) {
                return Optional.empty();
            }
        }

        @Override
        public boolean cancel(io.github.didacll.madre.sdk.execution.WorkId id) {
            return kernel.cancel(kernelId(id));
        }

        @Override
        public <R> Optional<R> collect(
                io.github.didacll.madre.sdk.execution.WorkId id, Class<R> resultType) {
            Optional<TextGenerationResult> result = readResult(kernelId(id));
            return result.map(resultType::cast);
        }

        @Override
        public boolean acknowledge(io.github.didacll.madre.sdk.execution.WorkId id) {
            io.github.didacll.madre.kernel.WorkId kernelId = kernelId(id);
            try {
                boolean existed = Files.deleteIfExists(resultPath(kernelId));
                Files.deleteIfExists(requestPath(kernelId));
                return existed;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Cannot acknowledge semantic inference result", exception);
            }
        }
    }

    private void monitorImmediate(
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work,
            CompletableFuture<TextGenerationResult> future) {
        CompletableFuture.runAsync(() -> {
            try {
                WorkSnapshot terminal = kernel.await(work.id(), maximumWait(work.requirements()));
                if (!future.isDone()
                        && terminal.status().terminal()
                        && terminal.status()
                                != io.github.didacll.madre.kernel.WorkStatus.DELIVERED) {
                    String detail = terminal.failure()
                            .map(failure -> failure.category() + ": " + failure.message())
                            .orElse(terminal.status().name());
                    waiting.remove(work.id());
                    future.completeExceptionally(new IllegalStateException(
                            "Inference work " + work.id() + " failed: " + detail));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                waiting.remove(work.id());
                future.completeExceptionally(exception);
            } catch (java.util.concurrent.TimeoutException exception) {
                waiting.remove(work.id());
                future.completeExceptionally(exception);
            }
        });
    }

    private static Duration maximumWait(InferenceRequirements requirements) {
        int attempts = requirements.retryPolicy().maximumAttempts();
        Duration execution = requirements.timeout().multipliedBy(attempts);
        Duration retries = requirements.retryPolicy().delay()
                .multipliedBy(Math.max(0, attempts - 1));
        Duration eligibility = Duration.between(Instant.now(), requirements.eligibleAt());
        if (eligibility.isNegative()) {
            eligibility = Duration.ZERO;
        }
        return eligibility.plus(execution).plus(retries).plusSeconds(1);
    }

    private <R, C extends ReasoningComputation<R>> PreparedChat prepare(
            AgentId actor, ReasoningRequest<R, C> request) {
        Objects.requireNonNull(request, "request");
        if (!(request.computation() instanceof TextGenerationCommand command)) {
            throw new IllegalArgumentException(
                    "No runtime inference translation for "
                            + request.computation().getClass().getName());
        }

        TechnicalCapabilityRequirement.ChatCompletion capability =
                new TechnicalCapabilityRequirement.ChatCompletion(
                        command.minimumContextTokens());
        Optional<InferenceSelection> selection =
                request.preferences().exactSelection().or(() -> defaultSelection);
        requireResolvable(command, request, capability, selection);

        ChatCompletionInput input = new ChatCompletionInput(
                command.messages().stream()
                        .map(RuntimeInferenceService::physicalMessage)
                        .toList(),
                OptionalInt.of(command.maximumGeneratedTokens()),
                command.stopSequences());

        Urgency urgency = request.priority() >= 100
                ? Urgency.FAST_LANE
                : request.priority() == 0 ? Urgency.BACKGROUND : Urgency.NORMAL;
        InferenceRequirements requirements = new InferenceRequirements(
                capability,
                urgency,
                request.eligibleAt(),
                Optional.empty(),
                request.timeout(),
                new RetryPolicy(
                        request.retryPolicy().maximumAttempts(),
                        request.retryPolicy().delay()),
                request.preferences().maximumLatency(),
                selection.flatMap(value -> value.engineId().map(EngineId::new)),
                selection.flatMap(InferenceSelection::provider),
                selection.flatMap(InferenceSelection::model),
                selection.flatMap(RuntimeInferenceService::endpoint),
                List.of());
        InferenceWork<ChatCompletionInput, ChatCompletionOutput> work =
                InferenceWork.create(InferenceTypes.CHAT_COMPLETION, requirements);
        return new PreparedChat(
                actor,
                request.sensitivity(),
                request.privacy(),
                request.integrity(),
                work,
                input);
    }

    private void requireResolvable(
            TextGenerationCommand command,
            ReasoningRequest<?, ?> request,
            TechnicalCapabilityRequirement.ChatCompletion capability,
            Optional<InferenceSelection> selection) {
        if (command.messages().isEmpty()) {
            throw new IllegalArgumentException(
                    "Text generation contains no meaningful inference intent");
        }

        List<EngineSnapshot> technicallyEligible = kernel.engineSnapshots().stream()
                .filter(snapshot ->
                        snapshot.inferenceType().equals(InferenceTypes.CHAT_COMPLETION.id()))
                .filter(snapshot -> snapshot.characteristics().satisfies(capability))
                .filter(snapshot -> request.preferences().maximumLatency().isEmpty()
                        || snapshot.characteristics().expectedLatency().compareTo(
                                request.preferences().maximumLatency().orElseThrow()) <= 0)
                .filter(snapshot -> selection.isEmpty()
                        || matches(snapshot, selection.orElseThrow()))
                .toList();

        if (technicallyEligible.isEmpty()) {
            String chosen = selection.map(Object::toString)
                    .orElse("concrete chat-completion requirements");
            throw new IllegalStateException(
                    "No installed inference engine matches " + chosen);
        }
    }

    private static boolean matches(EngineSnapshot snapshot, InferenceSelection selection) {
        return selection.engineId().isEmpty()
                || snapshot.id().value().equals(selection.engineId().orElseThrow())
                ? matchesProviderModelEndpoint(snapshot, selection)
                : false;
    }

    private static boolean matchesProviderModelEndpoint(
            EngineSnapshot snapshot, InferenceSelection selection) {
        if (selection.provider().isPresent()
                && !snapshot.characteristics().provider().equals(
                        selection.provider().orElseThrow())) {
            return false;
        }
        if (selection.model().isPresent()
                && !snapshot.characteristics().model().equals(
                        selection.model().orElseThrow())) {
            return false;
        }
        return selection.endpoint().isEmpty()
                || snapshot.characteristics().endpoint().equals(
                        URI.create(selection.endpoint().orElseThrow()));
    }

    private static Optional<URI> endpoint(InferenceSelection selection) {
        return selection.endpoint().map(value -> {
            URI uri = URI.create(value);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException(
                        "exact inference endpoint must be an absolute URI");
            }
            return uri;
        });
    }

    private InferenceResultReceiver<ChatCompletionOutput> receiver(
            io.github.didacll.madre.kernel.WorkId expected) {
        return new InferenceResultReceiver<>() {
            @Override
            public DeliveryAcknowledgement accept(
                    io.github.didacll.madre.kernel.WorkId id,
                    ChatCompletionOutput output) {
                if (!expected.equals(id)) {
                    throw new IllegalArgumentException("Unexpected Work identity");
                }
                TextGenerationResult semantic = semanticResult(output);
                writeResult(id, semantic);
                CompletableFuture<TextGenerationResult> future = waiting.remove(id);
                if (future != null) {
                    future.complete(semantic);
                }
                return DeliveryAcknowledgement.DURABLY_ACCEPTED;
            }

            @Override
            public boolean alreadyAccepted(io.github.didacll.madre.kernel.WorkId id) {
                return expected.equals(id) && Files.isRegularFile(resultPath(id));
            }
        };
    }

    private void persist(PreparedChat prepared) {
        writeAtomically(requestPath(prepared.work().id()), output -> {
            output.writeInt(FORMAT_VERSION);
            output.writeUTF(prepared.actor().moduleId().value());
            output.writeUTF(prepared.actor().name());
            output.writeUTF(prepared.sensitivity().name());
            output.writeUTF(prepared.privacy().name());
            output.writeUTF(prepared.integrity().name());
            output.writeUTF(prepared.work().id().toString());
            output.writeLong(prepared.work().createdAt().toEpochMilli());
            writeRequirements(output, prepared.work().requirements());
            output.writeInt(prepared.input().messages().size());
            for (ChatMessage message : prepared.input().messages()) {
                output.writeUTF(message.role().name());
                writeLongText(output, message.text());
            }
            output.writeInt(prepared.input().maximumOutputTokens().orElse(0));
            output.writeInt(prepared.input().stopSequences().size());
            for (String stop : prepared.input().stopSequences()) {
                writeLongText(output, stop);
            }
        });
    }

    private StoredChatRequest readRequest(io.github.didacll.madre.kernel.WorkId id) {
        try (DataInputStream input =
                new DataInputStream(Files.newInputStream(requestPath(id)))) {
            if (input.readInt() != FORMAT_VERSION) {
                throw new IOException("Unsupported semantic request format");
            }
            AgentId actor = new AgentId(new ModuleId(input.readUTF()), input.readUTF());
            Sensitivity sensitivity = Sensitivity.valueOf(input.readUTF());
            Privacy privacy = Privacy.valueOf(input.readUTF());
            Integrity integrity = Integrity.valueOf(input.readUTF());
            io.github.didacll.madre.kernel.WorkId storedId =
                    io.github.didacll.madre.kernel.WorkId.parse(input.readUTF());
            Instant created = Instant.ofEpochMilli(input.readLong());
            InferenceRequirements requirements = readRequirements(input);

            int count = input.readInt();
            List<ChatMessage> messages = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                messages.add(new ChatMessage(
                        ChatMessage.Role.valueOf(input.readUTF()),
                        readLongText(input)));
            }
            int maximum = input.readInt();
            int stopCount = input.readInt();
            List<String> stops = new ArrayList<>(stopCount);
            for (int index = 0; index < stopCount; index++) {
                stops.add(readLongText(input));
            }
            ChatCompletionInput physical = new ChatCompletionInput(
                    messages,
                    maximum == 0 ? OptionalInt.empty() : OptionalInt.of(maximum),
                    stops);
            return new StoredChatRequest(
                    actor,
                    sensitivity,
                    privacy,
                    integrity,
                    new InferenceWork<>(
                            storedId,
                            InferenceTypes.CHAT_COMPLETION,
                            requirements,
                            created),
                    physical);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Cannot read semantic inference request " + id, exception);
        }
    }

    private void writeResult(
            io.github.didacll.madre.kernel.WorkId id, TextGenerationResult result) {
        writeAtomically(resultPath(id), output -> {
            output.writeInt(FORMAT_VERSION);
            writeLongText(output, result.text());
            output.writeUTF(result.completionReason().name());
            output.writeInt(result.inputTokens());
            output.writeInt(result.generatedTokens());
        });
    }

    private Optional<TextGenerationResult> readResult(
            io.github.didacll.madre.kernel.WorkId id) {
        Path path = resultPath(id);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (DataInputStream input =
                new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != FORMAT_VERSION) {
                throw new IOException("Unsupported semantic result format");
            }
            return Optional.of(new TextGenerationResult(
                    readLongText(input),
                    TextGenerationResult.CompletionReason.valueOf(input.readUTF()),
                    input.readInt(),
                    input.readInt()));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read semantic inference result " + id, exception);
        }
    }

    private static void writeRequirements(
            DataOutputStream output, InferenceRequirements value) throws IOException {
        if (!(value.capability()
                instanceof TechnicalCapabilityRequirement.ChatCompletion capability)) {
            throw new IOException(
                    "Runtime chat persistence received a non-chat capability");
        }
        output.writeInt(capability.minimumContextTokens());
        output.writeUTF(value.urgency().name());
        output.writeLong(value.eligibleAt().toEpochMilli());
        writeInstant(output, value.deadline());
        output.writeLong(value.timeout().toMillis());
        output.writeInt(value.retryPolicy().maximumAttempts());
        output.writeLong(value.retryPolicy().delay().toMillis());
        output.writeLong(value.maximumExpectedLatency()
                .map(Duration::toMillis)
                .orElse(-1L));
        writeOptionalText(output, value.exactEngine().map(EngineId::value));
        writeOptionalText(output, value.exactProvider());
        writeOptionalText(output, value.exactModel());
        writeOptionalText(output, value.exactEndpoint().map(URI::toString));
        output.writeInt(value.resources().size());
        for (ResourceClaim resource : value.resources()) {
            output.writeUTF(resource.resource().value());
            output.writeLong(resource.units());
        }
    }

    private static InferenceRequirements readRequirements(DataInputStream input)
            throws IOException {
        TechnicalCapabilityRequirement.ChatCompletion capability =
                new TechnicalCapabilityRequirement.ChatCompletion(input.readInt());
        Urgency urgency = Urgency.valueOf(input.readUTF());
        Instant eligibleAt = Instant.ofEpochMilli(input.readLong());
        Optional<Instant> deadline = readInstant(input);
        Duration timeout = Duration.ofMillis(input.readLong());
        RetryPolicy retry =
                new RetryPolicy(input.readInt(), Duration.ofMillis(input.readLong()));
        long latency = input.readLong();
        Optional<EngineId> engine = readOptionalText(input).map(EngineId::new);
        Optional<String> provider = readOptionalText(input);
        Optional<String> model = readOptionalText(input);
        Optional<URI> endpoint = readOptionalText(input).map(URI::create);
        int resourceCount = input.readInt();
        List<ResourceClaim> resources = new ArrayList<>(resourceCount);
        for (int index = 0; index < resourceCount; index++) {
            resources.add(new ResourceClaim(
                    new ResourceId(input.readUTF()), input.readLong()));
        }
        return new InferenceRequirements(
                capability,
                urgency,
                eligibleAt,
                deadline,
                timeout,
                retry,
                latency < 0
                        ? Optional.empty()
                        : Optional.of(Duration.ofMillis(latency)),
                engine,
                provider,
                model,
                endpoint,
                resources);
    }

    private static void writeInstant(
            DataOutputStream output, Optional<Instant> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.orElseThrow().toEpochMilli());
        }
    }

    private static Optional<Instant> readInstant(DataInputStream input)
            throws IOException {
        return input.readBoolean()
                ? Optional.of(Instant.ofEpochMilli(input.readLong()))
                : Optional.empty();
    }

    private static void writeOptionalText(
            DataOutputStream output, Optional<String> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeUTF(value.orElseThrow());
        }
    }

    private static Optional<String> readOptionalText(DataInputStream input)
            throws IOException {
        return input.readBoolean()
                ? Optional.of(input.readUTF())
                : Optional.empty();
    }

    private void writeAtomically(Path destination, IoWriter writer) {
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(
                    destination.getParent(),
                    destination.getFileName().toString(),
                    ".tmp");
            try {
                try (DataOutputStream output =
                        new DataOutputStream(Files.newOutputStream(temporary))) {
                    writer.write(output);
                }
                try {
                    Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot persist semantic inference state", exception);
        }
    }

    private static void writeLongText(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readLongText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 64 * 1024 * 1024) {
            throw new IOException("Invalid text length");
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static ChatMessage physicalMessage(TextGenerationMessage message) {
        return new ChatMessage(
                ChatMessage.Role.valueOf(message.role().name()),
                message.content());
    }

    private static TextGenerationResult semanticResult(ChatCompletionOutput result) {
        TextGenerationResult.CompletionReason reason =
                switch (result.finishReason()) {
                    case COMPLETE, ENGINE_STOP ->
                            TextGenerationResult.CompletionReason.STOP;
                    case LENGTH_LIMIT ->
                            TextGenerationResult.CompletionReason.LENGTH;
                    case UNKNOWN ->
                            TextGenerationResult.CompletionReason.OTHER;
                };
        return new TextGenerationResult(
                result.text(),
                reason,
                Math.toIntExact(result.usage().inputTokens()),
                Math.toIntExact(result.usage().outputTokens()));
    }

    private static WorkStatus toSemantic(WorkSnapshot value) {
        WorkState state =
                switch (value.status()) {
                    case QUEUED, RETRY_WAIT, NEEDS_INPUT -> WorkState.QUEUED;
                    case RUNNING, DELIVERING -> WorkState.RUNNING;
                    case DELIVERED -> WorkState.SUCCEEDED;
                    case FAILED, OUTCOME_UNKNOWN -> WorkState.FAILED;
                    case CANCELLED -> WorkState.CANCELLED;
                };
        Optional<ReasoningFailureCategory> failure =
                value.failure().map(item -> switch (item.category()) {
                    case NO_ENGINE, RESOURCE_UNAVAILABLE ->
                            ReasoningFailureCategory.UNAVAILABLE;
                    case TIMEOUT -> ReasoningFailureCategory.TIMEOUT;
                    case CANCELLED -> ReasoningFailureCategory.CANCELLED;
                    case ENGINE_FAILURE -> ReasoningFailureCategory.REMOTE_FAILURE;
                    case DELIVERY_FAILURE -> ReasoningFailureCategory.INTERNAL;
                });
        return new WorkStatus(
                sdkId(value.id()),
                state,
                value.attempts(),
                value.createdAt(),
                failure,
                value.status().terminal()
                        ? Optional.of(value.updatedAt())
                        : Optional.empty());
    }

    private Path requestPath(io.github.didacll.madre.kernel.WorkId id) {
        return requests.resolve(id.value() + ".bin");
    }

    private Path resultPath(io.github.didacll.madre.kernel.WorkId id) {
        return results.resolve(id.value() + ".bin");
    }

    private static io.github.didacll.madre.kernel.WorkId kernelId(
            io.github.didacll.madre.sdk.execution.WorkId id) {
        return io.github.didacll.madre.kernel.WorkId.parse(id.value());
    }

    private static io.github.didacll.madre.sdk.execution.WorkId sdkId(
            io.github.didacll.madre.kernel.WorkId id) {
        return new io.github.didacll.madre.sdk.execution.WorkId(id.toString());
    }

    private record PreparedChat(
            AgentId actor,
            Sensitivity sensitivity,
            Privacy privacy,
            Integrity integrity,
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work,
            ChatCompletionInput input) { }

    private record StoredChatRequest(
            AgentId actor,
            Sensitivity sensitivity,
            Privacy privacy,
            Integrity integrity,
            InferenceWork<ChatCompletionInput, ChatCompletionOutput> work,
            ChatCompletionInput input) { }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
