package io.github.didacll.madre.kernel.client;

import java.net.URI;
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
        LocalKernelClient client = new LocalKernelClient(Path.of(args[1]));
        switch (args[0]) {
            case "submit-process" -> submitProcess(client, args);
            case "submit-http" -> submitHttp(client, args);
            case "submit-two-http" -> submitTwoHttp(client, args);
            case "submit-mixed" -> submitMixed(client, args);
            case "inspect" -> inspect(client, args);
            case "result" -> result(client, args);
            case "cancel" -> cancel(client, args);
            case "release" -> release(client, args);
            default -> throw new IllegalArgumentException("unknown command: " + args[0]);
        }
    }

    private static void submitProcess(LocalKernelClient client, String[] args) {
        if (args.length < 12) throw new IllegalArgumentException("submit-process endpoint id executable body eligible deadline timeout safety maxAttempts retryDelay target [process args...]");
        List<String> processArgs = new ArrayList<>();
        for (int i = 12; i < args.length; ++i) processArgs.add(args[i]);
        ProcessInvocation invocation = new ProcessInvocation(new InvocationId(args[2]), args[3], processArgs,
                args[4].getBytes(StandardCharsets.UTF_8), optionalText(args[11]));
        System.out.println(client.submit(new WorkRequest(List.of(invocation), Urgency.NORMAL, optionalLong(args[5]), optionalLong(args[6]), optionalLong(args[7]), retry(args[8], args[9], args[10]))).value());
    }

    private static void submitHttp(LocalKernelClient client, String[] args) {
        if (args.length < 12) throw new IllegalArgumentException("submit-http endpoint id uri body eligible deadline timeout safety maxAttempts retryDelay target [header flags]");
        List<HttpHeader> headers = new ArrayList<>();
        for (int i = 12; i < args.length;) {
            if ("--literal".equals(args[i]) && i + 2 < args.length) {
                headers.add(new LiteralHttpHeader(args[i + 1], args[i + 2])); i += 3;
            } else if ("--env".equals(args[i]) && i + 4 < args.length) {
                headers.add(new EnvironmentHttpHeader(args[i + 1], args[i + 2], args[i + 3], args[i + 4])); i += 5;
            } else {
                throw new IllegalArgumentException("invalid HTTP header flag sequence");
            }
        }
        HttpInvocation invocation = new HttpInvocation(new InvocationId(args[2]), URI.create(args[3]), headers,
                args[4].getBytes(StandardCharsets.UTF_8), optionalText(args[11]));
        System.out.println(client.submit(new WorkRequest(List.of(invocation), Urgency.NORMAL, optionalLong(args[5]), optionalLong(args[6]), optionalLong(args[7]), retry(args[8], args[9], args[10]))).value());
    }

    private static void submitTwoHttp(LocalKernelClient client, String[] args) {
        if (args.length != 13) throw new IllegalArgumentException("submit-two-http endpoint id1 uri1 body1 env1 id2 uri2 body2 env2 safety attempts timeout");
        List<HttpHeader> firstHeaders = "-".equals(args[5]) ? List.of() : List.of(new EnvironmentHttpHeader("Authorization", args[5], "Bearer ", ""));
        List<HttpHeader> secondHeaders = "-".equals(args[9]) ? List.of() : List.of(new EnvironmentHttpHeader("Authorization", args[9], "Bearer ", ""));
        HttpInvocation first = new HttpInvocation(new InvocationId(args[2]), URI.create(args[3]), firstHeaders, args[4].getBytes(StandardCharsets.UTF_8), Optional.of("first-target"));
        HttpInvocation second = new HttpInvocation(new InvocationId(args[6]), URI.create(args[7]), secondHeaders, args[8].getBytes(StandardCharsets.UTF_8), Optional.of("second-target"));
        System.out.println(client.submit(new WorkRequest(List.of(first, second), Urgency.NORMAL, OptionalLong.empty(), OptionalLong.empty(), optionalLong(args[12]), retry(args[10], args[11], "0"))).value());
    }

    private static void submitMixed(LocalKernelClient client, String[] args) {
        if (args.length != 8) throw new IllegalArgumentException("submit-mixed endpoint processId processExe processBody httpId uri httpBody");
        ProcessInvocation process = new ProcessInvocation(new InvocationId(args[2]), args[3], List.of(), args[4].getBytes(StandardCharsets.UTF_8), Optional.of("process-target"));
        HttpInvocation http = new HttpInvocation(new InvocationId(args[5]), URI.create(args[6]), List.of(), args[7].getBytes(StandardCharsets.UTF_8), Optional.of("http-target"));
        System.out.println(client.submit(new WorkRequest(List.of(process, http))).value());
    }

    private static void inspect(LocalKernelClient client, String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("inspect endpoint workId");
        WorkInspection value = client.inspect(new WorkId(args[2]));
        AttemptInspection attempt = value.latestAttempt().orElse(null);
        System.out.println(String.join("|",
                value.status().name(),
                Integer.toString(value.attemptCount()),
                Boolean.toString(value.payloadReleased()),
                attempt == null ? "" : Integer.toString(attempt.attemptNumber()),
                attempt == null ? "" : attempt.invocationId().value(),
                attempt == null ? "" : attempt.invocationKind().name(),
                attempt == null ? "" : attempt.targetIdentity().orElse(""),
                attempt == null ? "" : attempt.state().name(),
                attempt == null ? "" : Long.toString(attempt.startedAtMs()),
                attempt == null ? "" : (attempt.endedAtMs().isPresent() ? Long.toString(attempt.endedAtMs().getAsLong()) : ""),
                attempt == null ? "" : (attempt.processExitCode().isPresent() ? Integer.toString(attempt.processExitCode().getAsInt()) : ""),
                attempt == null ? "" : (attempt.httpStatus().isPresent() ? Integer.toString(attempt.httpStatus().getAsInt()) : ""),
                attempt == null ? "" : attempt.technicalFailure().orElse(""),
                value.technicalFailure().orElse("")));
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

    private static void release(LocalKernelClient client, String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("release endpoint workId");
        client.release(new WorkId(args[2]));
        System.out.println("released");
    }

    private static RetryPolicy retry(String safety, String attempts, String delay) {
        return new RetryPolicy(Integer.parseInt(attempts), Long.parseLong(delay), RetrySafety.valueOf(safety));
    }
    private static OptionalLong optionalLong(String value) { return "-".equals(value) ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value)); }
    private static Optional<String> optionalText(String value) { return "-".equals(value) ? Optional.empty() : Optional.of(value); }
}
