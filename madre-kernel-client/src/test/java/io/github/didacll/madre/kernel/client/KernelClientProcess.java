package io.github.didacll.madre.kernel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class KernelClientProcess {
    private KernelClientProcess() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: <socket-path> <command> [arguments]");
        }
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        try (LocalKernelClient client = new LocalKernelClient(Path.of(args[0]))) {
            switch (args[1]) {
                case "protocol" -> System.out.println(json.writeValueAsString(client.protocolInfo()));
                case "submit" -> {
                    if (args.length < 3) {
                        throw new IllegalArgumentException("submit requires input");
                    }
                    System.out.println(submit(client, args[2], args, 3).value());
                }
                case "submit-generated" -> {
                    if (args.length < 3) {
                        throw new IllegalArgumentException("submit-generated requires UTF-8 byte count");
                    }
                    int byteCount = Integer.parseInt(args[2]);
                    String unit = args.length > 3 ? args[3] : "x";
                    String input = exactUtf8Bytes(unit, byteCount);
                    System.out.println(submit(client, input, args, 4).value());
                }
                case "inspect" -> System.out.println(json.writeValueAsString(client.inspect(new WorkId(args[2]))));
                case "result" -> System.out.println(json.writeValueAsString(client.result(new WorkId(args[2]))));
                case "result-equals" -> {
                    if (args.length < 4) {
                        throw new IllegalArgumentException("result-equals requires work id and expected result");
                    }
                    WorkResult result = client.result(new WorkId(args[2]));
                    if (!args[3].equals(result.result())) {
                        throw new IllegalStateException("result did not match expected value");
                    }
                    System.out.println("true");
                }
                case "cancel" -> System.out.println(client.cancel(new WorkId(args[2])));
                case "release" -> System.out.println(client.release(new WorkId(args[2])));
                case "capabilities" -> System.out.println(json.writeValueAsString(client.capabilities()));
                case "refresh" -> System.out.println(client.refreshCapabilities().size());
                case "sequential-protocol" -> {
                    int count = Integer.parseInt(args[2]);
                    for (int i = 0; i < count; i++) {
                        KernelProtocolInfo info = client.protocolInfo();
                        if (info.version() != KernelProtocol.VERSION) {
                            throw new IllegalStateException("protocol changed during sequential calls");
                        }
                    }
                    System.out.println(count);
                }
                case "parallel-protocol" -> {
                    int count = Integer.parseInt(args[2]);
                    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<KernelProtocolInfo>> calls = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            calls.add(executor.submit(client::protocolInfo));
                        }
                        for (Future<KernelProtocolInfo> call : calls) {
                            if (call.get().version() != KernelProtocol.VERSION) {
                                throw new IllegalStateException("protocol changed during concurrent calls");
                            }
                        }
                    }
                    System.out.println(count);
                }
                default -> throw new IllegalArgumentException("unknown command: " + args[1]);
            }
        }
    }

    private static WorkId submit(LocalKernelClient client, String input, String[] args, int optionIndex) {
        InferenceEffort effort = args.length > optionIndex
                ? InferenceEffort.valueOf(args[optionIndex]) : InferenceEffort.Standard;
        WorkUrgency urgency = args.length > optionIndex + 1
                ? WorkUrgency.valueOf(args[optionIndex + 1]) : WorkUrgency.Normal;
        ExecutionBoundary boundary = args.length > optionIndex + 2
                ? ExecutionBoundary.valueOf(args[optionIndex + 2]) : ExecutionBoundary.LocalOnly;
        List<String> eligibleCapabilityIds = args.length > optionIndex + 3
                ? List.of(args[optionIndex + 3].split(",")) : null;
        return client.submit(new PhysicalInferenceRequest(
                input,
                effort,
                urgency,
                null,
                null,
                boundary,
                eligibleCapabilityIds));
    }

    private static String exactUtf8Bytes(String unit, int byteCount) {
        if (byteCount < 0 || unit.isEmpty()) {
            throw new IllegalArgumentException("invalid generated payload request");
        }
        int unitBytes = unit.getBytes(StandardCharsets.UTF_8).length;
        if (unitBytes == 0 || byteCount % unitBytes != 0) {
            throw new IllegalArgumentException("requested byte count is not divisible by unit UTF-8 width");
        }
        return unit.repeat(byteCount / unitBytes);
    }
}
