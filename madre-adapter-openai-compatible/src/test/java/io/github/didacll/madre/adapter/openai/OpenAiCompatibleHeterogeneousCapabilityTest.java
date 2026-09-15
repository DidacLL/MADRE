package io.github.didacll.madre.adapter.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.embedding.EmbeddingSpace;
import io.github.didacll.madre.embedding.TextEmbeddingCommand;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationMessage;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleHeterogeneousCapabilityTest {
    @Test void executesRichGenerationAndSpaceQualifiedEmbedding() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String body = request.contains("system") && request.contains("user")
                    ? "{\"choices\":[{\"message\":{\"content\":\"rich text\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2}}"
                    : "{}";
            respond(exchange, body);
        });
        server.createContext("/v1/embeddings", exchange ->
                respond(exchange, "{\"data\":[{\"embedding\":[0.1,-0.2,0.3]}]}"));
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            PreparedHttpTransport transport = request -> client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            OpenAiCompatibleConfiguration configuration = new OpenAiCompatibleConfiguration(
                    new ReasoningCapabilityId("heterogeneous"),
                    java.net.URI.create("http://127.0.0.1:"
                            + server.getAddress().getPort() + "/v1/"),
                    "installed-model", Privacy.UNKNOWN, ReasoningLocation.REMOTE,
                    java.time.Duration.ofMillis(20), List.of());
            ReasoningExecutionContext context = new ReasoningExecutionContext(
                    Instant.now().plusSeconds(2), () -> false, 1);

            OpenAiCompatibleTextGenerationCapability generation =
                    new OpenAiCompatibleTextGenerationCapability(configuration, transport);
            var generated = generation.execute(new TextGenerationCommand(List.of(
                    new TextGenerationMessage(TextGenerationMessage.Role.SYSTEM, "Be exact."),
                    new TextGenerationMessage(TextGenerationMessage.Role.USER, "Answer.")),
                    16, List.of()), context);
            assertEquals("rich text", generated.text());
            assertEquals(4, generated.inputTokens());

            EmbeddingSpace space = new EmbeddingSpace("installed-model/space-v1", 3);
            OpenAiCompatibleEmbeddingCapability embeddings =
                    new OpenAiCompatibleEmbeddingCapability(configuration, space, transport);
            assertTrue(embeddings.supports(new TextEmbeddingCommand("hello", space)));
            assertFalse(embeddings.supports(new TextEmbeddingCommand("hello",
                    new EmbeddingSpace("other-space", 3))));
            assertEquals(List.of(0.1, -0.2, 0.3), embeddings.execute(
                    new TextEmbeddingCommand("hello", space), context).values());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
