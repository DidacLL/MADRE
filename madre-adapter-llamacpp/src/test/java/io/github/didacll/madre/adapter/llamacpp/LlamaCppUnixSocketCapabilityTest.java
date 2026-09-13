package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LlamaCppUnixSocketCapabilityTest {
    @TempDir Path temporary;

    @Test void observesHealthAndExecutesChatOverUnixDomainSocket() throws Exception {
        Assumptions.assumeTrue(unixDomainSocketsSupported());
        Path socket = temporary.resolve("llama.sock").toAbsolutePath();
        AtomicReference<String> chatRequest = new AtomicReference<>();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            Files.deleteIfExists(socket);
            server.bind(UnixDomainSocketAddress.of(socket));
            Thread fixture = Thread.ofPlatform().name("llama-uds-fixture").start(() -> {
                try {
                    try (SocketChannel connection = server.accept()) {
                        String request = new String(readRequest(connection), StandardCharsets.UTF_8);
                        assertTrue(request.startsWith("GET /health HTTP/1.1"));
                        write(connection, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
                                + "Connection: close\r\n\r\n{}");
                    }
                    try (SocketChannel connection = server.accept()) {
                        String request = new String(readRequest(connection), StandardCharsets.UTF_8);
                        chatRequest.set(request);
                        String json = "{\"choices\":[{\"message\":{\"content\":\"uds text\"},"
                                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":4,"
                                + "\"completion_tokens\":2}}";
                        byte[] body = json.getBytes(StandardCharsets.UTF_8);
                        String header = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n"
                                + "Connection: close\r\n\r\n"
                                + Integer.toHexString(body.length) + "\r\n";
                        write(connection, header);
                        write(connection, body);
                        write(connection, "\r\n0\r\n\r\n");
                    }
                } catch (Throwable throwable) {
                    serverFailure.set(throwable);
                }
            });

            LlamaCppUnixSocketCapability capability = new LlamaCppUnixSocketCapability(
                    configuration(socket));
            assertEquals(CapabilityAvailability.AVAILABLE, capability.availability());
            TextInferenceResult result = capability.execute(
                    new TextInferenceCommand("hello over uds", 16, List.of("stop")),
                    new ExecutionContext(Instant.now().plusSeconds(5), () -> false, 1));

            assertEquals("uds text", result.text());
            assertEquals(4, result.promptTokens());
            assertEquals(2, result.generatedTokens());
            assertTrue(chatRequest.get().startsWith("POST /v1/chat/completions HTTP/1.1"));
            assertTrue(chatRequest.get().contains("\"model\":\"installed-model\""));
            assertTrue(chatRequest.get().contains("hello over uds"));

            fixture.join(Duration.ofSeconds(5).toMillis());
            assertFalse(fixture.isAlive(), "Unix-socket fixture did not terminate");
            if (serverFailure.get() != null) {
                throw new AssertionError("Unix-socket fixture failed", serverFailure.get());
            }
        } finally {
            Files.deleteIfExists(socket);
        }
    }

    @Test void absentSocketIsNeverReportedAvailable() throws Exception {
        Assumptions.assumeTrue(unixDomainSocketsSupported());
        Path socket = temporary.resolve("missing.sock").toAbsolutePath();
        LlamaCppUnixSocketCapability capability = new LlamaCppUnixSocketCapability(
                configuration(socket));
        assertEquals(CapabilityAvailability.UNAVAILABLE, capability.availability());

        CapabilityException failure = assertThrows(CapabilityException.class,
                () -> capability.execute(new TextInferenceCommand("hello", 1, List.of()),
                        new ExecutionContext(Instant.now().plusSeconds(1), () -> false, 1)));
        assertEquals(PhysicalFailureCategory.CONNECTION, failure.category());
    }

    @Test void configurationRequiresAbsoluteSocketPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlamaCppUnixSocketConfiguration(new CapabilityId("relative"),
                        Path.of("llama.sock"), "model", Privacy.P5, Integrity.I5,
                        Duration.ofSeconds(1), List.of()));
    }

    private LlamaCppUnixSocketConfiguration configuration(Path socket) {
        return new LlamaCppUnixSocketConfiguration(new CapabilityId("llama-uds"), socket,
                "installed-model", Privacy.P5, Integrity.I5, Duration.ofSeconds(1), List.of());
    }

    private static boolean unixDomainSocketsSupported() {
        try (ServerSocketChannel probe = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            return probe.isOpen();
        } catch (IOException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static byte[] readRequest(SocketChannel connection) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int expectedLength = -1;
        int headerEnd = -1;
        while (true) {
            int count = connection.read(buffer);
            if (count < 0) break;
            if (count == 0) continue;
            bytes.write(buffer.array(), 0, count);
            buffer.clear();
            byte[] current = bytes.toByteArray();
            if (headerEnd < 0) {
                headerEnd = find(current, new byte[] {'\r', '\n', '\r', '\n'});
                if (headerEnd >= 0) {
                    expectedLength = contentLength(current, headerEnd);
                    if (expectedLength == 0) break;
                }
            }
            if (headerEnd >= 0 && expectedLength >= 0
                    && current.length >= headerEnd + 4 + expectedLength) break;
        }
        return bytes.toByteArray();
    }

    private static int contentLength(byte[] request, int headerEnd) {
        String header = new String(request, 0, headerEnd, StandardCharsets.ISO_8859_1);
        for (String line : header.split("\\r\\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                return Integer.parseInt(line.substring(line.indexOf(':') + 1).strip());
            }
        }
        return 0;
    }

    private static int find(byte[] source, byte[] needle) {
        outer:
        for (int index = 0; index <= source.length - needle.length; index++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (source[index + offset] != needle[offset]) continue outer;
            }
            return index;
        }
        return -1;
    }

    private static void write(SocketChannel connection, String value) throws IOException {
        write(connection, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(SocketChannel connection, byte[] value) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        while (buffer.hasRemaining()) connection.write(buffer);
    }
}
