using Madre.Kernel;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static async Task PersistenceAndSchedulingBehaviorAsync()
    {
        using var temp = new TempDir("madre-schema-shape");
        string incompatible = Path.Combine(temp.Path, "pre-release.db");
        await using (var db = new SqliteConnection($"Data Source={incompatible}"))
        {
            await db.OpenAsync();
            await using SqliteCommand command = db.CreateCommand();
            command.CommandText = "CREATE TABLE work (legacy TEXT);";
            await command.ExecuteNonQueryAsync();
        }

        bool rejected = false;
        try
        {
            await new WorkStore(incompatible).InitializeAsync([], DateTimeOffset.UtcNow);
        }
        catch (InvalidDataException ex) when (ex.Message.Contains("incompatible pre-release", StringComparison.OrdinalIgnoreCase))
        {
            rejected = true;
        }
        Check(rejected, "unversioned incompatible pre-release database was treated as current schema");

        string current = Path.Combine(temp.Path, "current.db");
        await new WorkStore(current).InitializeAsync([], DateTimeOffset.UtcNow);
        await using (var db = new SqliteConnection($"Data Source={current}"))
        {
            await db.OpenAsync();
            await using SqliteCommand command = db.CreateCommand();
            command.CommandText = "PRAGMA user_version;";
            Check(Convert.ToInt32(await command.ExecuteScalarAsync()) == 2,
                "current database did not persist explicit schema identity");
        }

        await EligibilityBoundaryUsesSingleSnapshotAsync(temp.Path);
        Console.WriteLine("PASS explicit schema identity and deterministic eligibility boundary");
    }

    private static async Task EligibilityBoundaryUsesSingleSnapshotAsync(string directory)
    {
        var clock = new EligibilityBoundaryRaceClock();
        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(directory, "eligibility-boundary.db")),
            [],
            [],
            1,
            clock);
        await engine.InitializeAsync();
        engine.Start();

        string prime = await engine.SubmitAsync(Req(
            "prime-scheduler",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        WorkInspection primeDone = await WaitStateAsync(engine, prime, WorkState.Failed);
        Check(primeDone.Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability,
            "scheduler prime did not reach deterministic terminal state");

        string sentinel = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "sentinel",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            clock.BaseTime.AddDays(1),
            null,
            ExecutionBoundary.LocalOnly));
        await clock.WaitForBoundaryDelayAsync().WaitAsync(TimeSpan.FromSeconds(2));
        Check((await engine.InspectAsync(sentinel))?.State == WorkState.Queued,
            "future sentinel did not establish the scheduler boundary wait");

        clock.ArmEligibilityCrossing();
        string raced = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "eligibility-crossing",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            clock.BaseTime.AddMilliseconds(100),
            null,
            ExecutionBoundary.LocalOnly));
        WorkInspection racedDone = await WaitStateAsync(engine, raced, WorkState.Failed, 2000);
        Check(racedDone.Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability
            && racedDone.Attempts.Count == 0,
            "eligibility boundary crossing stranded queued Work");
    }

    private sealed class EligibilityBoundaryRaceClock : IKernelClock
    {
        private readonly TaskCompletionSource<bool> _boundaryDelayEntered = new(
            TaskCreationOptions.RunContinuationsAsynchronously);
        private int _armed;
        private int _armedReads;

        public DateTimeOffset BaseTime { get; } = new(2030, 1, 1, 0, 0, 0, TimeSpan.Zero);

        public DateTimeOffset UtcNow
        {
            get
            {
                if (Volatile.Read(ref _armed) == 0)
                {
                    return BaseTime;
                }

                int read = Interlocked.Increment(ref _armedReads);
                return read <= 2 ? BaseTime : BaseTime.AddMilliseconds(200);
            }
        }

        public Task DelayAsync(TimeSpan delay, CancellationToken cancellationToken)
        {
            _boundaryDelayEntered.TrySetResult(true);
            return Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
        }

        public Task WaitForBoundaryDelayAsync() => _boundaryDelayEntered.Task;

        public void ArmEligibilityCrossing()
        {
            Interlocked.Exchange(ref _armedReads, 0);
            Volatile.Write(ref _armed, 1);
        }
    }
}
