package io.github.didacll.madre.inference.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.kernel.EngineAvailability;
import io.github.didacll.madre.kernel.EngineCharacteristics;
import io.github.didacll.madre.kernel.EngineExecution;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.EngineLocation;
import io.github.didacll.madre.kernel.InferenceEngine;
import io.github.didacll.madre.kernel.InferenceType;
import io.github.didacll.madre.kernel.InferenceTypes;
import io.github.didacll.madre.kernel.TextInferenceInput;
import io.github.didacll.madre.kernel.TextInferenceOutput;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Concrete text engine for an explicitly configured OpenAI-compatible endpoint. */
public final class OpenAiCompatibleEngine
        implements InferenceEngine<TextInferenceInput, TextInferenceOutput> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final EngineId id;
    private final URI endpoint;
    private final String apiKey;
    private final HttpClient client;
    private final EngineCharacteristics characteristics;

    public OpenAiCompatibleEngine(EngineId id, URI endpoint, String apiKey, String provider,
            String model, EngineLocation location, Duration expectedLatency,
            boolean demandingReasoning, int maximumContextTokens, int preference) {
        this(id, endpoint, apiKey, provider, model, location, expectedLatency,
                demandingReasoning, maximumContextTokens, preference, HttpClient.newHttpClient());
    }

    OpenAiCompatibleEngine(EngineId id, URI endpoint, String apiKey, String provider,
            String model, EngineLocation location, Duration expectedLatency,
            boolean demandingReasoning, int maximumContextTokens, int preference,
            HttpClient client) {
        this.id = Objects.requireNonNull(id, "id");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.client = Objects.requireNonNull(client, "client");
        this.characteristics = new EngineCharacteristics(location, provider, model,
                expectedLatency, preference,
                Optional.of(new EngineCharacteristics.TextCapability(
                        demandingReasoning, maximumContextTokens)), Optional.empty(), Optional.empty());
    }

    @Override public EngineId id() { return id; }
    @Override public InferenceType<TextInferenceInput, TextInferenceOutput> type() {
        return InferenceTypes.TEXT_GENERATION;
    }
    @Override public EngineCharacteristics characteristics() { return characteristics; }

    @Override public EngineAvailability availability() {
        try {
            HttpRequest request = request(resolve("models")).timeout(Duration.ofSeconds(3)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300 ? EngineAvailability.AVAILABLE
                    : EngineAvailability.OFFLINE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return EngineAvailability.BUSY;
        } catch (Exception exception) {
            return EngineAvailability.OFFLINE;
        }
    }

    @Override public TextInferenceOutput execute(TextInferenceInput input,
            EngineExecution execution) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", characteristics.model());
        var messages = body.putArray("messages");
        input.messages().forEach(item -> {
            ObjectNode message = messages.addObject();
            message.put("role", item.role().name().toLowerCase(Locale.ROOT));
            message.put("content", item.text());
        });
        input.temperature().ifPresent(value -> body.put("temperature", value));
        input.maximumOutputTokens().ifPresent(value -> body.put("max_tokens", value));
        Duration remaining = Duration.between(Instant.now(), execution.deadline());
        if (remaining.isNegative() || remaining.isZero()) {
            throw new java.net.http.HttpTimeoutException("Inference deadline elapsed");
        }
        HttpRequest request = request(resolve("chat/completions")).timeout(remaining)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new java.io.IOException("OpenAI-compatible HTTP " + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual()) throw new java.io.IOException("Response has no textual choice");
        TextInferenceOutput.FinishReason finish = switch (choice.path("finish_reason").asText("")) {
            case "stop" -> TextInferenceOutput.FinishReason.ENGINE_STOP;
            case "length" -> TextInferenceOutput.FinishReason.LENGTH_LIMIT;
            default -> TextInferenceOutput.FinishReason.UNKNOWN;
        };
        return new TextInferenceOutput(content.textValue(), finish,
                new TextInferenceOutput.TokenUsage(root.path("usage").path("prompt_tokens").asLong(0),
                        root.path("usage").path("completion_tokens").asLong(0)));
    }

    private HttpRequest.Builder request(URI target) {
        HttpRequest.Builder value = HttpRequest.newBuilder(target);
        return apiKey.isBlank() ? value : value.header("Authorization", "Bearer " + apiKey);
    }

    private URI resolve(String path) {
        String base = endpoint.toString();
        return URI.create(base.endsWith("/") ? base : base + "/").resolve(path);
    }
}
