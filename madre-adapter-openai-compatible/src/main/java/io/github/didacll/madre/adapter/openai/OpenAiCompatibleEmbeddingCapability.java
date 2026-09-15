package io.github.didacll.madre.adapter.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.embedding.EmbeddingSpace;
import io.github.didacll.madre.embedding.TextEmbeddingCodecs;
import io.github.didacll.madre.embedding.TextEmbeddingCommand;
import io.github.didacll.madre.embedding.TextEmbeddingResult;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningException;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** OpenAI-compatible embeddings mechanism bound to one explicit vector space. */
public final class OpenAiCompatibleEmbeddingCapability
        implements ReasoningCapability<TextEmbeddingResult, TextEmbeddingCommand> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OpenAiCompatibleConfiguration configuration;
    private final EmbeddingSpace space;
    private final PreparedHttpTransport transport;
    private final ReasoningCapabilityManifest<TextEmbeddingResult, TextEmbeddingCommand> manifest;

    public OpenAiCompatibleEmbeddingCapability(OpenAiCompatibleConfiguration configuration,
            EmbeddingSpace space) {
        this(configuration, space, request -> HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()));
    }

    public OpenAiCompatibleEmbeddingCapability(OpenAiCompatibleConfiguration configuration,
            EmbeddingSpace space, PreparedHttpTransport transport) {
        this.configuration = Objects.requireNonNull(configuration);
        this.space = Objects.requireNonNull(space);
        this.transport = Objects.requireNonNull(transport);
        ReasoningContract<TextEmbeddingResult, TextEmbeddingCommand> contract =
                new ReasoningContract<>(TextEmbeddingCodecs.CONTRACT_ID,
                        TextEmbeddingCommand.class, TextEmbeddingResult.class,
                        codec(TextEmbeddingCodecs::encodeCommand, TextEmbeddingCodecs::decodeCommand),
                        codec(TextEmbeddingCodecs::encodeResult, TextEmbeddingCodecs::decodeResult));
        manifest = new ReasoningCapabilityManifest<>(configuration.id(), contract,
                configuration.privacy(), configuration.location(),
                configuration.expectedLatency(), configuration.resources());
    }

    public EmbeddingSpace space() { return space; }

    @Override public ReasoningCapabilityManifest<TextEmbeddingResult, TextEmbeddingCommand>
            manifest() {
        return manifest;
    }

    @Override public ReasoningAvailability availability() {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("models"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            int status = transport.send(request).statusCode();
            return status >= 200 && status < 300
                    ? ReasoningAvailability.AVAILABLE : ReasoningAvailability.UNAVAILABLE;
        } catch (IOException exception) {
            return ReasoningAvailability.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ReasoningAvailability.UNKNOWN;
        }
    }

    @Override public boolean supports(TextEmbeddingCommand computation) {
        return space.equals(computation.space());
    }

    @Override public TextEmbeddingResult execute(TextEmbeddingCommand command,
            ReasoningExecutionContext context) throws ReasoningException {
        if (!supports(command)) {
            throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                    "embedding request uses unsupported space " + command.space().id());
        }
        context.requireActive();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("model", configuration.model());
        payload.put("input", command.text());
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("embeddings"))
                    .timeout(remaining(context)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(
                            JSON.writeValueAsBytes(payload))).build();
            HttpResponse<String> response = transport.send(request);
            context.requireActive();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ReasoningException(ReasoningFailureCategory.REMOTE_FAILURE,
                        "OpenAI-compatible HTTP " + response.statusCode());
            }
            JsonNode embedding = JSON.readTree(response.body()).path("data").path(0)
                    .path("embedding");
            if (!embedding.isArray()) {
                throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                        "OpenAI-compatible response has no embedding vector");
            }
            List<Double> values = new ArrayList<>();
            for (JsonNode value : embedding) {
                if (!value.isNumber()) {
                    throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                            "OpenAI-compatible embedding contains a non-numeric coordinate");
                }
                values.add(value.doubleValue());
            }
            if (values.size() != space.dimensions()) {
                throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                        "OpenAI-compatible embedding dimension does not match configured space");
            }
            return new TextEmbeddingResult(space, values);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "OpenAI-compatible request timed out", exception);
        } catch (ConnectException exception) {
            throw new ReasoningException(ReasoningFailureCategory.CONNECTION,
                    "cannot connect to OpenAI-compatible endpoint", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReasoningException(ReasoningFailureCategory.CANCELLED,
                    "OpenAI-compatible request interrupted", exception);
        } catch (ReasoningException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                    "invalid OpenAI-compatible exchange", exception);
        }
    }

    private URI resolve(String path) {
        String base = configuration.endpoint().toString();
        if (!base.endsWith("/")) base += "/";
        return URI.create(base).resolve(path);
    }

    private static <T> ReasoningCodec<T> codec(java.util.function.Function<T, byte[]> encoder,
            java.util.function.Function<byte[], T> decoder) {
        return new ReasoningCodec<>() {
            @Override public byte[] encode(T value) { return encoder.apply(value); }
            @Override public T decode(byte[] bytes) { return decoder.apply(bytes); }
        };
    }

    private static Duration remaining(ReasoningExecutionContext context)
            throws ReasoningException {
        Duration value = Duration.between(java.time.Instant.now(), context.deadline());
        if (value.isNegative() || value.isZero()) {
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "reasoning execution timed out");
        }
        return value;
    }
}
