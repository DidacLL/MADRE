package io.github.didacll.madre.runtime;

import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationMessage;
import io.github.didacll.madre.generation.TextGenerationResult;
import io.github.didacll.madre.kernel.DeliveryAcknowledgement;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.kernel.InferenceRequirements;
import io.github.didacll.madre.kernel.InferenceResultReceiver;
import io.github.didacll.madre.kernel.InferenceTypes;
import io.github.didacll.madre.kernel.InferenceWork;
import io.github.didacll.madre.kernel.Placement;
import io.github.didacll.madre.kernel.RetryPolicy;
import io.github.didacll.madre.kernel.TechnicalCapabilityRequirement;
import io.github.didacll.madre.kernel.TextInferenceInput;
import io.github.didacll.madre.kernel.TextInferenceOutput;
import io.github.didacll.madre.kernel.TextMessage;
import io.github.didacll.madre.kernel.Urgency;
import io.github.didacll.madre.kernel.WorkSnapshot;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.identity.AgentId;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Semantic inference persistence and translation owned by MADRE runtime. */
public final class RuntimeInferenceService {
    private static final int FORMAT_VERSION = 1;
    private final InferenceKernel kernel;
    private final Path requests;
    private final Path results;
    private final ConcurrentHashMap<io.github.didacll.madre.kernel.WorkId,
            CompletableFuture<TextGenerationResult>> waiting = new ConcurrentHashMap<>();

    public RuntimeInferenceService(InferenceKernel kernel, Path semanticStateDirectory) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
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

    public ReasoningService forAgent(AgentId actor) {
        return new BoundService(Objects.requireNonNull(actor, "actor"));
    }

    /** Reattaches every recoverable Kernel Work whose semantic request remains available. */
    public void recoverPending() {
        for (io.github.didacll.madre.kernel.WorkId id : kernel.recoverableWork()) {
            StoredTextRequest stored = readRequest(id);
            kernel.reattach(stored.work(), stored.input(), receiver(id));
        }
    }

    private final class BoundService implements ReasoningService {
        private final AgentId actor;
        private BoundService(AgentId actor) { this.actor = actor; }

        @Override
        public <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
                ReasoningRequest<R, C> request) {
            PreparedText prepared = prepare(actor, request);
            CompletableFuture<TextGenerationResult> future = new CompletableFuture<>();
            waiting.put(prepared.work().id(), future);
            try {
                persist(prepared);
                kernel.submit(prepared.work(), prepared.input(), receiver(prepared.work().id()));
            } catch (RuntimeException exception) {
                waiting.remove(prepared.work().id());
                future.completeExceptionally(exception);
            }
            return future.thenApply(request.resultType()::cast);
        }

        @Override
        public <R, C extends ReasoningComputation<R>> io.github.didacll.madre.sdk.execution.WorkId submit(
                ReasoningRequest<R, C> request) {
            PreparedText prepared = prepare(actor, request);
            persist(prepared);
            kernel.submit(prepared.work(), prepared.input(), receiver(prepared.work().id()));
            return sdkId(prepared.work().id());
        }

        @Override public Optional<WorkStatus> inspect(io.github.didacll.madre.sdk.execution.WorkId id) {
            try { return Optional.of(toSemantic(kernel.snapshot(kernelId(id)))); }
            catch (RuntimeException unavailable) { return Optional.empty(); }
        }
        @Override public boolean cancel(io.github.didacll.madre.sdk.execution.WorkId id) {
            return kernel.cancel(kernelId(id));
        }
        @Override public <R> Optional<R> collect(io.github.didacll.madre.sdk.execution.WorkId id,
                Class<R> resultType) {
            Optional<TextGenerationResult> result = readResult(kernelId(id));
            return result.map(resultType::cast);
        }
        @Override public boolean acknowledge(io.github.didacll.madre.sdk.execution.WorkId id) {
            io.github.didacll.madre.kernel.WorkId kernelId = kernelId(id);
            try {
                boolean existed = Files.deleteIfExists(resultPath(kernelId));
                Files.deleteIfExists(requestPath(kernelId));
                return existed;
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot acknowledge semantic inference result", exception);
            }
        }
    }

    private <R, C extends ReasoningComputation<R>> PreparedText prepare(AgentId actor,
            ReasoningRequest<R, C> request) {
        Objects.requireNonNull(request, "request");
        if (!(request.computation() instanceof TextGenerationCommand command)) {
            throw new IllegalArgumentException("No runtime inference translation for "
                    + request.computation().getClass().getName());
        }
        TextInferenceInput input = new TextInferenceInput(command.messages().stream()
                .map(RuntimeInferenceService::physicalMessage).toList(), Optional.empty(),
                OptionalInt.of(command.maximumGeneratedTokens()));
        Placement placement = request.preferences().location()
                .filter(location -> location == ReasoningLocation.LOCAL)
                .map(ignored -> Placement.LOCAL_ONLY).orElse(Placement.LOCAL_OR_REMOTE);
        Urgency urgency = request.priority() >= 100 ? Urgency.FAST_LANE
                : request.priority() == 0 ? Urgency.BACKGROUND : Urgency.NORMAL;
        InferenceRequirements requirements = new InferenceRequirements(placement,
                new TechnicalCapabilityRequirement.Text(false, 0), urgency,
                request.eligibleAt(), Optional.empty(), request.timeout(),
                new RetryPolicy(request.retryPolicy().maximumAttempts(),
                        request.retryPolicy().delay()),
                request.preferences().maximumLatency(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of());
        InferenceWork<TextInferenceInput, TextInferenceOutput> work =
                InferenceWork.create(InferenceTypes.TEXT_GENERATION, requirements);
        return new PreparedText(actor, work, input, command.stopSequences());
    }

    private InferenceResultReceiver<TextInferenceOutput> receiver(
            io.github.didacll.madre.kernel.WorkId expected) {
        return new InferenceResultReceiver<>() {
            @Override public DeliveryAcknowledgement accept(
                    io.github.didacll.madre.kernel.WorkId id, TextInferenceOutput output) {
                if (!expected.equals(id)) throw new IllegalArgumentException("Unexpected Work identity");
                TextGenerationResult semantic = semanticResult(output);
                writeResult(id, semantic);
                CompletableFuture<TextGenerationResult> future = waiting.remove(id);
                if (future != null) future.complete(semantic);
                return DeliveryAcknowledgement.DURABLY_ACCEPTED;
            }
            @Override public boolean alreadyAccepted(io.github.didacll.madre.kernel.WorkId id) {
                return expected.equals(id) && Files.isRegularFile(resultPath(id));
            }
        };
    }

    private void persist(PreparedText prepared) {
        writeAtomically(requestPath(prepared.work().id()), output -> {
            output.writeInt(FORMAT_VERSION);
            output.writeUTF(prepared.actor().moduleId().value());
            output.writeUTF(prepared.actor().name());
            output.writeUTF(prepared.work().id().toString());
            output.writeLong(prepared.work().createdAt().toEpochMilli());
            writeRequirements(output, prepared.work().requirements());
            output.writeInt(prepared.input().messages().size());
            for (TextMessage message : prepared.input().messages()) {
                output.writeUTF(message.role().name());
                writeLongText(output, message.text());
            }
            output.writeInt(prepared.input().maximumOutputTokens().orElse(0));
            output.writeInt(prepared.stopSequences().size());
            for (String stop : prepared.stopSequences()) writeLongText(output, stop);
        });
    }

    private StoredTextRequest readRequest(io.github.didacll.madre.kernel.WorkId id) {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(requestPath(id)))) {
            if (input.readInt() != FORMAT_VERSION) throw new IOException("Unsupported semantic request format");
            AgentId actor = new AgentId(new io.github.didacll.madre.sdk.identity.ModuleId(input.readUTF()),
                    input.readUTF());
            io.github.didacll.madre.kernel.WorkId storedId =
                    io.github.didacll.madre.kernel.WorkId.parse(input.readUTF());
            Instant created = Instant.ofEpochMilli(input.readLong());
            InferenceRequirements requirements = readRequirements(input);
            int count = input.readInt();
            List<TextMessage> messages = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                messages.add(new TextMessage(TextMessage.Role.valueOf(input.readUTF()), readLongText(input)));
            }
            int maximum = input.readInt();
            int stops = input.readInt();
            for (int index = 0; index < stops; index++) readLongText(input);
            TextInferenceInput physical = new TextInferenceInput(messages, Optional.empty(),
                    maximum == 0 ? OptionalInt.empty() : OptionalInt.of(maximum));
            return new StoredTextRequest(actor, new InferenceWork<>(storedId,
                    InferenceTypes.TEXT_GENERATION, requirements, created), physical);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read semantic inference request " + id, exception);
        }
    }

    private void writeResult(io.github.didacll.madre.kernel.WorkId id,
            TextGenerationResult result) {
        writeAtomically(resultPath(id), output -> {
            output.writeInt(FORMAT_VERSION);
            writeLongText(output, result.text());
            output.writeUTF(result.completionReason().name());
            output.writeInt(result.inputTokens());
            output.writeInt(result.generatedTokens());
        });
    }

    private Optional<TextGenerationResult> readResult(io.github.didacll.madre.kernel.WorkId id) {
        Path path = resultPath(id);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != FORMAT_VERSION) throw new IOException("Unsupported semantic result format");
            return Optional.of(new TextGenerationResult(readLongText(input),
                    TextGenerationResult.CompletionReason.valueOf(input.readUTF()), input.readInt(),
                    input.readInt()));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read semantic inference result " + id, exception);
        }
    }

    private static void writeRequirements(DataOutputStream output, InferenceRequirements value)
            throws IOException {
        output.writeUTF(value.placement().name());
        output.writeUTF(value.urgency().name());
        output.writeLong(value.eligibleAt().toEpochMilli());
        output.writeLong(value.timeout().toMillis());
        output.writeInt(value.retryPolicy().maximumAttempts());
        output.writeLong(value.retryPolicy().delay().toMillis());
        output.writeLong(value.maximumExpectedLatency().map(java.time.Duration::toMillis).orElse(-1L));
    }

    private static InferenceRequirements readRequirements(DataInputStream input) throws IOException {
        Placement placement = Placement.valueOf(input.readUTF());
        Urgency urgency = Urgency.valueOf(input.readUTF());
        Instant eligibleAt = Instant.ofEpochMilli(input.readLong());
        java.time.Duration timeout = java.time.Duration.ofMillis(input.readLong());
        RetryPolicy retry = new RetryPolicy(input.readInt(), java.time.Duration.ofMillis(input.readLong()));
        long latency = input.readLong();
        return new InferenceRequirements(placement, new TechnicalCapabilityRequirement.Text(false, 0),
                urgency, eligibleAt, Optional.empty(), timeout, retry,
                latency < 0 ? Optional.empty() : Optional.of(java.time.Duration.ofMillis(latency)),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private void writeAtomically(Path destination, IoWriter writer) {
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
            try {
                try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                    writer.write(output);
                }
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist semantic inference state", exception);
        }
    }

    private static void writeLongText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
    private static String readLongText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 64 * 1024 * 1024) throw new IOException("Invalid text length");
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static TextMessage physicalMessage(TextGenerationMessage message) {
        return new TextMessage(TextMessage.Role.valueOf(message.role().name()), message.content());
    }
    private static TextGenerationResult semanticResult(TextInferenceOutput result) {
        TextGenerationResult.CompletionReason reason = switch (result.finishReason()) {
            case COMPLETE, ENGINE_STOP -> TextGenerationResult.CompletionReason.STOP;
            case LENGTH_LIMIT -> TextGenerationResult.CompletionReason.LENGTH;
            case UNKNOWN -> TextGenerationResult.CompletionReason.OTHER;
        };
        return new TextGenerationResult(result.text(), reason,
                Math.toIntExact(result.usage().inputTokens()), Math.toIntExact(result.usage().outputTokens()));
    }
    private static WorkStatus toSemantic(WorkSnapshot value) {
        WorkState state = switch (value.status()) {
            case QUEUED, RETRY_WAIT, NEEDS_INPUT -> WorkState.QUEUED;
            case RUNNING, DELIVERING -> WorkState.RUNNING;
            case DELIVERED -> WorkState.SUCCEEDED;
            case FAILED, OUTCOME_UNKNOWN -> WorkState.FAILED;
            case CANCELLED -> WorkState.CANCELLED;
        };
        Optional<ReasoningFailureCategory> failure = value.failure().map(item -> switch (item.category()) {
            case NO_ENGINE -> ReasoningFailureCategory.UNAVAILABLE;
            case TIMEOUT -> ReasoningFailureCategory.TIMEOUT;
            case CANCELLED -> ReasoningFailureCategory.CANCELLED;
            case ENGINE_FAILURE -> ReasoningFailureCategory.REMOTE_FAILURE;
            case RESOURCE_UNAVAILABLE -> ReasoningFailureCategory.UNAVAILABLE;
            case DELIVERY_FAILURE -> ReasoningFailureCategory.INTERNAL;
        });
        return new WorkStatus(sdkId(value.id()), state, value.attempts(), value.createdAt(), failure,
                value.status().terminal() ? Optional.of(value.updatedAt()) : Optional.empty());
    }
    private Path requestPath(io.github.didacll.madre.kernel.WorkId id) { return requests.resolve(id.value() + ".bin"); }
    private Path resultPath(io.github.didacll.madre.kernel.WorkId id) { return results.resolve(id.value() + ".bin"); }
    private static io.github.didacll.madre.kernel.WorkId kernelId(io.github.didacll.madre.sdk.execution.WorkId id) {
        return io.github.didacll.madre.kernel.WorkId.parse(id.value());
    }
    private static io.github.didacll.madre.sdk.execution.WorkId sdkId(io.github.didacll.madre.kernel.WorkId id) {
        return new io.github.didacll.madre.sdk.execution.WorkId(id.toString());
    }
    private record PreparedText(AgentId actor,
            InferenceWork<TextInferenceInput, TextInferenceOutput> work, TextInferenceInput input,
            List<String> stopSequences) { }
    private record StoredTextRequest(AgentId actor,
            InferenceWork<TextInferenceInput, TextInferenceOutput> work, TextInferenceInput input) { }
    @FunctionalInterface private interface IoWriter { void write(DataOutputStream output) throws IOException; }
}
