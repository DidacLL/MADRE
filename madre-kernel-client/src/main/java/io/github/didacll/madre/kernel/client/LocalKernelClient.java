package io.github.didacll.madre.kernel.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LocalKernelClient implements KernelClient, AutoCloseable {
    private final Path socketPath;
    private final ObjectMapper json;

    public LocalKernelClient(Path socketPath) {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath").toAbsolutePath();
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public KernelProtocolInfo protocolInfo() {
        return read(send("ProtocolInfo", null), KernelProtocolInfo.class);
    }

    @Override
    public WorkId submit(PhysicalInferenceRequest request) {
        byte[] input = request.preparedInput().getBytes(StandardCharsets.UTF_8);
        if (input.length > KernelProtocol.MAX_PAYLOAD_BYTES) {
            throw new KernelClientException("preparedInput exceeds Kernel payload bound", "InvalidRequest");
        }
        SubmissionResponse response = read(send("Submit", request), SubmissionResponse.class);
        return new WorkId(response.workId());
    }

    @Override
    public WorkInspection inspect(WorkId workId) {
        return read(send("Inspect", new WorkIdPayload(workId.value())), WorkInspection.class);
    }

    @Override
    public WorkResult result(WorkId workId) {
        return read(send("Result", new WorkIdPayload(workId.value())), WorkResult.class);
    }

    @Override
    public WorkState cancel(WorkId workId) {
        CancelResponse response = read(send("Cancel", new WorkIdPayload(workId.value())), CancelResponse.class);
        return response.state();
    }

    @Override
    public boolean release(WorkId workId) {
        ReleaseResponse response = read(send("Release", new WorkIdPayload(workId.value())), ReleaseResponse.class);
        return response.released();
    }

    @Override
    public List<CapabilitySnapshot> capabilities() {
        return read(send("Capabilities", null), new TypeReference<>() { });
    }

    @Override
    public List<CapabilitySnapshot> refreshCapabilities() {
        return read(send("RefreshCapabilities", null), new TypeReference<>() { });
    }

    private Response send(String operation, Object payload) {
        String requestId = UUID.randomUUID().toString();
        Request request = new Request(KernelProtocol.VERSION, requestId, operation, payload);
        byte[] encoded;
        try {
            encoded = json.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new KernelClientException("failed to encode Kernel IPC request", exception);
        }
        if (encoded.length > KernelProtocol.MAX_FRAME_BYTES) {
            throw new KernelClientException("Kernel IPC request exceeds frame bound", "InvalidRequest");
        }

        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socketPath));
            ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(encoded.length);
            header.flip();
            writeAll(channel, header);
            writeAll(channel, ByteBuffer.wrap(encoded));

            ByteBuffer responseHeader = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            readAll(channel, responseHeader);
            responseHeader.flip();
            int length = responseHeader.getInt();
            if (length <= 0 || length > KernelProtocol.MAX_FRAME_BYTES) {
                throw new KernelClientException("Kernel IPC response length is outside protocol bound", "ProtocolError");
            }
            ByteBuffer responseBody = ByteBuffer.allocate(length);
            readAll(channel, responseBody);
            Response response = json.readValue(responseBody.array(), Response.class);
            if (response.version() != KernelProtocol.VERSION) {
                throw new KernelClientException("Kernel IPC version mismatch", "ProtocolError");
            }
            if (!Objects.equals(requestId, response.requestId())) {
                throw new KernelClientException("Kernel IPC request correlation mismatch", "ProtocolError");
            }
            if (!response.ok()) {
                String code = response.error() == null ? "InternalFailure" : response.error().code();
                String detail = response.error() == null ? null : response.error().detail();
                throw new KernelClientException("Kernel IPC " + code + (detail == null ? "" : ": " + detail), code);
            }
            return response;
        } catch (IOException exception) {
            throw new KernelClientException("Kernel IPC request failed", exception);
        }
    }

    private <T> T read(Response response, Class<T> type) {
        try {
            return json.treeToValue(response.payload(), type);
        } catch (JsonProcessingException exception) {
            throw new KernelClientException("failed to decode Kernel IPC response", exception);
        }
    }

    private <T> T read(Response response, TypeReference<T> type) {
        return json.convertValue(response.payload(), type);
    }

    private static void writeAll(SocketChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void readAll(SocketChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("Kernel IPC connection closed before the frame completed");
            }
        }
    }

    @Override
    public void close() {
        // Each operation owns one short-lived local socket connection.
    }

    private record Request(int version, String requestId, String operation, Object payload) { }
    private record Response(int version, String requestId, boolean ok, JsonNode payload, ErrorBody error) { }
    private record ErrorBody(String code, String detail) { }
    private record WorkIdPayload(String workId) { }
    private record SubmissionResponse(String workId) { }
    private record CancelResponse(WorkState state) { }
    private record ReleaseResponse(boolean released) { }
}
