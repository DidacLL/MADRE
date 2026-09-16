package io.github.didacll.madre.adapter.llamacpp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Minimal HTTP/1.1 exchange over a local Unix-domain socket. */
final class UnixSocketHttpExchange {
    private static final int MAXIMUM_RESPONSE_BYTES = 32 * 1024 * 1024;

    private UnixSocketHttpExchange() { }

    static Response exchange(Path socketPath, String method, String target, byte[] body,
            Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(socketPath, "socketPath");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new SocketTimeoutException("Unix-socket HTTP exchange timed out");
        }

        FutureTask<Response> task = new FutureTask<>(
                () -> blockingExchange(socketPath, method, target, body));
        Thread.ofVirtual().name("madre-llama-uds-http").start(task);
        try {
            return task.get(timeoutNanos(timeout), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            SocketTimeoutException timeoutFailure =
                    new SocketTimeoutException("Unix-socket HTTP exchange timed out");
            timeoutFailure.initCause(exception);
            throw timeoutFailure;
        } catch (InterruptedException exception) {
            task.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) throw ioException;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IOException("Unix-socket HTTP exchange failed", cause);
        }
    }

    private static Response blockingExchange(Path socketPath, String method, String target,
            byte[] body) throws IOException {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(address);
            byte[] request = requestBytes(method, target, body);
            ByteBuffer outgoing = ByteBuffer.wrap(request);
            while (outgoing.hasRemaining()) channel.write(outgoing);

            ByteArrayOutputStream incoming = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (true) {
                int count = channel.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                if (incoming.size() + count > MAXIMUM_RESPONSE_BYTES) {
                    throw new ProtocolException("Unix-socket HTTP response exceeds physical limit");
                }
                incoming.write(buffer.array(), 0, count);
                buffer.clear();
            }
            return parseResponse(incoming.toByteArray());
        }
    }

    private static byte[] requestBytes(String method, String target, byte[] body) {
        StringBuilder headers = new StringBuilder()
                .append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
                .append("Host: localhost\r\n")
                .append("Accept: application/json\r\n")
                .append("Connection: close\r\n");
        if (body.length > 0) {
            headers.append("Content-Type: application/json\r\n")
                    .append("Content-Length: ").append(body.length).append("\r\n");
        }
        headers.append("\r\n");
        byte[] headerBytes = headers.toString().getBytes(StandardCharsets.US_ASCII);
        byte[] request = Arrays.copyOf(headerBytes, headerBytes.length + body.length);
        System.arraycopy(body, 0, request, headerBytes.length, body.length);
        return request;
    }

    private static Response parseResponse(byte[] raw) throws ProtocolException {
        int headerEnd = find(raw, 0, new byte[] {'\r', '\n', '\r', '\n'});
        if (headerEnd < 0) throw new ProtocolException("HTTP response has no header terminator");
        String headerText = new String(raw, 0, headerEnd, StandardCharsets.ISO_8859_1);
        String[] lines = headerText.split("\\r\\n");
        if (lines.length == 0) throw new ProtocolException("HTTP response has no status line");
        String[] statusParts = lines[0].split(" ", 3);
        if (statusParts.length < 2 || !statusParts[0].startsWith("HTTP/")) {
            throw new ProtocolException("invalid HTTP status line");
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException exception) {
            throw protocol("invalid HTTP status code", exception);
        }

        Map<String, String> headers = new HashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator <= 0) continue;
            String name = lines[index].substring(0, separator).strip().toLowerCase(Locale.ROOT);
            String value = lines[index].substring(separator + 1).strip();
            headers.merge(name, value, (left, right) -> left + "," + right);
        }

        byte[] encodedBody = Arrays.copyOfRange(raw, headerEnd + 4, raw.length);
        String transferEncoding = headers.getOrDefault("transfer-encoding", "");
        byte[] responseBody;
        if (transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            responseBody = decodeChunked(encodedBody);
        } else if (headers.containsKey("content-length")) {
            long length;
            try {
                length = Long.parseLong(headers.get("content-length"));
            } catch (NumberFormatException exception) {
                throw protocol("invalid Content-Length", exception);
            }
            if (length < 0 || length > encodedBody.length || length > Integer.MAX_VALUE) {
                throw new ProtocolException("HTTP response body length is invalid");
            }
            responseBody = Arrays.copyOf(encodedBody, (int) length);
        } else {
            responseBody = encodedBody;
        }
        return new Response(statusCode, responseBody);
    }

    private static byte[] decodeChunked(byte[] encoded) throws ProtocolException {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int position = 0;
        while (true) {
            int lineEnd = find(encoded, position, new byte[] {'\r', '\n'});
            if (lineEnd < 0) throw new ProtocolException("invalid chunked response framing");
            String sizeLine = new String(encoded, position, lineEnd - position,
                    StandardCharsets.US_ASCII);
            int extension = sizeLine.indexOf(';');
            String sizeText = (extension >= 0 ? sizeLine.substring(0, extension) : sizeLine).strip();
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeText, 16);
            } catch (NumberFormatException exception) {
                throw protocol("invalid chunk size", exception);
            }
            if (chunkSize < 0 || chunkSize > Integer.MAX_VALUE) {
                throw new ProtocolException("chunk size exceeds physical limit");
            }
            position = lineEnd + 2;
            if (chunkSize == 0) return decoded.toByteArray();
            int size = (int) chunkSize;
            if (position + size + 2 > encoded.length) {
                throw new ProtocolException("truncated chunked response");
            }
            if (decoded.size() + size > MAXIMUM_RESPONSE_BYTES) {
                throw new ProtocolException("decoded HTTP response exceeds physical limit");
            }
            decoded.write(encoded, position, size);
            position += size;
            if (encoded[position] != '\r' || encoded[position + 1] != '\n') {
                throw new ProtocolException("invalid chunk terminator");
            }
            position += 2;
        }
    }

    private static int find(byte[] source, int start, byte[] needle) {
        outer:
        for (int index = start; index <= source.length - needle.length; index++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (source[index + offset] != needle[offset]) continue outer;
            }
            return index;
        }
        return -1;
    }

    private static ProtocolException protocol(String message, Exception cause) {
        ProtocolException exception = new ProtocolException(message);
        exception.initCause(cause);
        return exception;
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return Math.max(1L, timeout.toNanos());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    record Response(int statusCode, byte[] body) {
        Response {
            body = body.clone();
        }

        @Override public byte[] body() {
            return body.clone();
        }
    }
}
