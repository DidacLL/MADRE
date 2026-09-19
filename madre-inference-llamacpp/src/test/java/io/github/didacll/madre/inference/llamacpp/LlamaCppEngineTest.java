package io.github.didacll.madre.inference.llamacpp;

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

final class LlamaCppEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void configuredEndpointIsReportedWithoutLocalityAssumption()
            throws Exception {
        URI endpoint = URI.create("https://llama.example.net/proxy/");
        LlamaCppEngine engine = new LlamaCppEngine(
                new EngineId("llama"),
                endpoint,
                "model",
                Duration.ofMillis(20),
                8192);

        assertEquals(endpoint, engine.characteristics().endpoint());
        assertEquals("llama.cpp", engine.characteristics().provider());
        assertEquals("model", engine.characteristics().model());
    }

    @Test
    void chatRequestSerializesMessagesMaximumTokensAndStops() throws Exception {
        LlamaCppEngine engine = new LlamaCppEngine(
                new EngineId("llama"),
                URI.create("http://example.invalid/"),
                "configured-model",
                Duration.ofMillis(20),
                8192);
        ChatCompletionInput input = new ChatCompletionInput(
                List.of(
                        new ChatMessage(ChatMessage.Role.SYSTEM, "system"),
                        new ChatMessage(ChatMessage.Role.USER, "hello")),
                OptionalInt.of(123),
                List.of("STOP-A", "STOP-B"));

        JsonNode body = JSON.readTree(engine.requestBody(input));

        assertEquals("configured-model", body.path("model").asText());
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("system", body.path("messages").path(0).path("content").asText());
        assertEquals("user", body.path("messages").path(1).path("role").asText());
        assertEquals("hello", body.path("messages").path(1).path("content").asText());
        assertEquals(123, body.path("max_tokens").asInt());
        assertEquals("STOP-A", body.path("stop").path(0).asText());
        assertEquals("STOP-B", body.path("stop").path(1).asText());
    }
}
