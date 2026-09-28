using System.Diagnostics;
using Madre.Kernel;

internal static partial class Program
{
    private sealed class SwitchingProcessBinding(
        string bindingId,
        string bindingVersion,
        ProcessInferenceBinding ordinary,
        IReadOnlyDictionary<string, ProcessInferenceBinding> special) : IInferenceBinding
    {
        public string BindingId => bindingId;
        public string BindingVersion => bindingVersion;

        public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken) =>
            Task.FromResult(CapabilityAvailability.Available);

        public Task<BindingExecutionResult> ExecuteAsync(
            InferenceExecutionRequest request,
            CancellationToken cancellationToken)
        {
            foreach ((string prefix, ProcessInferenceBinding binding) in special)
            {
                if (request.PreparedInput.StartsWith(prefix, StringComparison.Ordinal))
                {
                    return binding.ExecuteAsync(request, cancellationToken);
                }
            }
            return ordinary.ExecuteAsync(request, cancellationToken);
        }
    }

    private static async Task ProcessBindingIntegrationVerificationAsync()
    {
        using var temp = new TempDir("madre-process-integration");
        string childMarker = Path.Combine(temp.Path, "child pid.txt");
        var ordinary = new ProcessInferenceBinding("process/inner-ordinary", "1", Dotnet, [FixtureDll]);
        var noRead = new ProcessInferenceBinding(
            "process/inner-no-read", "1", Dotnet,
            [FixtureDll, "--mode", "no-read-stdin", "--delay-ms", "30000"]);
        var closeStdin = new ProcessInferenceBinding(
            "process/inner-close-stdin", "1", Dotnet,
            [FixtureDll, "--mode", "close-stdin", "--delay-ms", "5000"]);
        var exitWithoutRead = new ProcessInferenceBinding(
            "process/inner-exit-write", "1", Dotnet,
            [FixtureDll, "--mode", "exit-without-reading", "--exit-code", "23"]);
        var processTree = new ProcessInferenceBinding(
            "process/inner-tree", "1", Dotnet,
            [FixtureDll, "--mode", "spawn-child", "--marker", childMarker, "--delay-ms", "30000"]);
        var missing = new ProcessInferenceBinding(
            "process/inner-missing", "1", Path.Combine(temp.Path, "does-not-exist-executable"));
        var unicodeArguments = new ProcessInferenceBinding(
            "process/inner-unicode", "1", Dotnet,
            [FixtureDll, "--prefix", "pré fix 😀 : "]);

        var switching = new SwitchingProcessBinding(
            "process/verification",
            "1",
            ordinary,
            new Dictionary<string, ProcessInferenceBinding>(StringComparer.Ordinal)
            {
                ["NO_READ_STDIN:"] = noRead,
                ["CLOSE_STDIN:"] = closeStdin,
                ["EXIT_WHILE_WRITING:"] = exitWithoutRead,
                ["SPAWN_TREE:"] = processTree,
                ["MISSING_EXECUTABLE:"] = missing,
                ["UNICODE_ARGS:"] = unicodeArguments
            });
        InferenceCapability cap = Capability(
            "process-verification", switching.BindingId, "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "process.db")), [cap], [switching], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        string unicode = "line one\nembedded\0control\táéí 😀 𐐷";
        await AssertProcessCaseAsync(engine, unicode, WorkState.Succeeded, null, expectedResult: unicode);

        string exactInput = new('i', KernelProtocol.MaxPayloadBytes);
        await AssertProcessCaseAsync(engine, exactInput, WorkState.Succeeded, null, expectedResult: exactInput, timeoutMs: 20_000);
        await AssertProcessCaseAsync(
            engine,
            $"FLOOD_STDOUT:{KernelProtocol.MaxPayloadBytes}",
            WorkState.Succeeded,
            null,
            expectedResultLength: KernelProtocol.MaxPayloadBytes,
            timeoutMs: 15_000);
        await AssertProcessContainedFailureAsync(
            engine,
            $"FLOOD_STDOUT:{KernelProtocol.MaxPayloadBytes + 1}",
            PhysicalFailureKind.PayloadLimitExceeded,
            "stdout overflow");
        await AssertProcessContainedFailureAsync(
            engine,
            $"FLOOD_STDERR:{KernelProtocol.MaxPayloadBytes + 1}",
            PhysicalFailureKind.PayloadLimitExceeded,
            "stderr overflow");
        await AssertProcessContainedFailureAsync(
            engine,
            $"FLOOD_BOTH:{KernelProtocol.MaxPayloadBytes + 1}",
            PhysicalFailureKind.PayloadLimitExceeded,
            "simultaneous stdout/stderr overflow");
        await AssertProcessContainedFailureAsync(engine, "INVALID_UTF8_STDOUT:32", PhysicalFailureKind.IoFailure, "invalid UTF-8 stdout");
        await AssertProcessContainedFailureAsync(engine, "INVALID_UTF8_STDERR:32", PhysicalFailureKind.IoFailure, "invalid UTF-8 stderr");

        await AssertProcessCaseAsync(engine, "EXIT:17", WorkState.Failed, PhysicalFailureKind.ProcessExited);
        await AssertProcessCaseAsync(engine, "EXIT:19|explicit stderr failure", WorkState.Failed, PhysicalFailureKind.ProcessExited);
        await AssertProcessCaseAsync(engine, "CLOSE_STDOUT", WorkState.Succeeded, null, expectedResult: string.Empty);
        await AssertProcessCaseAsync(engine, "CLOSE_STDERR", WorkState.Succeeded, null, expectedResult: "CLOSE_STDERR");
        await AssertProcessCaseAsync(
            engine,
            "UNICODE_ARGS:payload",
            WorkState.Succeeded,
            null,
            expectedResult: "pré fix 😀 : UNICODE_ARGS:payload");
        await AssertProcessCaseAsync(engine, "MISSING_EXECUTABLE:any", WorkState.Failed, PhysicalFailureKind.LaunchFailed);

        string closeInput = "CLOSE_STDIN:" + new string('c', KernelProtocol.MaxPayloadBytes - "CLOSE_STDIN:".Length);
        await AssertProcessContainedFailureAsync(engine, closeInput, PhysicalFailureKind.IoFailure, "child closing stdin", timeoutMs: 10_000);

        string blockedInput = "NO_READ_STDIN:" + new string('n', KernelProtocol.MaxPayloadBytes - "NO_READ_STDIN:".Length);
        string blocked = await engine.SubmitAsync(Req(blockedInput, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, blocked, WorkState.Running, 8_000);
        _ = await engine.CancelAsync(blocked);
        WorkInspection blockedCancelled = await WaitTerminalAsync(engine, blocked, 10_000);
        Check(blockedCancelled.State is WorkState.Cancelled or WorkState.UnknownCompletion,
            $"cancellation while stdin blocked became {blockedCancelled.State}");
        await ProveSoleSlotRecoveredAsync(engine, "after-blocked-stdin-cancel");

        string exitWrite = "EXIT_WHILE_WRITING:" + new string('w', KernelProtocol.MaxPayloadBytes - "EXIT_WHILE_WRITING:".Length);
        WorkInspection writeRace = await RunProcessTerminalAsync(engine, exitWrite, 10_000);
        Check(writeRace.State is WorkState.Failed or WorkState.UnknownCompletion,
            $"exit while parent wrote input became {writeRace.State}");
        await ProveSoleSlotRecoveredAsync(engine, "after-exit-while-writing");

        string stdoutCancel = await engine.SubmitAsync(Req(
            $"FLOOD_STDOUT:{KernelProtocol.MaxPayloadBytes * 4}",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, stdoutCancel, WorkState.Running);
        _ = await engine.CancelAsync(stdoutCancel);
        WorkInspection stdoutCancelled = await WaitTerminalAsync(engine, stdoutCancel, 10_000);
        Check(stdoutCancelled.State is WorkState.Cancelled or WorkState.Failed or WorkState.UnknownCompletion,
            "stdout flood cancellation did not reach a truthful terminal state");
        await ProveSoleSlotRecoveredAsync(engine, "after-stdout-flood-cancel");

        string stderrCancel = await engine.SubmitAsync(Req(
            $"FLOOD_STDERR:{KernelProtocol.MaxPayloadBytes * 4}",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, stderrCancel, WorkState.Running);
        _ = await engine.CancelAsync(stderrCancel);
        WorkInspection stderrCancelled = await WaitTerminalAsync(engine, stderrCancel, 10_000);
        Check(stderrCancelled.State is WorkState.Cancelled or WorkState.Failed or WorkState.UnknownCompletion,
            "stderr flood cancellation did not reach a truthful terminal state");
        await ProveSoleSlotRecoveredAsync(engine, "after-stderr-flood-cancel");

        string aroundExit = await engine.SubmitAsync(Req("SLOW:25", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, aroundExit, WorkState.Running);
        Task<WorkState?> cancelAroundExit = engine.CancelAsync(aroundExit);
        WorkInspection exitRace = await WaitTerminalAsync(engine, aroundExit, 10_000);
        _ = await cancelAroundExit;
        Check(exitRace.State is WorkState.Succeeded or WorkState.Cancelled or WorkState.Failed,
            $"cancel around process exit produced invalid state {exitRace.State}");
        await ProveSoleSlotRecoveredAsync(engine, "after-exit-cancel-race");

        string tree = await engine.SubmitAsync(Req("SPAWN_TREE:any", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, tree, WorkState.Running);
        await WaitFileAsync(childMarker);
        int childPid = int.Parse((await File.ReadAllTextAsync(childMarker)).Trim());
        _ = await engine.CancelAsync(tree);
        WorkInspection treeCancelled = await WaitTerminalAsync(engine, tree, 10_000);
        Check(treeCancelled.State is WorkState.Cancelled or WorkState.UnknownCompletion,
            $"process-tree cancellation became {treeCancelled.State}");
        await WaitUntilAsync(
            () => !IsProcessAlive(childPid),
            5_000,
            "process-tree cancellation leaked test child process");
        await ProveSoleSlotRecoveredAsync(engine, "after-process-tree-cancel");

        Console.WriteLine("PASS hostile process binding integration, process-tree cancellation and maxConcurrent=1 recovery");
    }

    private static async Task AssertProcessCaseAsync(
        KernelEngine engine,
        string input,
        WorkState expectedState,
        PhysicalFailureKind? expectedFailure,
        string? expectedResult = null,
        int? expectedResultLength = null,
        int timeoutMs = 10_000)
    {
        WorkInspection terminal = await RunProcessTerminalAsync(engine, input, timeoutMs);
        Check(terminal.State == expectedState,
            $"process case {Summarize(input)} became {terminal.State}, expected {expectedState}");
        if (expectedFailure.HasValue)
        {
            Check(terminal.Failure?.Kind == expectedFailure,
                $"process case {Summarize(input)} failure={terminal.Failure?.Kind}, expected={expectedFailure}");
        }
        if (expectedResult is not null || expectedResultLength.HasValue)
        {
            WorkResultSnapshot result = (await engine.ResultAsync(terminal.WorkId))!;
            if (expectedResult is not null)
            {
                Check(result.Result == expectedResult, $"process case {Summarize(input)} lost exact UTF-8 result");
            }
            if (expectedResultLength.HasValue)
            {
                Check(result.Result?.Length == expectedResultLength.Value,
                    $"process output boundary result length {result.Result?.Length} != {expectedResultLength.Value}");
            }
        }
        await ProveSoleSlotRecoveredAsync(engine, "clean-after-" + Guid.NewGuid().ToString("N"));
    }

    private static async Task AssertProcessContainedFailureAsync(
        KernelEngine engine,
        string input,
        PhysicalFailureKind definiteKind,
        string scenario,
        int timeoutMs = 12_000)
    {
        WorkInspection terminal = await RunProcessTerminalAsync(engine, input, timeoutMs);
        if (terminal.State == WorkState.UnknownCompletion)
        {
            Check(terminal.Failure?.Kind == PhysicalFailureKind.CompletionUnknown,
                $"{scenario} uncertainty was not UnknownCompletion");
        }
        else
        {
            Check(terminal.State == WorkState.Failed && terminal.Failure?.Kind == definiteKind,
                $"{scenario} classified {terminal.State}/{terminal.Failure?.Kind}");
        }
        await ProveSoleSlotRecoveredAsync(engine, "clean-after-" + scenario.Replace(' ', '-'));
    }

    private static async Task<WorkInspection> RunProcessTerminalAsync(
        KernelEngine engine,
        string input,
        int timeoutMs)
    {
        string id = await engine.SubmitAsync(Req(input, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        return await WaitTerminalAsync(engine, id, timeoutMs);
    }

    private static bool IsProcessAlive(int pid)
    {
        try
        {
            using Process process = Process.GetProcessById(pid);
            return !process.HasExited;
        }
        catch (ArgumentException)
        {
            return false;
        }
    }

    private static string Summarize(string input) =>
        input.Length <= 80 ? input : input[..80] + $"...({input.Length} chars)";
}
