package io.github.didacll.madre.inference.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.didacll.madre.kernel.ChatCompletionInput;
import io.github.didacll.madre.kernel.ChatCompletionOutput;
import io.github.didacll.madre.kernel.EngineAvailability;
import io.github.didacll.madre.kernel.EngineCharacteristics;
import io.github.didacll.madre.kernel.EngineExecution;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.InferenceEngine;
import io.github.didacll.madre.kernel.InferenceType;
import io.github.didacll.madre.kernel.InferenceTypes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Chat-completion engine for an explicitly configured OpenAI-compatible endpoint. */
public final class OpenAiCompatibleEngine
        implements InferenceEngine<ChatCompletionInput, ChatCompletionOutput> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final EngineId id;
    private final URI endpoint;
    private final String apiKey;
    private final HttpClient client;
    private final EngineCharacteristics characteristics;

    public OpenAiCompatibleEngine(
            EngineId id,
            URI endpoint,
            String apiKey,
            String provider,
            String model,
            Duration expectedLatency,
            int maximumContextTokens) {
        this(id, endpoint, apiKey, provider, model, expectedLatency,
                maximumContextTokens, HttpClient.newHttpClient());
    }

    OpenAiCompatibleEngine(
            EngineId id,
            URI endpoint,
            String apiKey,
            String provider,
            String model,
            Duration expectedLatency,
            int maximumContextTokens,
            HttpClient client) {
        this.id = Objects.requireNonNull(id, "id");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.client = Objects.requireNonNull(client, "client");
        this.characteristics = new EngineCharacteristics(
                provider,
                model,
                endpoint,
                expectedLatency,
                Optional.of(new EngineCharacteristics.ChatCompletionCapability(
                        maximumContextTokens)));
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
        try {
            HttpRequest request = request(resolve("models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            int status = client.send(
                    request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300
                    ? EngineAvailability.AVAILABLE
                    : EngineAvailability.OFFLINE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return EngineAvailability.BUSY;
        } catch (Exception exception) {
            return EngineAvailability.OFFLINE;
        }
    }

    @Override
    public ChatCompletionOutput execute(
            ChatCompletionInput input, EngineExecution execution) throws Exception {
        Duration remaining = Duration.between(Instant.now(), execution.deadline());
        if (remaining.isNegative() || remaining.isZero()) {
            throw new java.net.http.HttpTimeoutException(
                    "Inference deadline elapsed");
        }
        HttpRequest request = request(resolve("chat/completions"))
                .timeout(remaining)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody(input)))
                .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new java.io.IOException(
                    "OpenAI-compatible HTTP " + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual()) {
            throw new java.io.IOException("Response has no textual choice");
        }
        ChatCompletionOutput.FinishReason finish =
                switch (choice.path("finish_reason").asText("")) {
                    case "stop" -> ChatCompletionOutput.FinishReason.ENGINE_STOP;
                    case "length" -> ChatCompletionOutput.FinishReason.LENGTH_LIMIT;
                    default -> ChatCompletionOutput.FinishReason.UNKNOWN;
                };
        return new ChatCompletionOutput(
                content.textValue(),
                finish,
                new ChatCompletionOutput.TokenUsage(
                        root.path("usage").path("prompt_tokens").asLong(0),
                        root.path("usage").path("completion_tokens").asLong(0)));
    }

    byte[] requestBody(ChatCompletionInput input) throws JsonProcessingException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", characteristics.model());
        var messages = body.putArray("messages");
        input.messages().forEach(item -> {
            ObjectNode message = messages.addObject();
            message.put("role", item.role().name().toLowerCase(Locale.ROOT));
            message.put("content", item.text());
        });
        input.maximumOutputTokens().ifPresent(
                value -> body.put("max_tokens", value));
        if (!input.stopSequences().isEmpty()) {
            var stops = body.putArray("stop");
            input.stopSequences().forEach(stops::add);
        }
        return JSON.writeValueAsBytes(body);
    }

    private HttpRequest.Builder request(URI target) {
        HttpRequest.Builder value = HttpRequest.newBuilder(target);
        return apiKey.isBlank()
                ? value
                : value.header("Authorization", "Bearer " + apiKey);
    }

    private URI resolve(String path) {
        String base = endpoint.toString();
        return URI.create(base.endsWith("/") ? base : base + "/").resolve(path);
    }
}
