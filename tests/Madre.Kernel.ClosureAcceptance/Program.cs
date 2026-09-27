using Microsoft.Data.Sqlite;
using Madre.Kernel;

internal static class Program
{
    public static async Task<int> Main()
    {
        await SchedulerDoesNotStarveRunnableWorkAsync();
        await ReleasedMafWorkDeletesCheckpointStateAsync();
        Console.WriteLine("MADRE Lane C closure acceptance passed");
        return 0;
    }

    private static async Task SchedulerDoesNotStarveRunnableWorkAsync()
    {
        using var temp = new TempDirectory("madre-closure-starvation");
        string database = Path.Combine(temp.Path, "kernel.db");

        var waitingCapability = new InferenceCapability(
            "waiting-local",
            "closure/waiting",
            "1",
            new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.LocalOnly, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.Low, FactProvenance.Owner),
            100);
        var runnableCapability = new InferenceCapability(
            "runnable-external",
            "closure/runnable",
            "1",
            new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.ExternalAllowed, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.Low, FactProvenance.Owner),
            1);

        await using var engine = new KernelEngine(
            new WorkStore(database),
            [waitingCapability, runnableCapability],
            [
                new FixedBinding("closure/waiting", CapabilityAvailability.Unavailable, failIfExecuted: true),
                new FixedBinding("closure/runnable", CapabilityAvailability.Available, failIfExecuted: false)
            ],
            maxConcurrent: 1);
        await engine.InitializeAsync();

        var waiting = new List<string>(65);
        for (int i = 0; i < 65; i++)
        {
            waiting.Add(await engine.SubmitAsync(new PhysicalInferenceRequest(
                $"waiting-{i}",
                InferenceEffort.Low,
                WorkUrgency.Normal,
                null,
                null,
                ExecutionBoundary.LocalOnly)));
        }

        string runnable = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "later-runnable",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed));

        engine.Start();
        WorkInspection completed = await WaitForStateAsync(engine, runnable, WorkState.Succeeded);
        Assert(completed.SelectedCapabilityId == "runnable-external" && completed.Attempts.Count == 1,
            "later runnable Work did not execute through its available admissible capability");

        foreach (string id in waiting)
        {
            WorkInspection inspection = await InspectRequiredAsync(engine, id);
            Assert(inspection.State == WorkState.Queued && inspection.Attempts.Count == 0,
                "waiting Work was executed or failed instead of remaining queued for physical availability");
        }

        Console.WriteLine("PASS 65 waiting Works cannot starve a later runnable Work");
    }

    private static async Task ReleasedMafWorkDeletesCheckpointStateAsync()
    {
        using var temp = new TempDirectory("madre-closure-maf-release");
        string database = Path.Combine(temp.Path, "kernel.db");
        var capability = new InferenceCapability(
            "maf-release",
            "closure/maf",
            "1",
            new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.LocalOnly, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.High, FactProvenance.Owner),
            10);

        await using var engine = new KernelEngine(
            new WorkStore(database),
            [capability],
            [new FixedBinding("closure/maf", CapabilityAvailability.Available, failIfExecuted: false)],
            maxConcurrent: 1);
        await engine.InitializeAsync();
        engine.Start();

        string active = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "not-terminal",
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            DateTimeOffset.UtcNow.AddSeconds(5),
            null,
            ExecutionBoundary.LocalOnly));
        Assert(await engine.ReleaseAsync(active) == false, "active queued Work was releasable");
        await engine.CancelAsync(active);

        string workId = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "release-maf",
            InferenceEffort.High,
            WorkUrgency.Background,
            null,
            null,
            ExecutionBoundary.LocalOnly));
        WorkInspection completed = await WaitForStateAsync(engine, workId, WorkState.Succeeded, timeoutMs: 10000);
        Assert(completed.StrategyType == KernelContract.CheckpointedTwoStageStrategyType
               && !string.IsNullOrWhiteSpace(completed.CheckpointId)
               && completed.Attempts.Count == 2,
            "release regression did not execute a real checkpointed two-stage MAF Work");

        string checkpointDirectory = Path.Combine(database + ".maf-checkpoints", workId);
        Assert(Directory.Exists(checkpointDirectory)
               && Directory.EnumerateFileSystemEntries(checkpointDirectory, "*", SearchOption.AllDirectories).Any(),
            "real MAF checkpoint state was not present before explicit release");
        WorkResultSnapshot before = await ResultRequiredAsync(engine, workId);
        Assert(!before.Released && before.Result == "stage:stage:release-maf",
            "terminal MAF result was not retained before release");

        Assert(await engine.ReleaseAsync(workId) == true, "terminal MAF Work release failed");
        WorkInspection after = await InspectRequiredAsync(engine, workId);
        Assert(after.State == WorkState.Succeeded && after.Released && after.Attempts.Count == 2,
            "release removed Work identity/history instead of only retained physical payload");
        WorkResultSnapshot releasedResult = await ResultRequiredAsync(engine, workId);
        Assert(releasedResult.Released && releasedResult.Result is null,
            "released Work still exposes retained result payload");
        await AssertSqlitePayloadReleasedAsync(database, workId);
        Assert(!Directory.Exists(checkpointDirectory),
            "released terminal Work left subordinate MAF checkpoint state behind");

        Assert(await engine.ReleaseAsync(workId) == true, "repeated release was not idempotent");
        Assert(!Directory.Exists(checkpointDirectory),
            "idempotent repeated release recreated or retained subordinate checkpoint state");

        Console.WriteLine("PASS terminal MAF release preserves history and deletes retained SQLite/checkpoint payload");
    }

    private static async Task AssertSqlitePayloadReleasedAsync(string database, string workId)
    {
        await using var connection = new SqliteConnection($"Data Source={database}");
        await connection.OpenAsync();
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = "SELECT prepared_input, result_text, released FROM work WHERE work_id=$id;";
        command.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader reader = await command.ExecuteReaderAsync();
        Assert(await reader.ReadAsync(), "released Work row disappeared from authoritative SQLite history");
        Assert(reader.IsDBNull(0) && reader.IsDBNull(1) && reader.GetInt64(2) == 1,
            "released Work retained SQLite input/result payload");
    }

    private static async Task<WorkInspection> WaitForStateAsync(
        KernelEngine engine,
        string workId,
        WorkState expected,
        int timeoutMs = 5000)
    {
        DateTimeOffset deadline = DateTimeOffset.UtcNow.AddMilliseconds(timeoutMs);
        WorkInspection? latest = null;
        while (DateTimeOffset.UtcNow < deadline)
        {
            latest = await engine.InspectAsync(workId);
            if (latest?.State == expected)
            {
                return latest;
            }
            await Task.Delay(20);
        }
        throw new InvalidOperationException($"Work {workId} did not reach {expected}; latest={latest?.State}, failure={latest?.FailureCode}");
    }

    private static async Task<WorkInspection> InspectRequiredAsync(KernelEngine engine, string workId) =>
        await engine.InspectAsync(workId) ?? throw new InvalidOperationException($"Work {workId} missing");

    private static async Task<WorkResultSnapshot> ResultRequiredAsync(KernelEngine engine, string workId) =>
        await engine.ResultAsync(workId) ?? throw new InvalidOperationException($"Result for {workId} missing");

    private static void Assert(bool condition, string message)
    {
        if (!condition)
        {
            throw new InvalidOperationException(message);
        }
    }

    private sealed class FixedBinding(
        string bindingId,
        CapabilityAvailability availability,
        bool failIfExecuted) : IInferenceBinding
    {
        public string BindingId => bindingId;
        public string BindingVersion => "1";

        public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken) =>
            Task.FromResult(availability);

        public Task<BindingExecutionResult> ExecuteAsync(
            PhysicalInferenceRequest request,
            CancellationToken cancellationToken)
        {
            if (failIfExecuted)
            {
                throw new InvalidOperationException("unavailable waiting binding was executed");
            }
            return Task.FromResult(BindingExecutionResult.Success("stage:" + request.PreparedInput));
        }
    }

    private sealed class TempDirectory : IDisposable
    {
        public TempDirectory(string prefix)
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), $"{prefix}-{Guid.NewGuid():N}");
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose()
        {
            SqliteConnection.ClearAllPools();
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
