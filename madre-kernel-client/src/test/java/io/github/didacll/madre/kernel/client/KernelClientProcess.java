package io.github.didacll.madre.kernel.client;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public final class KernelClientProcess {
    private KernelClientProcess() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: <submit|submit-type|submit-config|status|collect|inspect|cancel|cancel-work|framing-mismatch|protocol-version-mismatch|protocol-overlap|payload-limits> <socket> ...");
        }
        LocalKernelClient client = new LocalKernelClient(Path.of(args[1]));
        switch (args[0]) {
            case "submit" -> submit(client, args);
            case "submit-type" -> submitType(client, args);
            case "submit-config" -> submitConfig(client, args);
            case "status" -> status(client, args);
            case "collect" -> collect(client, args);
            case "inspect" -> inspect(client);
            case "cancel" -> cancel(client, args);
            case "cancel-work" -> cancelWork(client, args);
            case "framing-mismatch" -> framingMismatch(Path.of(args[1]));
            case "protocol-version-mismatch" -> protocolVersionMismatch(Path.of(args[1]));
            case "protocol-overlap" -> protocolOverlap(Path.of(args[1]));
            case "payload-limits" -> payloadLimits(Path.of(args[1]));
            default -> throw new IllegalArgumentException("unknown test command: " + args[0]);
        }
    }

    private static void submit(LocalKernelClient client, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("submit requires payload text");
        }
        WorkId id = client.submit(new WorkRequest("text-generation/v1", args[2].getBytes(StandardCharsets.UTF_8)));
        System.out.println(id.value());
    }

    private static void submitType(LocalKernelClient client, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("submit-type requires Work type and payload text");
        }
        WorkId id = client.submit(new WorkRequest(args[2], args[3].getBytes(StandardCharsets.UTF_8)));
        System.out.println(id.value());
    }

    private static void submitConfig(LocalKernelClient client, String[] args) {
        if (args.length != 14) {
            throw new IllegalArgumentException(
                    "submit-config requires payload effort urgency capabilities allowlist exactEngine exactModel eligibleAt deadline timeout maxAttempts retryDelay");
        }
        WorkRequest request = new WorkRequest(
                "text-generation/v1",
                args[2].getBytes(StandardCharsets.UTF_8),
                Effort.valueOf(args[3]),
                Urgency.valueOf(args[4]),
                parseSet(args[5]),
                parseSet(args[6]),
                parseOptional(args[7]),
                parseOptional(args[8]),
                parseOptionalLong(args[9]),
                parseOptionalLong(args[10]),
                parseOptionalLong(args[11]),
                new RetryPolicy(Integer.parseInt(args[12]), Long.parseLong(args[13])));
        System.out.println(client.submit(request).value());
    }

    private static void status(LocalKernelClient client, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("status requires WorkId");
        }
        System.out.println(client.status(new WorkId(args[2])));
    }

    private static void collect(LocalKernelClient client, String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("collect requires WorkId and expected result");
        }
        WorkId id = new WorkId(args[2]);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        WorkStatus state;
        do {
            state = client.status(id);
            if (state == WorkStatus.FAILED || state == WorkStatus.CANCELLED) {
                throw new AssertionError("Work reached unexpected terminal state: " + state);
            }
            if (state != WorkStatus.SUCCEEDED) {
                Thread.sleep(50);
            }
        } while (state != WorkStatus.SUCCEEDED && Instant.now().isBefore(deadline));
        if (state != WorkStatus.SUCCEEDED) {
            throw new AssertionError("Work did not complete before timeout; state=" + state);
        }
        WorkResult result = client.result(id).orElseThrow(() -> new AssertionError("durable result missing"));
        String text = new String(result.payload(), StandardCharsets.UTF_8);
        if (!text.equals(args[3])) {
            throw new AssertionError("unexpected result: " + text);
        }
        client.acknowledge(id);
        if (client.result(id).isPresent()) {
            throw new AssertionError("acknowledged result remained available");
        }
        System.out.println("COLLECTED " + id.value());
    }

    private static void inspect(LocalKernelClient client) {
        List<EngineDescriptor> engines = client.engines();
        if (engines.size() != 3) {
            throw new AssertionError("fake engine inventory size mismatch: " + engines);
        }
        List<String> ids = engines.stream().map(EngineDescriptor::engineId).toList();
        if (!ids.equals(List.of("fake-standard", "fake-capable", "fake-vision"))) {
            throw new AssertionError("fake engine inventory order mismatch: " + ids);
        }
        for (EngineDescriptor listed : engines) {
            EngineDescriptor status = client.engineStatus(listed.engineId());
            if (!status.equals(listed)) {
                throw new AssertionError("ENGINE_STATUS did not preserve Kernel descriptor facts: listed=" +
                        listed + " status=" + status);
            }
            if (!"KERNEL_PROCESS".equals(status.placement())) {
                throw new AssertionError("Kernel placement fact was not preserved: " + status);
            }
        }
        EngineDescriptor standard = engines.get(0);
        EngineDescriptor capable = engines.get(1);
        EngineDescriptor vision = engines.get(2);
        if (!standard.supportedEfforts().equals(Set.of(Effort.STANDARD)) ||
                !standard.modelIds().contains("standard-v1") ||
                standard.supportedCapabilities().contains("structured-output")) {
            throw new AssertionError("fake-standard descriptor is not factual/distinct: " + standard);
        }
        if (!capable.supportedEfforts().containsAll(Set.of(Effort.STANDARD, Effort.HIGH)) ||
                !capable.supportedCapabilities().contains("structured-output") ||
                !capable.modelIds().contains("high-v1")) {
            throw new AssertionError("fake-capable descriptor is not factual/distinct: " + capable);
        }
        if (!vision.supportedEfforts().equals(Set.of(Effort.HIGH)) ||
                !vision.supportedCapabilities().contains("image-input") ||
                !vision.modelIds().contains("vision-v1")) {
            throw new AssertionError("fake-vision descriptor is not factual/distinct: " + vision);
        }
        System.out.println("ENGINE_INVENTORY 3 factual KERNEL_PROCESS descriptors");
    }

    private static void framingMismatch(Path endpoint) throws Exception {
        try (SocketChannel channel = connect(endpoint)) {
            writeRawHeader(channel, Protocol.FRAMING_VERSION + 1, Protocol.HELLO, 997, 0, 0);
            Protocol.Frame response = Protocol.read(channel);
            requireError(response, "FRAMING_ERROR", "incompatible framing version");
        }
        System.out.println("FRAMING_ERROR rejected incompatible framing version");
    }

    private static void protocolVersionMismatch(Path endpoint) throws Exception {
        try (SocketChannel channel = connect(endpoint)) {
            Protocol.write(channel, new Protocol.Frame(
                    Protocol.HELLO,
                    998,
                    Map.of(
                            "min_kernel_protocol_version", Integer.toString(Protocol.MAX_KERNEL_PROTOCOL_VERSION + 1),
                            "max_kernel_protocol_version", Integer.toString(Protocol.MAX_KERNEL_PROTOCOL_VERSION + 1)),
                    new byte[0]));
            Protocol.Frame response = Protocol.read(channel);
            requireError(response, "VERSION_MISMATCH", "incompatible Kernel protocol range");
        }
        System.out.println("VERSION_MISMATCH rejected incompatible Kernel protocol range");
    }

    private static void protocolOverlap(Path endpoint) throws Exception {
        try (SocketChannel channel = connect(endpoint)) {
            Protocol.write(channel, new Protocol.Frame(
                    Protocol.HELLO,
                    999,
                    Map.of(
                            "min_kernel_protocol_version", Integer.toString(Protocol.MIN_KERNEL_PROTOCOL_VERSION),
                            "max_kernel_protocol_version", Integer.toString(Protocol.MAX_KERNEL_PROTOCOL_VERSION + 1)),
                    new byte[0]));
            Protocol.Frame hello = Protocol.read(channel);
            if (hello.type() != Protocol.HELLO_RESPONSE) {
                throw new AssertionError("overlapping Kernel protocol range did not negotiate: " + hello);
            }
            int negotiated = Integer.parseInt(hello.metadata().getOrDefault("kernel_protocol_version", "-1"));
            if (negotiated < Protocol.MIN_KERNEL_PROTOCOL_VERSION ||
                    negotiated > Protocol.MAX_KERNEL_PROTOCOL_VERSION) {
                throw new AssertionError("negotiated Kernel protocol version outside supported overlap: " + negotiated);
            }

            Protocol.write(channel, new Protocol.Frame(
                    Protocol.LIST_ENGINES,
                    1000,
                    Map.of(),
                    new byte[0]));
            Protocol.Frame response = Protocol.read(channel);
            if (response.type() != Protocol.LIST_ENGINES_RESPONSE) {
                throw new AssertionError("connection did not continue after compatible negotiation: " + response);
            }
        }
        System.out.println("KERNEL_PROTOCOL overlap negotiated and continued");
    }

    private static void payloadLimits(Path endpoint) throws Exception {
        byte[] oversized = new byte[Protocol.MAX_C1_TEXT_GENERATION_OPAQUE_PAYLOAD_BYTES + 1];

        boolean requestRejected = false;
        try {
            new WorkRequest("text-generation/v1", oversized);
        } catch (IllegalArgumentException expected) {
            requestRejected = true;
        }
        if (!requestRejected) {
            throw new AssertionError(">1 MiB C1 WorkRequest was not rejected before cloning");
        }

        boolean javaFrameRejected = false;
        try {
            new Protocol.Frame(Protocol.SUBMIT, 1001, Map.of("work_type", "text-generation/v1"), oversized);
        } catch (IllegalArgumentException expected) {
            javaFrameRejected = true;
        }
        if (!javaFrameRejected) {
            throw new AssertionError(">1 MiB Java frame send was not rejected before cloning");
        }

        try (SocketChannel channel = connect(endpoint)) {
            negotiateCurrentProtocol(channel, 1002);
            writeRawHeader(
                    channel,
                    Protocol.FRAMING_VERSION,
                    Protocol.SUBMIT,
                    1003,
                    0,
                    (long) Protocol.MAX_C1_TEXT_GENERATION_OPAQUE_PAYLOAD_BYTES + 1L);
            Protocol.Frame response = Protocol.read(channel);
            requireError(response, "FRAMING_ERROR", ">1 MiB native frame receive");
        }

        System.out.println("C1_PAYLOAD_LIMIT rejected >1 MiB");
    }

    private static void negotiateCurrentProtocol(SocketChannel channel, long correlation) throws Exception {
        Protocol.write(channel, new Protocol.Frame(
                Protocol.HELLO,
                correlation,
                Map.of(
                        "min_kernel_protocol_version", Integer.toString(Protocol.MIN_KERNEL_PROTOCOL_VERSION),
                        "max_kernel_protocol_version", Integer.toString(Protocol.MAX_KERNEL_PROTOCOL_VERSION)),
                new byte[0]));
        Protocol.Frame hello = Protocol.read(channel);
        if (hello.type() != Protocol.HELLO_RESPONSE) {
            throw new AssertionError("Kernel protocol negotiation failed: " + hello);
        }
    }

    private static SocketChannel connect(Path endpoint) throws Exception {
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        channel.connect(UnixDomainSocketAddress.of(endpoint));
        return channel;
    }

    private static void writeRawHeader(
            SocketChannel channel,
            int framingVersion,
            int type,
            long correlation,
            int metadataLength,
            long payloadLength) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN);
        header.put((byte) 'M');
        header.put((byte) 'A');
        header.put((byte) 'D');
        header.put((byte) 'R');
        header.putShort((short) framingVersion);
        header.putShort((short) type);
        header.putLong(correlation);
        header.putInt(metadataLength);
        header.putLong(payloadLength);
        header.flip();
        while (header.hasRemaining()) {
            channel.write(header);
        }
    }

    private static void requireError(Protocol.Frame response, String code, String description) {
        if (response.type() != Protocol.ERROR || !code.equals(response.metadata().get("code"))) {
            throw new AssertionError(description + " was not rejected with " + code + ": " + response);
        }
    }

    private static void cancel(LocalKernelClient client, String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("cancel requires payload text");
        }
        WorkId id = client.submit(new WorkRequest("text-generation/v1", args[2].getBytes(StandardCharsets.UTF_8)));
        client.cancel(id);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        WorkStatus status;
        do {
            status = client.status(id);
            if (status != WorkStatus.CANCELLED) {
                Thread.sleep(20);
            }
        } while (status != WorkStatus.CANCELLED && Instant.now().isBefore(deadline));
        if (status != WorkStatus.CANCELLED) {
            throw new AssertionError("cancel did not terminate Work; state=" + status);
        }
        System.out.println("CANCELLED " + id.value());
    }

    private static void cancelWork(LocalKernelClient client, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("cancel-work requires WorkId");
        }
        System.out.println(client.cancel(new WorkId(args[2])));
    }

    private static Set<String> parseSet(String value) {
        if ("-".equals(value)) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    private static Optional<String> parseOptional(String value) {
        return "-".equals(value) ? Optional.empty() : Optional.of(value);
    }

    private static OptionalLong parseOptionalLong(String value) {
        return "-".equals(value) ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
    }
}
