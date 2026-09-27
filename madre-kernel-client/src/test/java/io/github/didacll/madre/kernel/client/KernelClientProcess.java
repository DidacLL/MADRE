package io.github.didacll.madre.kernel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Path;

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
                    InferenceEffort effort = args.length > 3 ? InferenceEffort.valueOf(args[3]) : InferenceEffort.Standard;
                    WorkUrgency urgency = args.length > 4 ? WorkUrgency.valueOf(args[4]) : WorkUrgency.Normal;
                    ExecutionBoundary boundary = args.length > 5 ? ExecutionBoundary.valueOf(args[5]) : ExecutionBoundary.LocalOnly;
                    WorkId id = client.submit(new PhysicalInferenceRequest(args[2], effort, urgency, null, null, boundary));
                    System.out.println(id.value());
                }
                case "inspect" -> System.out.println(json.writeValueAsString(client.inspect(new WorkId(args[2]))));
                case "result" -> System.out.println(json.writeValueAsString(client.result(new WorkId(args[2]))));
                case "cancel" -> System.out.println(client.cancel(new WorkId(args[2])));
                case "release" -> System.out.println(client.release(new WorkId(args[2])));
                case "capabilities" -> System.out.println(json.writeValueAsString(client.capabilities()));
                case "refresh" -> System.out.println(client.refreshCapabilities().size());
                default -> throw new IllegalArgumentException("unknown command: " + args[1]);
            }
        }
    }
}
