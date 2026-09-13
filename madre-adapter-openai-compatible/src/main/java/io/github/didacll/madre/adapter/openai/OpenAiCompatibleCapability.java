package io.github.didacll.madre.adapter.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
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

/** Real OpenAI-compatible chat-completions connector with an externally prepared transport. */
public final class OpenAiCompatibleCapability implements Capability<TextInferenceCommand, TextInferenceResult> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OpenAiCompatibleConfiguration configuration;
    private final PreparedHttpTransport transport;
    private final CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest;

    public OpenAiCompatibleCapability(OpenAiCompatibleConfiguration configuration) {
        this(configuration, request -> HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()));
    }

    public OpenAiCompatibleCapability(OpenAiCompatibleConfiguration configuration, PreparedHttpTransport transport) {
        this.configuration = Objects.requireNonNull(configuration); this.transport = Objects.requireNonNull(transport);
        PhysicalContract<TextInferenceCommand, TextInferenceResult> contract = new PhysicalContract<>(TextInferenceCodecs.CONTRACT_ID,
                TextInferenceCommand.class, TextInferenceResult.class, codec(TextInferenceCodecs::encodeCommand, TextInferenceCodecs::decodeCommand),
                codec(TextInferenceCodecs::encodeResult, TextInferenceCodecs::decodeResult));
        manifest = new CapabilityManifest<>(configuration.id(), contract, configuration.privacy(), java.util.Optional.of(configuration.integrity()),
                configuration.location(), configuration.expectedLatency(), configuration.resources());
    }

    @Override public CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest() { return manifest; }

    @Override public CapabilityAvailability availability() {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("models")).timeout(Duration.ofSeconds(3)).GET().build();
            int status = transport.send(request).statusCode();
            return status >= 200 && status < 300 ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE;
        } catch (IOException exception) { return CapabilityAvailability.UNAVAILABLE; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return CapabilityAvailability.UNAVAILABLE; }
    }

    @Override public TextInferenceResult execute(TextInferenceCommand command, ExecutionContext context) throws CapabilityException {
        context.requireActive();
        ObjectNode payload = JSON.createObjectNode(); payload.put("model", configuration.model());
        ObjectNode message = payload.putArray("messages").addObject(); message.put("role", "user"); message.put("content", command.prompt());
        payload.put("max_tokens", command.maximumGeneratedTokens()); var stops = payload.putArray("stop"); command.stopSequences().forEach(stops::add);
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("chat/completions")).timeout(remaining(context))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(payload))).build();
            HttpResponse<String> response = transport.send(request); context.requireActive();
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new CapabilityException(PhysicalFailureCategory.REMOTE_FAILURE, "OpenAI-compatible HTTP " + response.statusCode());
            JsonNode root = JSON.readTree(response.body()); JsonNode choice = root.path("choices").path(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isTextual()) throw new CapabilityException(PhysicalFailureCategory.PROTOCOL, "OpenAI-compatible response has no textual choice");
            String finish = choice.path("finish_reason").asText("");
            TextInferenceResult.CompletionReason reason = "stop".equals(finish) ? TextInferenceResult.CompletionReason.STOP
                    : "length".equals(finish) ? TextInferenceResult.CompletionReason.LENGTH : TextInferenceResult.CompletionReason.OTHER;
            int prompt = root.path("usage").path("prompt_tokens").asInt(-1);
            int generated = root.path("usage").path("completion_tokens").asInt(-1);
            return new TextInferenceResult(content.textValue(), reason, prompt, generated);
        } catch (java.net.http.HttpTimeoutException exception) { throw new CapabilityException(PhysicalFailureCategory.TIMEOUT, "OpenAI-compatible request timed out", exception); }
        catch (ConnectException exception) { throw new CapabilityException(PhysicalFailureCategory.CONNECTION, "cannot connect to OpenAI-compatible endpoint", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CapabilityException(PhysicalFailureCategory.CANCELLED, "OpenAI-compatible request interrupted", exception); }
        catch (CapabilityException exception) { throw exception; }
        catch (IOException exception) { throw new CapabilityException(PhysicalFailureCategory.PROTOCOL, "invalid OpenAI-compatible exchange", exception); }
    }

    private URI resolve(String path) { String base = configuration.endpoint().toString(); if (!base.endsWith("/")) base += "/"; return URI.create(base).resolve(path); }
    private static <T> io.github.didacll.madre.kernel.capability.PhysicalCodec<T> codec(
            java.util.function.Function<T, byte[]> encoder, java.util.function.Function<byte[], T> decoder) {
        return new io.github.didacll.madre.kernel.capability.PhysicalCodec<>() {
            @Override public byte[] encode(T value) { return encoder.apply(value); }
            @Override public T decode(byte[] bytes) { return decoder.apply(bytes); }
        };
    }
    private static Duration remaining(ExecutionContext context) throws CapabilityException {
        Duration value = Duration.between(java.time.Instant.now(), context.deadline());
        if (value.isNegative() || value.isZero()) throw new CapabilityException(PhysicalFailureCategory.TIMEOUT, "execution timed out"); return value;
    }
}
