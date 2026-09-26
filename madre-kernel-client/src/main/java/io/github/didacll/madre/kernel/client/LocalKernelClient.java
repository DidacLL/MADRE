package io.github.didacll.madre.kernel.client;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalKernelClient implements KernelClient {
    private static final byte[] EMPTY = new byte[0];
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    private final Path endpoint;
    private final AtomicLong correlation = new AtomicLong(1);

    public LocalKernelClient(Path endpoint) {
        Path value = Objects.requireNonNull(endpoint, "endpoint");
        this.endpoint = WINDOWS ? value : value.toAbsolutePath();
    }

    @Override
    public WorkId submit(WorkRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("urgency", request.urgency().name());
        putOptionalLong(metadata, "eligible_at_ms", request.eligibleAtMs());
        putOptionalLong(metadata, "deadline_ms", request.deadlineMs());
        putOptionalLong(metadata, "attempt_timeout_ms", request.attemptTimeoutMs());
        metadata.put("max_attempts", Integer.toString(request.retry().maxAttempts()));
        metadata.put("retry_delay_ms", Long.toString(request.retry().retryDelayMs()));
        metadata.put("retry_safety", request.retry().safety().name());
        metadata.put("candidate_count", Integer.toString(request.candidates().size()));

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (int index = 0; index < request.candidates().size(); ++index) {
            ConcretePhysicalInvocation candidate = request.candidates().get(index);
            String prefix = "candidate." + index + ".";
            metadata.put(prefix + "id", candidate.id().value());
            metadata.put(prefix + "kind", candidate.kind().name());
            candidate.targetIdentity().ifPresent(value -> metadata.put(prefix + "target_identity", value));
            byte[] requestPayload = candidate.requestPayload();
            metadata.put(prefix + "payload_offset", Integer.toString(payload.size()));
            metadata.put(prefix + "payload_length", Integer.toString(requestPayload.length));
            payload.writeBytes(requestPayload);

            if (candidate instanceof ProcessInvocation process) {
                metadata.put(prefix + "executable", process.executable());
                metadata.put(prefix + "arg_count", Integer.toString(process.arguments().size()));
                for (int arg = 0; arg < process.arguments().size(); ++arg) metadata.put(prefix + "arg." + arg, process.arguments().get(arg));
            } else if (candidate instanceof HttpInvocation http) {
                metadata.put(prefix + "uri", http.uri().toASCIIString());
                metadata.put(prefix + "header_count", Integer.toString(http.headers().size()));
                for (int headerIndex = 0; headerIndex < http.headers().size(); ++headerIndex) {
                    HttpHeader header = http.headers().get(headerIndex);
                    String hp = prefix + "header." + headerIndex + ".";
                    metadata.put(hp + "name", header.name());
                    if (header instanceof LiteralHttpHeader literal) {
                        metadata.put(hp + "source", "LITERAL");
                        metadata.put(hp + "value", literal.value());
                    } else if (header instanceof EnvironmentHttpHeader environment) {
                        metadata.put(hp + "source", "ENVIRONMENT");
                        metadata.put(hp + "environment_variable", environment.environmentVariable());
                        metadata.put(hp + "prefix", environment.prefix());
                        metadata.put(hp + "suffix", environment.suffix());
                    } else {
                        throw new IllegalArgumentException("unsupported HTTP header variant: " + header.getClass().getName());
                    }
                }
            } else {
                throw new IllegalArgumentException("unsupported concrete physical invocation variant: " + candidate.getClass().getName());
            }
        }
        if (payload.size() > Protocol.MAX_BOUNDED_PAYLOAD_BYTES) throw new IllegalArgumentException("total candidate request payload exceeds bounded submission limit");

        Protocol.Frame response = exchange(new Protocol.Frame(Protocol.SUBMIT, nextCorrelation(), metadata, payload.toByteArray()));
        expectType(response, Protocol.SUBMIT_RESPONSE);
        return new WorkId(required(response, "work_id"));
    }

    @Override public WorkStatus status(WorkId id) { return inspect(id).status(); }

    @Override
    public WorkInspection inspect(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.STATUS, id));
        expectType(response, Protocol.STATUS_RESPONSE);
        String failure = required(response, "technical_failure");
        int attemptCount = Integer.parseInt(required(response, "attempt_count"));
        boolean hasAttempt = Boolean.parseBoolean(required(response, "has_latest_attempt"));
        Optional<AttemptInspection> latest = Optional.empty();
        if (hasAttempt) {
            String target = required(response, "attempt.target_identity");
            String attemptFailure = required(response, "attempt.technical_failure");
            latest = Optional.of(new AttemptInspection(
                    Integer.parseInt(required(response, "attempt.number")),
                    new InvocationId(required(response, "attempt.invocation_id")),
                    InvocationKind.valueOf(required(response, "attempt.kind")),
                    target.isEmpty() ? Optional.empty() : Optional.of(target),
                    AttemptStatus.valueOf(required(response, "attempt.state")),
                    Long.parseLong(required(response, "attempt.started_at_ms")),
                    optionalLongField(response, "attempt.ended_at_ms"),
                    optionalIntField(response, "attempt.exit_code"),
                    optionalIntField(response, "attempt.http_status"),
                    attemptFailure.isEmpty() ? Optional.empty() : Optional.of(attemptFailure)));
        }
        return new WorkInspection(
                WorkStatus.valueOf(required(response, "state")),
                attemptCount,
                latest,
                failure.isEmpty() ? Optional.empty() : Optional.of(failure),
                Boolean.parseBoolean(required(response, "payload_released")));
    }

    @Override
    public Optional<WorkResult> result(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.RESULT, id));
        expectType(response, Protocol.RESULT_RESPONSE);
        if (!Boolean.parseBoolean(required(response, "available"))) return Optional.empty();
        return Optional.of(new WorkResult(response.payload()));
    }

    @Override
    public WorkStatus cancel(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.CANCEL, id));
        expectType(response, Protocol.CANCEL_RESPONSE);
        return WorkStatus.valueOf(required(response, "state"));
    }

    @Override
    public void release(WorkId id) {
        Protocol.Frame response = exchange(command(Protocol.RELEASE, id));
        expectType(response, Protocol.RELEASE_RESPONSE);
        if (!Boolean.parseBoolean(required(response, "released"))) {
            throw new KernelProtocolException("RELEASE_REJECTED", "Kernel did not release retained payload content");
        }
    }

    private Protocol.Frame command(int type, WorkId id) {
        Objects.requireNonNull(id, "id");
        return new Protocol.Frame(type, nextCorrelation(), Map.of("work_id", id.value()), EMPTY);
    }

    private Protocol.Frame exchange(Protocol.Frame command) {
        try (ByteChannel channel = openChannel()) {
            long helloCorrelation = nextCorrelation();
            Protocol.write(channel, new Protocol.Frame(
                    Protocol.HELLO, helloCorrelation,
                    Map.of("min_kernel_protocol_version", Integer.toString(Protocol.MIN_KERNEL_PROTOCOL_VERSION),
                           "max_kernel_protocol_version", Integer.toString(Protocol.MAX_KERNEL_PROTOCOL_VERSION)), EMPTY));
            Protocol.Frame hello = Protocol.read(channel);
            checkError(hello);
            expectType(hello, Protocol.HELLO_RESPONSE);
            int negotiated = Integer.parseInt(required(hello, "kernel_protocol_version"));
            if (hello.correlationId() != helloCorrelation || negotiated != Protocol.MAX_KERNEL_PROTOCOL_VERSION) {
                throw new KernelProtocolException("VERSION_NEGOTIATION_FAILED", "Kernel returned an incompatible negotiated protocol/API version");
            }
            Protocol.write(channel, command);
            Protocol.Frame response = Protocol.read(channel);
            checkError(response);
            if (response.correlationId() != command.correlationId()) throw new KernelProtocolException("CORRELATION_MISMATCH", "Kernel response correlation id did not match request");
            return response;
        } catch (IOException ex) {
            throw new KernelProtocolException("IPC_FAILURE", ex.getMessage());
        }
    }

    private ByteChannel openChannel() throws IOException {
        if (!WINDOWS) {
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(endpoint));
            return channel;
        }
        IOException lastFailure = null;
        long deadline = System.nanoTime() + 2_000_000_000L;
        do {
            try {
                return new RandomAccessFile(endpoint.toString(), "rw").getChannel();
            } catch (FileNotFoundException ex) {
                lastFailure = ex;
                try { Thread.sleep(20); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting to Kernel named pipe", interrupted);
                }
            }
        } while (System.nanoTime() < deadline);
        throw lastFailure == null ? new IOException("failed to connect to Kernel named pipe") : lastFailure;
    }

    private static void checkError(Protocol.Frame frame) {
        if (frame.type() == Protocol.ERROR) throw new KernelProtocolException(required(frame, "code"), required(frame, "message"));
    }
    private static void expectType(Protocol.Frame frame, int type) {
        if (frame.type() != type) throw new KernelProtocolException("UNEXPECTED_RESPONSE", "unexpected Kernel response type: " + frame.type());
    }
    private static String required(Protocol.Frame frame, String key) {
        String value = frame.metadata().get(key);
        if (value == null) throw new KernelProtocolException("MALFORMED_RESPONSE", "missing Kernel metadata field: " + key);
        return value;
    }
    private static void putOptionalLong(Map<String, String> metadata, String key, OptionalLong value) {
        if (value.isPresent()) metadata.put(key, Long.toString(value.getAsLong()));
    }
    private static OptionalLong optionalLongField(Protocol.Frame frame, String key) {
        String value = required(frame, key);
        return value.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
    }
    private static OptionalInt optionalIntField(Protocol.Frame frame, String key) {
        String value = required(frame, key);
        return value.isEmpty() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value));
    }
    private long nextCorrelation() { return correlation.getAndIncrement(); }
}
