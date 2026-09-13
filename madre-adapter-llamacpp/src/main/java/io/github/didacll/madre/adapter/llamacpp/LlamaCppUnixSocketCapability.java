package io.github.didacll.madre.adapter.llamacpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalCodec;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.text.TextInferenceCodecs;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Local llama-server connector using a Unix-domain socket instead of a TCP listener. */
public final class LlamaCppUnixSocketCapability
        implements Capability<TextInferenceCommand, TextInferenceResult> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration MAXIMUM_PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final PhysicalContract<TextInferenceCommand, TextInferenceResult> CONTRACT =
            new PhysicalContract<>(TextInferenceCodecs.CONTRACT_ID,
                    TextInferenceCommand.class, TextInferenceResult.class,
                    codec(TextInferenceCodecs::encodeCommand, TextInferenceCodecs::decodeCommand),
                    codec(TextInferenceCodecs::encodeResult, TextInferenceCodecs::decodeResult));

    private final LlamaCppUnixSocketConfiguration configuration;
    private final CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest;

    public LlamaCppUnixSocketCapability(LlamaCppUnixSocketConfiguration configuration) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
        manifest = new CapabilityManifest<>(configuration.id(), CONTRACT,
                configuration.privacy(), Optional.of(configuration.integrity()),
                PhysicalLocation.LOCAL, configuration.expectedLatency(), configuration.resources());
    }

    @Override public CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest() {
        return manifest;
    }

    @Override public CapabilityAvailability availability() {
        Duration timeout = configuration.expectedLatency().compareTo(MAXIMUM_PROBE_TIMEOUT) < 0
                ? configuration.expectedLatency() : MAXIMUM_PROBE_TIMEOUT;
        try {
            UnixSocketHttpExchange.Response response = UnixSocketHttpExchange.exchange(
                    configuration.socketPath(), "GET", "/health", new byte[0], timeout);
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? CapabilityAvailability.AVAILABLE
                    : CapabilityAvailability.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CapabilityAvailability.UNKNOWN;
        } catch (IOException | UnsupportedOperationException exception) {
            return CapabilityAvailability.UNAVAILABLE;
        }
    }

    @Override public TextInferenceResult execute(TextInferenceCommand command,
            ExecutionContext context) throws CapabilityException {
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
                throw new CapabilityException(PhysicalFailureCategory.REMOTE_FAILURE,
                        "llama.cpp Unix socket returned HTTP " + response.statusCode());
            }
            return decodeResult(response.body());
        } catch (SocketTimeoutException exception) {
            throw new CapabilityException(PhysicalFailureCategory.TIMEOUT,
                    "llama.cpp Unix-socket request timed out", exception);
        } catch (ProtocolException exception) {
            throw new CapabilityException(PhysicalFailureCategory.PROTOCOL,
                    "invalid llama.cpp Unix-socket HTTP exchange", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CapabilityException(PhysicalFailureCategory.INTERRUPTED,
                    "llama.cpp Unix-socket request interrupted", exception);
        } catch (UnsupportedOperationException exception) {
            throw new CapabilityException(PhysicalFailureCategory.UNAVAILABLE,
                    "Unix-domain sockets are not supported by this runtime", exception);
        } catch (CapabilityException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CapabilityException(PhysicalFailureCategory.CONNECTION,
                    "cannot connect to llama.cpp Unix socket", exception);
        }
    }

    private byte[] requestBody(TextInferenceCommand command) throws CapabilityException {
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
            throw new CapabilityException(PhysicalFailureCategory.INTERNAL,
                    "cannot encode llama.cpp request", exception);
        }
    }

    private static TextInferenceResult decodeResult(byte[] body) throws CapabilityException {
        try {
            JsonNode root = JSON.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode choice = root.path("choices").path(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isTextual()) {
                throw new CapabilityException(PhysicalFailureCategory.PROTOCOL,
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
            throw new CapabilityException(PhysicalFailureCategory.PROTOCOL,
                    "invalid llama.cpp JSON response", exception);
        }
    }

    private static Duration remaining(ExecutionContext context) throws CapabilityException {
        Duration value = Duration.between(Instant.now(), context.deadline());
        if (value.isNegative() || value.isZero()) {
            throw new CapabilityException(PhysicalFailureCategory.TIMEOUT,
                    "execution timed out");
        }
        return value;
    }

    private static <T> PhysicalCodec<T> codec(java.util.function.Function<T, byte[]> encoder,
            java.util.function.Function<byte[], T> decoder) {
        return new PhysicalCodec<>() {
            @Override public byte[] encode(T value) { return encoder.apply(value); }
            @Override public T decode(byte[] bytes) { return decoder.apply(bytes); }
        };
    }
}
