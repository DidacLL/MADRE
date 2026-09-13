package io.github.didacll.madre.adapter.searxng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.web.WebSearchCommand;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class SearxngCapabilityTest {
    @Test void executesDocumentedJsonSearchProtocolAndReturnsPhysicalHits() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
                    Privacy.UNKNOWN, Integrity.I2, Duration.ofMillis(50), List.of()));

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
