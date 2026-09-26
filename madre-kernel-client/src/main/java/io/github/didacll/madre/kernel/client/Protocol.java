package io.github.didacll.madre.kernel.client;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class Protocol {
    static final int FRAMING_VERSION = 1;
    static final int MIN_KERNEL_PROTOCOL_VERSION = 3;
    static final int MAX_KERNEL_PROTOCOL_VERSION = 3;
    static final int MAX_BOUNDED_PAYLOAD_BYTES = 1024 * 1024;

    static final int HELLO = 1;
    static final int HELLO_RESPONSE = 2;
    static final int SUBMIT = 10;
    static final int SUBMIT_RESPONSE = 11;
    static final int STATUS = 20;
    static final int STATUS_RESPONSE = 21;
    static final int RESULT = 30;
    static final int RESULT_RESPONSE = 31;
    static final int ACKNOWLEDGE = 40;
    static final int ACKNOWLEDGE_RESPONSE = 41;
    static final int CANCEL = 50;
    static final int CANCEL_RESPONSE = 51;
    static final int ERROR = 90;

    private static final byte[] MAGIC = {'M', 'A', 'D', 'R'};
    private static final int HEADER_SIZE = 28;
    private static final int MAX_METADATA = 1024 * 1024;

    private Protocol() {}

    record Frame(int type, long correlationId, Map<String, String> metadata, byte[] payload) {
        Frame {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(payload, "payload");
            if (payload.length > MAX_BOUNDED_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("frame exceeds bounded physical payload limit");
            }
            metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    static void write(ByteChannel channel, Frame frame) throws IOException {
        byte[] metadata = encodeMetadata(frame.metadata());
        byte[] payload = frame.payload();
        if (metadata.length > MAX_METADATA || payload.length > MAX_BOUNDED_PAYLOAD_BYTES) {
            throw new IOException("frame exceeds bounded physical payload limits");
        }
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        header.put(MAGIC);
        header.putShort((short) FRAMING_VERSION);
        header.putShort((short) frame.type());
        header.putLong(frame.correlationId());
        header.putInt(metadata.length);
        header.putLong(payload.length);
        header.flip();
        writeFully(channel, header);
        writeFully(channel, ByteBuffer.wrap(metadata));
        writeFully(channel, ByteBuffer.wrap(payload));
    }

    static Frame read(ByteChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        readFully(channel, header);
        header.flip();
        for (byte expected : MAGIC) {
            if (header.get() != expected) {
                throw new IOException("invalid framing magic");
            }
        }
        int framingVersion = Short.toUnsignedInt(header.getShort());
        if (framingVersion != FRAMING_VERSION) {
            throw new IOException("unsupported framing version: " + framingVersion);
        }
        int type = Short.toUnsignedInt(header.getShort());
        long correlation = header.getLong();
        int metadataLength = header.getInt();
        long payloadLength = header.getLong();
        if (metadataLength < 0 || metadataLength > MAX_METADATA || payloadLength < 0 || payloadLength > MAX_BOUNDED_PAYLOAD_BYTES) {
            throw new IOException("frame exceeds bounded physical payload limits");
        }
        ByteBuffer metadata = ByteBuffer.allocate(metadataLength);
        readFully(channel, metadata);
        ByteBuffer payload = ByteBuffer.allocate((int) payloadLength);
        readFully(channel, payload);
        return new Frame(type, correlation, decodeMetadata(metadata.array()), payload.array());
    }

    private static byte[] encodeMetadata(Map<String, String> metadata) throws IOException {
        StringBuilder out = new StringBuilder();
        for (var entry : new TreeMap<>(metadata).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.isBlank() || key.indexOf('=') >= 0 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0 ||
                    value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IOException("invalid framed metadata");
            }
            out.append(key).append('=').append(value).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> decodeMetadata(byte[] encoded) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        String text = new String(encoded, StandardCharsets.UTF_8);
        for (String line : text.split("\\n", -1)) {
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("malformed framed metadata");
            result.put(line.substring(0, equals), line.substring(equals + 1));
        }
        return result;
    }

    private static void writeFully(ByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    private static void readFully(ByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw new EOFException("peer closed framed IPC");
        }
    }
}
