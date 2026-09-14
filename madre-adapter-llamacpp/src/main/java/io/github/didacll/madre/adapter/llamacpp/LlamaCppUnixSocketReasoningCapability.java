package io.github.didacll.madre.adapter.llamacpp;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Local llama-server reasoning mechanism over a Unix-domain socket. */
public final class LlamaCppUnixSocketReasoningCapability
        implements ReasoningCapability<TextInferenceResult, TextInferenceCommand> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration MAXIMUM_PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final ReasoningContract<TextInferenceResult, TextInferenceCommand> CONTRACT =
            new ReasoningContract<>(TextInferenceCodecs.CONTRACT_ID,
                    TextInferenceCommand.class, TextInferenceResult.class,
                    codec(TextInferenceCodecs::encodeCommand, TextInferenceCodecs::decodeCommand),
                    codec(TextInferenceCodecs::encodeResult, TextInferenceCodecs::decodeResult));

    private final LlamaCppUnixSocketConfiguration configuration;
    private final ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand> manifest;

    public LlamaCppUnixSocketReasoningCapability(
            LlamaCppUnixSocketConfiguration configuration) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
        manifest = new ReasoningCapabilityManifest<>(configuration.id(), CONTRACT,
                configuration.privacy(), ReasoningLocation.LOCAL,
                configuration.expectedLatency(), configuration.resources());
    }

    @Override public ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand>
            manifest() {
        return manifest;
    }

    @Override public ReasoningAvailability availability() {
        Duration timeout = configuration.expectedLatency().compareTo(MAXIMUM_PROBE_TIMEOUT) < 0
                ? configuration.expectedLatency() : MAXIMUM_PROBE_TIMEOUT;
        try {
            UnixSocketHttpExchange.Response response = UnixSocketHttpExchange.exchange(
                    configuration.socketPath(), "GET", "/health", new byte[0], timeout);
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? ReasoningAvailability.AVAILABLE : ReasoningAvailability.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ReasoningAvailability.UNKNOWN;
        } catch (IOException | UnsupportedOperationException exception) {
            return ReasoningAvailability.UNAVAILABLE;
        }
    }

    @Override public TextInferenceResult execute(TextInferenceCommand command,
            ReasoningExecutionContext context) throws ReasoningException {
        java.util.Objects.requireNonNull(command, "command");
        java.util.Objects.requireNonNull(context, "context");
        context.requireActive();
        try {
            byte[] requestBody = requestBody(command);
            UnixSocketHttpExchange.Response response = UnixSocketHttpExchange.exchange(
                    configuration.socketPath(), "POST", "/v1/chat/completions", requestBody,
                    remaining(context));
            context.requireActive();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ReasoningException(ReasoningFailureCategory.REMOTE_FAILURE,
                        "llama.cpp Unix socket returned HTTP " + response.statusCode());
            }
            return decodeResult(response.body());
        } catch (SocketTimeoutException exception) {
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "llama.cpp Unix-socket request timed out", exception);
        } catch (ProtocolException exception) {
            throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                    "invalid llama.cpp Unix-socket HTTP exchange", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReasoningException(ReasoningFailureCategory.INTERRUPTED,
                    "llama.cpp Unix-socket request interrupted", exception);
        } catch (UnsupportedOperationException exception) {
            throw new ReasoningException(ReasoningFailureCategory.UNAVAILABLE,
                    "Unix-domain sockets are not supported by this runtime", exception);
        } catch (ReasoningException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ReasoningException(ReasoningFailureCategory.CONNECTION,
                    "cannot connect to llama.cpp Unix socket", exception);
        }
    }

    private byte[] requestBody(TextInferenceCommand command) throws ReasoningException {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("model", configuration.modelAlias());
        ObjectNode message = payload.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", command.prompt());
        payload.put("max_tokens", command.maximumGeneratedTokens());
        var stops = payload.putArray("stop");
        command.stopSequences().forEach(stops::add);
        try {
            return JSON.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new ReasoningException(ReasoningFailureCategory.INTERNAL,
                    "cannot encode llama.cpp request", exception);
        }
    }

    private static TextInferenceResult decodeResult(byte[] body) throws ReasoningException {
        try {
            JsonNode root = JSON.readTree(new String(body, StandardCharsets.UTF_8));
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
        } catch (JsonProcessingException exception) {
            throw new ReasoningException(ReasoningFailureCategory.PROTOCOL,
                    "invalid llama.cpp JSON response", exception);
        }
    }

    private static Duration remaining(ReasoningExecutionContext context)
            throws ReasoningException {
        Duration value = Duration.between(Instant.now(), context.deadline());
        if (value.isNegative() || value.isZero()) {
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "reasoning execution timed out");
        }
        return value;
    }

    private static <T> ReasoningCodec<T> codec(java.util.function.Function<T, byte[]> encoder,
            java.util.function.Function<byte[], T> decoder) {
        return new ReasoningCodec<>() {
            @Override public byte[] encode(T value) { return encoder.apply(value); }
            @Override public T decode(byte[] bytes) { return decoder.apply(bytes); }
        };
    }
}
