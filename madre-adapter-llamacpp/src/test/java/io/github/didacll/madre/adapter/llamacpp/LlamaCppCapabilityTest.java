package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningException;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class LlamaCppCapabilityTest {
    @Test void executesChatProtocolAndClassifiesMalformedResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean malformed = new AtomicBoolean();
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String body = !malformed.get() && request.contains("installed-model")
                    ? "{\"choices\":[{\"message\":{\"content\":\"real protocol text\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3}}"
                    : "{}";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            LlamaCppReasoningCapability capability = new LlamaCppReasoningCapability(
                    new LlamaCppConfiguration(new ReasoningCapabilityId("llama"),
                            java.net.URI.create("http://127.0.0.1:"
                                    + server.getAddress().getPort()),
                            "installed-model", Privacy.SECRET,
                            java.time.Duration.ofMillis(10), List.of()));
            assertEquals(ReasoningAvailability.AVAILABLE, capability.availability());
            TextInferenceResult result = capability.execute(
                    new TextInferenceCommand("hello", 16, List.of()),
                    new ReasoningExecutionContext(Instant.now().plusSeconds(2),
                            () -> false, 1));
            assertEquals("real protocol text", result.text());
            malformed.set(true);
            ReasoningException malformedFailure = assertThrows(ReasoningException.class,
                    () -> capability.execute(new TextInferenceCommand("hello", 16, List.of()),
                            new ReasoningExecutionContext(Instant.now().plusSeconds(2),
                                    () -> false, 1)));
            assertEquals(ReasoningFailureCategory.PROTOCOL, malformedFailure.category());
        } finally {
            server.stop(0);
        }

        LlamaCppReasoningCapability unavailable = new LlamaCppReasoningCapability(
                new LlamaCppConfiguration(new ReasoningCapabilityId("closed"),
                        java.net.URI.create("http://127.0.0.1:1"), "model", Privacy.SECRET,
                        java.time.Duration.ofMillis(10), List.of()));
        ReasoningException failure = assertThrows(ReasoningException.class,
                () -> unavailable.execute(new TextInferenceCommand("hello", 1, List.of()),
                        new ReasoningExecutionContext(Instant.now().plusSeconds(1),
                                () -> false, 1)));
        assertEquals(ReasoningFailureCategory.CONNECTION, failure.category());
        assertEquals(ReasoningAvailability.UNAVAILABLE, unavailable.availability());
    }

    @Test void localHttpAdapterRejectsRemoteEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> new LlamaCppConfiguration(
                new ReasoningCapabilityId("remote"),
                java.net.URI.create("http://192.0.2.10:8080"), "model", Privacy.SECRET,
                java.time.Duration.ofMillis(10), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LlamaCppConfiguration(
                new ReasoningCapabilityId("hostname"),
                java.net.URI.create("http://localhost:8080"), "model", Privacy.SECRET,
                java.time.Duration.ofMillis(10), List.of()));
    }
}
