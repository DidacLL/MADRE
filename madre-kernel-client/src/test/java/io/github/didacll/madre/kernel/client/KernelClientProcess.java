package io.github.didacll.madre.kernel.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public final class KernelClientProcess {
    private KernelClientProcess() {}

    public static void main(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("command and endpoint are required");
        String command = args[0];
        LocalKernelClient client = new LocalKernelClient(Path.of(args[1]));
        switch (command) {
            case "submit" -> submit(client, args);
            case "submit-two" -> submitTwo(client, args);
            case "inspect" -> inspect(client, args);
            case "result" -> result(client, args);
            case "cancel" -> cancel(client, args);
            default -> throw new IllegalArgumentException("unknown command: " + command);
        }
    }

    private static void submit(LocalKernelClient client, String[] args) {
        if (args.length < 12) {
            throw new IllegalArgumentException("submit endpoint candidate executable input eligible deadline timeout safety maxAttempts retryDelay target [process args...]");
        }
        List<String> processArgs = new ArrayList<>();
        for (int i = 12; i < args.length; ++i) processArgs.add(args[i]);
        ProcessInvocation invocation = new ProcessInvocation(
                new InvocationId(args[2]),
                args[3],
                processArgs,
                "-".equals(args[11]) ? Optional.empty() : Optional.of(args[11]));
        WorkRequest request = new WorkRequest(
                List.of(invocation),
                args[4].getBytes(StandardCharsets.UTF_8),
                Urgency.NORMAL,
                optionalLong(args[5]),
                optionalLong(args[6]),
                optionalLong(args[7]),
                new RetryPolicy(Integer.parseInt(args[9]), Long.parseLong(args[10]), RetrySafety.valueOf(args[8])));
        System.out.println(client.submit(request).value());
    }

    private static void submitTwo(LocalKernelClient client, String[] args) {
        if (args.length != 7) {
            throw new IllegalArgumentException("submit-two endpoint id1 exe1 id2 exe2 input");
        }
        WorkRequest request = new WorkRequest(
                List.of(
                        new ProcessInvocation(new InvocationId(args[2]), args[3], List.of()),
                        new ProcessInvocation(new InvocationId(args[4]), args[5], List.of())),
                args[6].getBytes(StandardCharsets.UTF_8));
        System.out.println(client.submit(request).value());
    }

    private static void inspect(LocalKernelClient client, String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("inspect endpoint workId");
        WorkInspection value = client.inspect(new WorkId(args[2]));
        System.out.println(value.status() + "|" + value.attemptCount() + "|" +
                value.selectedInvocation().map(InvocationId::value).orElse("") + "|" +
                value.selectedTargetIdentity().orElse("") + "|" + value.technicalFailure().orElse(""));
    }

    private static void result(LocalKernelClient client, String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("result endpoint workId");
        Optional<WorkResult> value = client.result(new WorkId(args[2]));
        System.out.println(value.map(result -> new String(result.payload(), StandardCharsets.UTF_8)).orElse("<none>"));
    }

    private static void cancel(LocalKernelClient client, String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("cancel endpoint workId");
        System.out.println(client.cancel(new WorkId(args[2])));
    }

    private static OptionalLong optionalLong(String value) {
        return "-".equals(value) ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
    }
}
