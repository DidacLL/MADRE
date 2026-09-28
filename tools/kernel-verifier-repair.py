from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    p.write_text(text.replace(old, new))


replace_once(
    "tests/Madre.Kernel.Verification/AdditionalVerification.cs",
    '''        var blockerStarted = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var blockerRelease = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var binding = new ControlledBinding("controlled/urgency-order", "1")
        {
            ExecuteHandler = (request, _) =>
            {
                if (request.PreparedInput == "blocker")
                {
                    blockerStarted.TrySetResult(true);
                    return blockerRelease.Task;
                }
                return Task.FromResult(BindingExecutionResult.Success(request.PreparedInput));
            }
        };
''',
    '''        var blockerStarted = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var blockerRelease = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var interactiveRelease = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var normalRelease = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var backgroundRelease = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        using var candidateStarted = new SemaphoreSlim(0, 3);
        var binding = new ControlledBinding("controlled/urgency-order", "1")
        {
            ExecuteHandler = (request, _) => request.PreparedInput switch
            {
                "blocker" => SignalAndWait(blockerStarted, blockerRelease),
                "interactive" => SignalAndWait(candidateStarted, interactiveRelease),
                "normal" => SignalAndWait(candidateStarted, normalRelease),
                "background" => SignalAndWait(candidateStarted, backgroundRelease),
                _ => throw new InvalidOperationException("unexpected urgency fixture input")
            }
        };
''',
    "urgency fixture gates",
)

replace_once(
    "tests/Madre.Kernel.Verification/AdditionalVerification.cs",
    '''        blockerRelease.TrySetResult(BindingExecutionResult.Success("blocker"));
        await WaitAllTerminalAsync(engine, [blocker, background, normal, interactive]);

        string[] order = binding.Invocations.ToArray();
        Check(order.Length == 4
            && order[0] == "blocker"
            && order[1] == "interactive"
            && order[2] == "normal"
            && order[3] == "background",
            "urgency priority was not applied when the final physical slot became available");
    }
''',
    '''        blockerRelease.TrySetResult(BindingExecutionResult.Success("blocker"));

        await candidateStarted.WaitAsync(TimeSpan.FromSeconds(5));
        string[] first = binding.Invocations.ToArray();
        Check(first.Length == 2 && first[0] == "blocker" && first[1] == "interactive",
            "Interactive Work was not the first dispatch after the final physical slot became available");
        interactiveRelease.TrySetResult(BindingExecutionResult.Success("interactive"));

        await candidateStarted.WaitAsync(TimeSpan.FromSeconds(5));
        string[] second = binding.Invocations.ToArray();
        Check(second.Length == 3 && second[2] == "normal",
            "Normal Work was not dispatched before Background Work");
        normalRelease.TrySetResult(BindingExecutionResult.Success("normal"));

        await candidateStarted.WaitAsync(TimeSpan.FromSeconds(5));
        string[] third = binding.Invocations.ToArray();
        Check(third.Length == 4 && third[3] == "background",
            "Background Work was not the final urgency dispatch");
        backgroundRelease.TrySetResult(BindingExecutionResult.Success("background"));

        await WaitAllTerminalAsync(engine, [blocker, background, normal, interactive]);
    }

    private static Task<BindingExecutionResult> SignalAndWait(
        TaskCompletionSource<bool> started,
        TaskCompletionSource<BindingExecutionResult> release)
    {
        started.TrySetResult(true);
        return release.Task;
    }

    private static Task<BindingExecutionResult> SignalAndWait(
        SemaphoreSlim started,
        TaskCompletionSource<BindingExecutionResult> release)
    {
        started.Release();
        return release.Task;
    }
''',
    "staged urgency assertions",
)

replace_once(
    "madre-kernel-client/src/test/java/io/github/didacll/madre/kernel/client/KernelClientProcess.java",
    '''                case "result" -> System.out.println(json.writeValueAsString(client.result(new WorkId(args[2]))));
                case "cancel" -> System.out.println(client.cancel(new WorkId(args[2])));
''',
    '''                case "result" -> System.out.println(json.writeValueAsString(client.result(new WorkId(args[2]))));
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
''',
    "Java result value assertion helper",
)

replace_once(
    "tests/Madre.Kernel.Verification/IntegrationVerification.cs",
    '''        string id = (await JavaAsync(env.Socket, "submit", "java-á😀𐐷", "Standard", "Normal", "LocalOnly")).Trim(); await WaitStateAsync(client, id, WorkState.Succeeded); Check((await JavaAsync(env.Socket, "result", id)).Contains("java-á😀𐐷", StringComparison.Ordinal), "Java Unicode roundtrip failed");
''',
    '''        string id = (await JavaAsync(env.Socket, "submit", "java-á😀𐐷", "Standard", "Normal", "LocalOnly")).Trim(); await WaitStateAsync(client, id, WorkState.Succeeded); Check((await JavaAsync(env.Socket, "result-equals", id, "slow:java-á😀𐐷")).Trim() == "true", "Java Unicode roundtrip failed");
''',
    "Java Unicode logical value assertion",
)
