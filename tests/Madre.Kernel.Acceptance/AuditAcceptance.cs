using System.Diagnostics;
using Madre.Kernel;

internal static partial class Program
{
    private static async Task AuditBlockersAsync()
    {
        await SingleInstanceSafetyAsync();
        await BackgroundFailureTruthAsync();
        await ObservationDemandAndProvenanceAsync();
        await PhysicalBoundaryContractAsync();
        await StrictBoundaryAsync();
        await IpcBoundednessAndUtf8Async();
        await ProcessBindingSupervisionAsync();
        await PersistenceAndSchedulingBehaviorAsync();
    }

    private static async Task ProcessBindingSupervisionAsync()
    {
        using var temp = new TempDir("madre-process-output");
        const string capabilityId = "bounded-process";
        const string bindingId = "process/bounded";
        var capability = Cap(
            capabilityId,
            bindingId,
            InferenceEffort.Standard,
            ExecutionBoundary.LocalOnly,
            1);
        var binding = new ProcessInferenceBinding(
            bindingId,
            "1",
            Dotnet,
            [FixtureDll]);

        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "process-output.db")),
            [capability],
            [binding],
            1);
        await engine.InitializeAsync();
        engine.Start();

        int floodBytes = KernelProtocol.MaxPayloadBytes + (8 * 1024 * 1024);
        string overflowId = await engine.SubmitAsync(Req(
            $"FLOOD_STDOUT:{floodBytes}",
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        WorkInspection overflow = await WaitPhysicalTerminalAsync(engine, overflowId, 7000);
        AssertContainedReaderFailure(
            overflow,
            PhysicalFailureKind.PayloadLimitExceeded,
            "stdout overflow");

        string recovered = await engine.SubmitAsync(Req(
            "after-overflow",
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        WorkInspection recoveredWork = await WaitStateAsync(engine, recovered, WorkState.Succeeded, 5000);
        Check(recoveredWork.Attempts.Count == 1, "physical slot was not recovered after stdout overflow");
        Check((await engine.ResultAsync(recovered))?.Result == "after-overflow", "Kernel failed ordinary Work after stdout overflow");

        string invalidId = await engine.SubmitAsync(Req(
            $"INVALID_UTF8_STDERR:{floodBytes}",
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        WorkInspection invalid = await WaitPhysicalTerminalAsync(engine, invalidId, 7000);
        AssertContainedReaderFailure(
            invalid,
            PhysicalFailureKind.IoFailure,
            "stderr reader failure");

        string final = await engine.SubmitAsync(Req(
            "after-reader-failure",
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        Check((await WaitStateAsync(engine, final, WorkState.Succeeded, 5000)).Attempts.Count == 1,
            "physical slot was not recovered after stderr reader failure");
        Console.WriteLine("PASS bounded process output supervision and maxConcurrent=1 slot recovery");
    }

    private static async Task<WorkInspection> WaitPhysicalTerminalAsync(
        KernelEngine engine,
        string workId,
        int timeoutMs)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        WorkInspection? latest = null;
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            latest = await engine.InspectAsync(workId);
            if (latest?.State is WorkState.Failed or WorkState.UnknownCompletion)
            {
                return latest;
            }
            await Task.Delay(20);
        }
        throw new InvalidOperationException(
            $"Work {workId} did not leave physical execution; latest={latest?.State}, failure={latest?.Failure}");
    }

    private static void AssertContainedReaderFailure(
        WorkInspection work,
        PhysicalFailureKind definiteFailure,
        string scenario)
    {
        if (work.State == WorkState.UnknownCompletion)
        {
            Check(work.Failure?.Kind == PhysicalFailureKind.CompletionUnknown,
                $"{scenario} uncertainty was not represented as UnknownCompletion");
            return;
        }

        Check(work.State == WorkState.Failed && work.Failure?.Kind == definiteFailure,
            $"{scenario} did not preserve definite failure after confirmed physical termination");
    }
}
