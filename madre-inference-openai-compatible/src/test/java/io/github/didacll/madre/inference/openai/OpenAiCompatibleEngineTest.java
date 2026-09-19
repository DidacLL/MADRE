package io.github.didacll.madre.inference.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.didacll.madre.kernel.ChatCompletionInput;
import io.github.didacll.madre.kernel.ChatMessage;
import io.github.didacll.madre.kernel.EngineId;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void configuredProviderModelAndEndpointAreReported() {
        URI endpoint = URI.create("https://owner-configured.example/v1/");
        OpenAiCompatibleEngine engine = new OpenAiCompatibleEngine(
                new EngineId("compatible"),
                endpoint,
                "token",
                "owner-provider",
                "owner-model",
                Duration.ofMillis(20),
                16384);

        assertEquals(endpoint, engine.characteristics().endpoint());
        assertEquals("owner-provider", engine.characteristics().provider());
        assertEquals("owner-model", engine.characteristics().model());
    }

    @Test
    void chatRequestSerializesMessagesMaximumTokensAndStops() throws Exception {
        OpenAiCompatibleEngine engine = new OpenAiCompatibleEngine(
                new EngineId("compatible"),
                URI.create("https://example.invalid/v1/"),
                "token",
                "provider",
                "configured-model",
                Duration.ofMillis(20),
                16384);
        ChatCompletionInput input = new ChatCompletionInput(
                List.of(
                        new ChatMessage(ChatMessage.Role.SYSTEM, "system"),
                        new ChatMessage(ChatMessage.Role.USER, "hello")),
                OptionalInt.of(321),
                List.of("STOP-A", "STOP-B"));

        JsonNode body = JSON.readTree(engine.requestBody(input));

        assertEquals("configured-model", body.path("model").asText());
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("system", body.path("messages").path(0).path("content").asText());
        assertEquals("user", body.path("messages").path(1).path("role").asText());
        assertEquals("hello", body.path("messages").path(1).path("content").asText());
        assertEquals(321, body.path("max_tokens").asInt());
        assertEquals("STOP-A", body.path("stop").path(0).asText());
        assertEquals("STOP-B", body.path("stop").path(1).asText());
    }
}
