package io.github.didacll.madre.kernel.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Narrow process harness for LCR3 scheduler acceptance. */
public final class Lcr3KernelClientProcess {
    private Lcr3KernelClientProcess() {}

    public static void main(String[] args) {
        if (args.length != 7 || !"submit-urgency".equals(args[0])) {
            throw new IllegalArgumentException(
                    "submit-urgency endpoint urgency id executable body delayMs");
        }
        LocalKernelClient client = new LocalKernelClient(Path.of(args[1]));
        ProcessInvocation invocation = new ProcessInvocation(
                new InvocationId(args[3]),
                args[4],
                List.of("--delay-ms", args[6]),
                args[5].getBytes(StandardCharsets.UTF_8),
                Optional.empty());
        WorkRequest request = new WorkRequest(
                List.of(invocation),
                Urgency.valueOf(args[2]),
                java.util.OptionalLong.empty(),
                java.util.OptionalLong.empty(),
                java.util.OptionalLong.empty(),
                new RetryPolicy(1, 0, RetrySafety.NEVER));
        System.out.println(client.submit(request).value());
    }
}
