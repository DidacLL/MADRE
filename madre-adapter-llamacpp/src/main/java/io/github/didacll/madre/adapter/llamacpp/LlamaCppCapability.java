package io.github.didacll.madre.adapter.llamacpp;

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
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceCodecs;
import io.github.didacll.madre.text.TextInferenceResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Real connector for a configured running llama-server native completion endpoint. */
public final class LlamaCppCapability implements Capability<TextInferenceCommand, TextInferenceResult> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LlamaCppConfiguration configuration;
    private final HttpClient client;
    private final CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest;

    public LlamaCppCapability(LlamaCppConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    public LlamaCppCapability(LlamaCppConfiguration configuration, HttpClient client) {
        this.configuration = Objects.requireNonNull(configuration); this.client = Objects.requireNonNull(client);
        PhysicalContract<TextInferenceCommand, TextInferenceResult> contract = new PhysicalContract<>(TextInferenceCodecs.CONTRACT_ID,
                TextInferenceCommand.class, TextInferenceResult.class, codec(TextInferenceCodecs::encodeCommand, TextInferenceCodecs::decodeCommand),
                codec(TextInferenceCodecs::encodeResult, TextInferenceCodecs::decodeResult));
        manifest = new CapabilityManifest<>(configuration.id(), contract, configuration.privacy(), java.util.Optional.of(configuration.integrity()),
                PhysicalLocation.LOCAL, configuration.expectedLatency(), configuration.resources());
    }

    @Override public CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest() { return manifest; }

    @Override public CapabilityAvailability availability() {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("health")).timeout(Duration.ofSeconds(2)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300 ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE;
        } catch (IOException exception) { return CapabilityAvailability.UNAVAILABLE; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return CapabilityAvailability.UNAVAILABLE; }
    }

    @Override public TextInferenceResult execute(TextInferenceCommand command, ExecutionContext context) throws CapabilityException {
        context.requireActive();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("prompt", command.prompt()); payload.put("n_predict", command.maximumGeneratedTokens());
        var stops = payload.putArray("stop"); command.stopSequences().forEach(stops::add); payload.put("model", configuration.modelAlias());
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("completion"))
                    .timeout(remaining(context)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(payload))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            context.requireActive();
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new CapabilityException(PhysicalFailureCategory.REMOTE_FAILURE, "llama.cpp HTTP " + response.statusCode());
            JsonNode root = JSON.readTree(response.body()); JsonNode content = root.get("content");
            if (content == null || !content.isTextual()) throw new CapabilityException(PhysicalFailureCategory.PROTOCOL, "llama.cpp response has no textual content");
            boolean stopped = root.path("stopped_eos").asBoolean(false) || root.path("stopped_word").asBoolean(false);
            int generated = root.path("tokens_predicted").asInt(-1); int prompt = root.path("tokens_evaluated").asInt(-1);
            TextInferenceResult.CompletionReason reason = stopped ? TextInferenceResult.CompletionReason.STOP
                    : generated >= command.maximumGeneratedTokens() ? TextInferenceResult.CompletionReason.LENGTH : TextInferenceResult.CompletionReason.OTHER;
            return new TextInferenceResult(content.textValue(), reason, prompt, generated);
        } catch (java.net.http.HttpTimeoutException exception) { throw new CapabilityException(PhysicalFailureCategory.TIMEOUT, "llama.cpp request timed out", exception); }
        catch (ConnectException exception) { throw new CapabilityException(PhysicalFailureCategory.CONNECTION, "cannot connect to llama.cpp", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CapabilityException(PhysicalFailureCategory.CANCELLED, "llama.cpp request interrupted", exception); }
        catch (CapabilityException exception) { throw exception; }
        catch (IOException exception) { throw new CapabilityException(PhysicalFailureCategory.PROTOCOL, "invalid llama.cpp exchange", exception); }
    }

    private URI resolve(String path) {
        String base = configuration.endpoint().toString(); if (!base.endsWith("/")) base += "/"; return URI.create(base).resolve(path);
    }
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
