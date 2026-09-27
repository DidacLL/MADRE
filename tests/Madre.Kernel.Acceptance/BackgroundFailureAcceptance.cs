using Madre.Kernel;
using Madre.Kernel.Host;

internal static partial class Program
{
    private static async Task BackgroundFailureTruthAsync()
    {
        await ProbePersistenceFailureTerminatesHostAsync();
        await SchedulerFailureTerminatesHostAsync();
        await AttemptPersistenceFailureTerminatesAndRecoversAsync();
        Console.WriteLine("PASS supervised scheduler/probe/attempt infrastructure failure truth");
    }

    private static async Task ProbePersistenceFailureTerminatesHostAsync()
    {
        using var temp = new TempDir("madre-fatal-probe");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        LoadedKernelConfiguration loaded = KernelConfigurationLoader.Load(env.Config);
        var store = new WorkStore(env.Database);
        await store.InitializeAsync(loaded.Capabilities, DateTimeOffset.UtcNow);
        await ExecuteSqlAsync(env.Database, """
            CREATE TRIGGER fail_capability_observation
            BEFORE UPDATE ON capability_state
            BEGIN
                SELECT RAISE(ABORT, 'forced capability persistence failure');
            END;
            """);

        await using RawHost host = RawHost.StartConfigured(env.Config, env.Database, env.Socket);
        HostExit exit = await host.WaitForExitAsync(7000);
        Check(exit.ExitCode != 0, "probe persistence failure left host alive");
    }

    private static async Task SchedulerFailureTerminatesHostAsync()
    {
        using var temp = new TempDir("madre-fatal-scheduler");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using RawHost host = await RawHost.StartHealthyAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        string blocker = await SubmitAsync(client, Req("SLOW:1200", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, blocker, WorkState.Running);
        await ExecuteSqlAsync(env.Database, """
            CREATE TRIGGER fail_deadline_expiry
            BEFORE UPDATE OF state ON work
            WHEN OLD.state = 'Queued' AND NEW.state = 'Failed'
            BEGIN
                SELECT RAISE(ABORT, 'forced scheduler persistence failure');
            END;
            """);
        _ = await SubmitAsync(client, new PhysicalInferenceRequest(
            "deadline-fatal",
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            null,
            DateTimeOffset.UtcNow.AddMilliseconds(150),
            ExecutionBoundary.LocalOnly));
        HostExit exit = await host.WaitForExitAsync(5000);
        Check(exit.ExitCode != 0, "scheduler persistence failure left host alive and healthy");
    }

    private static async Task AttemptPersistenceFailureTerminatesAndRecoversAsync()
    {
        using var temp = new TempDir("madre-fatal-attempt");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        string workId;

        await using (RawHost host = await RawHost.StartHealthyAsync(env.Config, env.Database, env.Socket, 1))
        {
            var client = new IpcClient(env.Socket);
            await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
            workId = await SubmitAsync(client, Req("SLOW:500", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            await WaitStateAsync(client, workId, WorkState.Running);
            await ExecuteSqlAsync(env.Database, """
                CREATE TRIGGER fail_attempt_completion
                BEFORE UPDATE OF outcome ON attempts
                WHEN OLD.outcome = 'Running' AND NEW.outcome <> 'Running'
                BEGIN
                    SELECT RAISE(ABORT, 'forced attempt persistence failure');
                END;
                """);
            HostExit exit = await host.WaitForExitAsync(5000);
            Check(exit.ExitCode != 0, "detached attempt persistence failure left host alive");
        }

        await ExecuteSqlAsync(env.Database, "DROP TRIGGER fail_attempt_completion;");
        await using RawHost restarted = await RawHost.StartHealthyAsync(env.Config, env.Database, env.Socket, 1);
        WorkInspection recovered = await new IpcClient(env.Socket).CallAsync<WorkInspection>("Inspect", new WorkIdArg(workId));
        Check(recovered.State == WorkState.UnknownCompletion
            && recovered.Attempts.Single().Outcome == PhysicalAttemptOutcome.UnknownCompletion,
            "fatal attempt persistence failure left durable Work indefinitely Running");
    }
}
