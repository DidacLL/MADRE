package io.github.didacll.madre.adapter.llamacpp;

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
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LlamaCppHeterogeneousCapabilityTest {
    @Test void executesRichGenerationAndSpaceQualifiedEmbedding() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String body = request.contains("system") && request.contains("user")
                    ? "{\"choices\":[{\"message\":{\"content\":\"local rich text\"},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3}}"
                    : "{}";
            respond(exchange, body);
        });
        server.createContext("/v1/embeddings", exchange ->
                respond(exchange, "{\"data\":[{\"embedding\":[0.4,0.5]}]}"));
        server.start();
        try {
            LlamaCppConfiguration configuration = new LlamaCppConfiguration(
                    new ReasoningCapabilityId("llama-heterogeneous"),
                    java.net.URI.create("http://127.0.0.1:"
                            + server.getAddress().getPort() + "/"),
                    "local-model", Privacy.LOCAL, java.time.Duration.ofMillis(20), List.of());
            HttpClient client = HttpClient.newHttpClient();
            ReasoningExecutionContext context = new ReasoningExecutionContext(
                    Instant.now().plusSeconds(2), () -> false, 1);

            LlamaCppTextGenerationCapability generation =
                    new LlamaCppTextGenerationCapability(configuration, client);
            var generated = generation.execute(new TextGenerationCommand(List.of(
                    new TextGenerationMessage(TextGenerationMessage.Role.SYSTEM, "Be exact."),
                    new TextGenerationMessage(TextGenerationMessage.Role.USER, "Answer.")),
                    16, List.of()), context);
            assertEquals("local rich text", generated.text());
            assertEquals(5, generated.inputTokens());

            EmbeddingSpace space = new EmbeddingSpace("local-model/space-v1", 2);
            LlamaCppEmbeddingCapability embeddings =
                    new LlamaCppEmbeddingCapability(configuration, space, client);
            assertTrue(embeddings.supports(new TextEmbeddingCommand("hello", space)));
            assertFalse(embeddings.supports(new TextEmbeddingCommand("hello",
                    new EmbeddingSpace("other-space", 2))));
            assertEquals(List.of(0.4, 0.5), embeddings.execute(
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
