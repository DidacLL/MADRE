package io.github.didacll.madre.adapter.llamacpp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningException;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.text.TextInferenceCodecs;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Real reasoning mechanism backed by a configured llama-server chat endpoint. */
public final class LlamaCppReasoningCapability
        implements ReasoningCapability<TextInferenceResult, TextInferenceCommand> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LlamaCppConfiguration configuration;
    private final HttpClient client;
    private final ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand> manifest;

    public LlamaCppReasoningCapability(LlamaCppConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    public LlamaCppReasoningCapability(LlamaCppConfiguration configuration, HttpClient client) {
        this.configuration = Objects.requireNonNull(configuration);
        this.client = Objects.requireNonNull(client);
        ReasoningContract<TextInferenceResult, TextInferenceCommand> contract =
                new ReasoningContract<>(TextInferenceCodecs.CONTRACT_ID,
                        TextInferenceCommand.class, TextInferenceResult.class,
                        codec(TextInferenceCodecs::encodeCommand, TextInferenceCodecs::decodeCommand),
                        codec(TextInferenceCodecs::encodeResult, TextInferenceCodecs::decodeResult));
        manifest = new ReasoningCapabilityManifest<>(configuration.id(), contract,
                configuration.privacy(), ReasoningLocation.LOCAL,
                configuration.expectedLatency(), configuration.resources());
    }

    @Override public ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand>
            manifest() {
        return manifest;
    }

    @Override public ReasoningAvailability availability() {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300
                    ? ReasoningAvailability.AVAILABLE : ReasoningAvailability.UNAVAILABLE;
        } catch (IOException exception) {
            return ReasoningAvailability.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ReasoningAvailability.UNKNOWN;
        }
    }

    @Override public TextInferenceResult execute(TextInferenceCommand command,
            ReasoningExecutionContext context) throws ReasoningException {
        context.requireActive();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("model", configuration.modelAlias());
        ObjectNode message = payload.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", command.prompt());
        payload.put("max_tokens", command.maximumGeneratedTokens());
        var stops = payload.putArray("stop");
        command.stopSequences().forEach(stops::add);
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("v1/chat/completions"))
                    .timeout(remaining(context)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(
                            JSON.writeValueAsBytes(payload))).build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            context.requireActive();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ReasoningException(ReasoningFailureCategory.REMOTE_FAILURE,
                        "llama.cpp HTTP " + response.statusCode());
            }
            JsonNode root = JSON.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isTextual()) {
                throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                        "llama.cpp response has no textual choice");
            }
            String finish = choice.path("finish_reason").asText("");
            TextInferenceResult.CompletionReason reason = "stop".equals(finish)
                    ? TextInferenceResult.CompletionReason.STOP
                    : "length".equals(finish)
                            ? TextInferenceResult.CompletionReason.LENGTH
                            : TextInferenceResult.CompletionReason.OTHER;
            int prompt = root.path("usage").path("prompt_tokens").asInt(-1);
            int generated = root.path("usage").path("completion_tokens").asInt(-1);
            return new TextInferenceResult(content.textValue(), reason, prompt, generated);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "llama.cpp request timed out", exception);
        } catch (ConnectException exception) {
            throw new ReasoningException(ReasoningFailureCategory.CONNECTION,
                    "cannot connect to llama.cpp", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReasoningException(ReasoningFailureCategory.CANCELLED,
                    "llama.cpp request interrupted", exception);
        } catch (ReasoningException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                    "invalid llama.cpp exchange", exception);
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
