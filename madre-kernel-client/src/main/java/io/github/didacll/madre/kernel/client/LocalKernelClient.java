package io.github.didacll.madre.kernel.client;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalKernelClient implements KernelClient {
    private static final byte[] EMPTY = new byte[0];
    private final Path endpoint;
    private final AtomicLong correlation = new AtomicLong(1);

    public LocalKernelClient(Path endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint").toAbsolutePath();
    }

    @Override
    public WorkId submit(WorkRequest request) {
        Objects.requireNonNull(request, "request");
        Protocol.Frame response = exchange(new Protocol.Frame(
                Protocol.SUBMIT,
                nextCorrelation(),
                Map.of("work_type", request.workType()),
                request.input()));
        expectType(response, Protocol.SUBMIT_RESPONSE);
        return new WorkId(required(response, "work_id"));
    }

    @Override
    public WorkStatus status(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.STATUS, id));
        expectType(response, Protocol.STATUS_RESPONSE);
        return WorkStatus.valueOf(required(response, "state"));
    }

    @Override
    public Optional<WorkResult> result(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.RESULT, id));
        expectType(response, Protocol.RESULT_RESPONSE);
        if (!Boolean.parseBoolean(required(response, "available"))) {
            return Optional.empty();
        }
        return Optional.of(new WorkResult(response.payload()));
    }

    @Override
    public WorkStatus cancel(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.CANCEL, id));
        expectType(response, Protocol.CANCEL_RESPONSE);
        return WorkStatus.valueOf(required(response, "state"));
    }

    @Override
    public void acknowledge(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.ACKNOWLEDGE, id));
        expectType(response, Protocol.ACKNOWLEDGE_RESPONSE);
        if (!Boolean.parseBoolean(required(response, "acknowledged"))) {
            throw new KernelProtocolException("ACKNOWLEDGEMENT_REJECTED", "Kernel did not acknowledge result consumption");
        }
    }

    @Override
    public List<EngineDescriptor> engines() {
        Protocol.Frame response = exchange(new Protocol.Frame(
                Protocol.LIST_ENGINES, nextCorrelation(), Map.of(), EMPTY));
        expectType(response, Protocol.LIST_ENGINES_RESPONSE);
        int count = Integer.parseInt(required(response, "count"));
        List<EngineDescriptor> engines = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            String prefix = "engine." + i + ".";
            engines.add(new EngineDescriptor(
                    required(response, prefix + "id"),
                    splitSet(required(response, prefix + "work_types")),
                    splitSet(required(response, prefix + "capabilities")),
                    required(response, prefix + "placement"),
                    required(response, prefix + "availability"),
                    Boolean.parseBoolean(required(response, prefix + "warm"))));
        }
        return List.copyOf(engines);
    }

    @Override
    public EngineDescriptor engineStatus(String engineId) {
        Objects.requireNonNull(engineId, "engineId");
        Protocol.Frame response = exchange(new Protocol.Frame(
                Protocol.ENGINE_STATUS,
                nextCorrelation(),
                Map.of("engine_id", engineId),
                EMPTY));
        expectType(response, Protocol.ENGINE_STATUS_RESPONSE);
        return new EngineDescriptor(
                required(response, "engine_id"),
                Set.of(),
                Set.of(),
                "LOCAL_MACHINE",
                required(response, "availability"),
                Boolean.parseBoolean(required(response, "warm")));
    }

    private Protocol.Frame command(int type, WorkId id) {
        Objects.requireNonNull(id, "id");
        return new Protocol.Frame(type, nextCorrelation(), Map.of("work_id", id.value()), EMPTY);
    }

    private Protocol.Frame exchange(Protocol.Frame command) {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(endpoint));
            long helloCorrelation = nextCorrelation();
            Protocol.write(channel, new Protocol.Frame(
                    Protocol.HELLO,
                    helloCorrelation,
                    Map.of("min_version", Integer.toString(Protocol.VERSION),
                           "max_version", Integer.toString(Protocol.VERSION)),
                    EMPTY));
            Protocol.Frame hello = Protocol.read(channel);
            checkError(hello);
            expectType(hello, Protocol.HELLO_RESPONSE);
            if (hello.correlationId() != helloCorrelation ||
                    Integer.parseInt(required(hello, "version")) != Protocol.VERSION) {
                throw new KernelProtocolException("VERSION_NEGOTIATION_FAILED", "Kernel returned an incompatible protocol version");
            }

            Protocol.write(channel, command);
            Protocol.Frame response = Protocol.read(channel);
            checkError(response);
            if (response.correlationId() != command.correlationId()) {
                throw new KernelProtocolException("CORRELATION_MISMATCH", "Kernel response correlation id did not match request");
            }
            return response;
        } catch (IOException ex) {
            throw new KernelProtocolException("IPC_FAILURE", ex.getMessage());
        }
    }

    private static void checkError(Protocol.Frame frame) {
        if (frame.type() == Protocol.ERROR) {
            throw new KernelProtocolException(required(frame, "code"), required(frame, "message"));
        }
    }

    private static void expectType(Protocol.Frame frame, int type) {
        if (frame.type() != type) {
            throw new KernelProtocolException("UNEXPECTED_RESPONSE", "unexpected Kernel response type: " + frame.type());
        }
    }

    private static String required(Protocol.Frame frame, String key) {
        String value = frame.metadata().get(key);
        if (value == null) {
            throw new KernelProtocolException("MALFORMED_RESPONSE", "missing Kernel metadata field: " + key);
        }
        return value;
    }

    private static Set<String> splitSet(String value) {
        if (value.isBlank()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(value.split(","))));
    }

    private long nextCorrelation() {
        return correlation.getAndIncrement();
    }
}
