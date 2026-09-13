package io.github.didacll.madre.adapter.searxng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.web.WebSearchCommand;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class SearxngCapabilityTest {
    @Test void probesHealthAndExecutesDocumentedJsonSearchProtocol() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicInteger healthStatus = new AtomicInteger(200);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/healthz", exchange -> {
            int status = healthStatus.get();
            byte[] body = status >= 200 && status < 300
                    ? "OK".getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.sendResponseHeaders(status, body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/search", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = ("{\"results\":["
                    + "{\"title\":\"First\",\"url\":\"https://example.test/1\","
                    + "\"content\":\"Excerpt one\"},"
                    + "{\"title\":\"Second\",\"url\":\"https://example.test/2\","
                    + "\"content\":\"Excerpt two\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            SearxngCapability capability = new SearxngCapability(new SearxngConfiguration(
                    new CapabilityId("fixture-search"),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"),
                    Privacy.UNKNOWN, Integrity.I2, Duration.ofMillis(500), List.of()));

            assertEquals(CapabilityAvailability.AVAILABLE, capability.availability());
            healthStatus.set(503);
            assertEquals(CapabilityAvailability.UNAVAILABLE, capability.availability());
            healthStatus.set(200);

            var result = capability.execute(new WebSearchCommand("privacy algebra", 1),
                    new ExecutionContext(Instant.now().plusSeconds(5), () -> false, 1));

            assertEquals(1, result.hits().size());
            assertEquals("First", result.hits().get(0).title());
            assertEquals(Privacy.UNKNOWN, capability.manifest().receivingPrivacy());
            assertTrue(rawQuery.get().contains("q=privacy+algebra"));
            assertTrue(rawQuery.get().contains("format=json"));
        } finally {
            server.stop(0);
        }
    }
}
