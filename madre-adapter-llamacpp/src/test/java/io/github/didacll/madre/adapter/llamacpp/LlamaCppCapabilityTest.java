package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
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
        server.createContext("/health", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String body = !malformed.get() && request.contains("installed-model")
                    ? "{\"choices\":[{\"message\":{\"content\":\"real protocol text\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3}}"
                    : "{}";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            LlamaCppCapability capability = new LlamaCppCapability(new LlamaCppConfiguration(new CapabilityId("llama"),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "installed-model",
                    Privacy.P5, Integrity.I5, java.time.Duration.ofMillis(10), List.of()));
            TextInferenceResult result = capability.execute(new TextInferenceCommand("hello", 16, List.of()),
                    new ExecutionContext(Instant.now().plusSeconds(2), () -> false, 1));
            assertEquals("real protocol text", result.text());
            malformed.set(true);
            CapabilityException malformedFailure = assertThrows(CapabilityException.class, () -> capability.execute(
                    new TextInferenceCommand("hello", 16, List.of()), new ExecutionContext(Instant.now().plusSeconds(2), () -> false, 1)));
            assertEquals(PhysicalFailureCategory.PROTOCOL, malformedFailure.category());
        } finally { server.stop(0); }

        LlamaCppCapability unavailable = new LlamaCppCapability(new LlamaCppConfiguration(new CapabilityId("closed"),
                java.net.URI.create("http://127.0.0.1:1"), "model", Privacy.P5, Integrity.I5, java.time.Duration.ofMillis(10), List.of()));
        CapabilityException failure = assertThrows(CapabilityException.class, () -> unavailable.execute(
                new TextInferenceCommand("hello", 1, List.of()), new ExecutionContext(Instant.now().plusSeconds(1), () -> false, 1)));
        assertEquals(PhysicalFailureCategory.CONNECTION, failure.category());
    }
}
