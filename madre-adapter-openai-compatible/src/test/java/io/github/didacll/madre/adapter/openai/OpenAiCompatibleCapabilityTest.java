package io.github.didacll.madre.adapter.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.text.TextInferenceCommand;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleCapabilityTest {
    @Test void usesExternallyPreparedTransportAndInstalledModel() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String body = request.contains("installed-model")
                    ? "{\"choices\":[{\"message\":{\"content\":\"compatible text\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}"
                    : "{}";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            AtomicInteger calls = new AtomicInteger(); HttpClient client = HttpClient.newHttpClient();
            PreparedHttpTransport prepared = request -> { calls.incrementAndGet(); return client.send(request, HttpResponse.BodyHandlers.ofString()); };
            OpenAiCompatibleCapability capability = new OpenAiCompatibleCapability(new OpenAiCompatibleConfiguration(new CapabilityId("compatible"),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/"), "installed-model",
                    Privacy.UNKNOWN, Integrity.I4, PhysicalLocation.REMOTE, java.time.Duration.ofMillis(20), List.of()), prepared);
            assertEquals("compatible text", capability.execute(new TextInferenceCommand("hello", 8, List.of()),
                    new ExecutionContext(Instant.now().plusSeconds(2), () -> false, 1)).text());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
}
