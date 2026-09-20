package io.github.didacll.madre.kernel.client;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class KernelClientProcess {
    private KernelClientProcess() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: <submit|collect|inspect|cancel|version-mismatch> <socket> ...");
        }
        LocalKernelClient client = new LocalKernelClient(Path.of(args[1]));
        switch (args[0]) {
            case "submit" -> submit(client, args);
            case "collect" -> collect(client, args);
            case "inspect" -> inspect(client);
            case "cancel" -> cancel(client, args);
            case "version-mismatch" -> versionMismatch(Path.of(args[1]));
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
        if (engines.size() != 1 || !engines.getFirst().engineId().equals("fake-local")) {
            throw new AssertionError("fake engine inventory mismatch: " + engines);
        }
        EngineDescriptor status = client.engineStatus("fake-local");
        if (!status.availability().equals("AVAILABLE")) {
            throw new AssertionError("fake engine is not available");
        }
        System.out.println("ENGINE fake-local AVAILABLE");
    }

    private static void versionMismatch(Path endpoint) throws Exception {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(endpoint));
            Protocol.write(channel, new Protocol.Frame(
                    Protocol.HELLO, 999,
                    java.util.Map.of("min_version", "2", "max_version", "2"),
                    new byte[0]));
            Protocol.Frame response = Protocol.read(channel);
            if (response.type() != Protocol.ERROR ||
                    !"VERSION_MISMATCH".equals(response.metadata().get("code"))) {
                throw new AssertionError("incompatible protocol range was not rejected: " + response);
            }
        }
        System.out.println("VERSION_MISMATCH rejected");
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
}
