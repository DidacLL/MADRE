using System.Diagnostics;
using System.Net.Sockets;
using Madre.Kernel;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static async Task RunAdditionalBoundaryVerificationAsync()
    {
        await UrgencyOnFinalSlotAsync();
        await JavaStalledPeerTimeoutAsync();
        Console.WriteLine("PASS explicit urgency ordering and Java stalled-peer timeout verification");
    }

    private static async Task UrgencyOnFinalSlotAsync()
    {
        using var temp = new TempDir("verify-urgency-final-slot");
        var blockerStarted = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
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
        InferenceCapability cap = Capability(
            "urgency-order", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "urgency.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        string blocker = await engine.SubmitAsync(Req(
            "blocker", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await blockerStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        string background = await engine.SubmitAsync(Req(
            "background", InferenceEffort.Low, WorkUrgency.Background, ExecutionBoundary.LocalOnly));
        string normal = await engine.SubmitAsync(Req(
            "normal", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        string interactive = await engine.SubmitAsync(Req(
            "interactive", InferenceEffort.Low, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        blockerRelease.TrySetResult(BindingExecutionResult.Success("blocker"));

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

    private static async Task JavaStalledPeerTimeoutAsync()
    {
        string socketPath = NewSocketPath();
        using var listener = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
        listener.Bind(new UnixDomainSocketEndPoint(socketPath));
        listener.Listen(1);
        try
        {
            Task<ProcessExit> call = RunJavaToExitAsync(socketPath, "protocol");
            using Socket peer = await listener.AcceptAsync().WaitAsync(TimeSpan.FromSeconds(3));
            ProcessExit exit = await call.WaitAsync(TimeSpan.FromSeconds(8));
            Check(exit.ExitCode != 0
                && exit.Stderr.Contains("timeout", StringComparison.OrdinalIgnoreCase),
                "Java stalled local peer did not terminate through the bounded client timeout");
        }
        finally
        {
            try
            {
                if (File.Exists(socketPath))
                {
                    File.Delete(socketPath);
                }
            }
            catch (IOException)
            {
            }
        }
    }

    private static async Task RunSoakVerificationAsync(VerificationOptions options)
    {
        Console.WriteLine($"SOAK seed={options.Seed} duration={options.SoakDuration}");
        using var temp = new TempDir("verify-soak");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 4);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        var random = new Random(options.Seed);
        var ids = new List<string>();
        Stopwatch duration = Stopwatch.StartNew();
        Stopwatch restart = Stopwatch.StartNew();
        int submitted = 0;
        int restarts = 0;
        int ipcChurn = 0;

        while (duration.Elapsed < options.SoakDuration)
        {
            int action = random.Next(10);
            if (action < 6)
            {
                string input = action switch
                {
                    0 => "FAIL",
                    1 => "SLOW:60",
                    2 => new string('x', 1024),
                    3 => new string('é', 2048),
                    _ => $"soak-{submitted}"
                };
                ids.Add(await SubmitAsync(client, Req(
                    input,
                    InferenceEffort.Standard,
                    (WorkUrgency)random.Next(3),
                    ExecutionBoundary.LocalOnly)));
                submitted++;
            }
            else if (action == 6)
            {
                Check((await client.CallAsync<Health>("Health", null)).Status == "ok", "soak Health failed");
                ipcChurn++;
            }
            else if (action == 7 && ids.Count > 0)
            {
                try
                {
                    _ = await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(ids[random.Next(ids.Count)]));
                }
                catch (InvalidOperationException)
                {
                }
            }
            else
            {
                await WriteProbeStateAsync(env.SlowState, "unavailable");
                _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
                await WriteProbeStateAsync(env.SlowState, "available");
                _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
            }

            if (restart.Elapsed > TimeSpan.FromSeconds(5))
            {
                await kernel.RestartAsync();
                client = new IpcClient(env.Socket);
                restart.Restart();
                restarts++;
            }
            await Task.Delay(5);
        }

        await kernel.RestartAsync();
        client = new IpcClient(env.Socket);
        restarts++;
        await WriteProbeStateAsync(env.SlowState, "available");
        _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);

        int completed = 0;
        foreach (string id in ids)
        {
            WorkInspection work = await WaitIpcTerminalAsync(client, id, 30_000);
            Check(work.State != WorkState.Running
                && work.Attempts.All(attempt => attempt.Outcome != PhysicalAttemptOutcome.Running),
                "soak recovery left durable Running truth");
            if (IsTerminal(work.State))
            {
                completed++;
            }
        }
        await AssertCleanIpcWorkAsync(client, "soak-final");

        SoakAttemptDiagnostics attemptDiagnostics = await ReadSoakAttemptDiagnosticsAsync(env.Database);
        using Process process = Process.GetCurrentProcess();
        int? descendants = OperatingSystem.IsLinux() ? CountLinuxDescendants(Environment.ProcessId) : null;
        if (descendants.HasValue)
        {
            Check(descendants.Value <= 1,
                $"soak leaked child processes; verifier has {descendants.Value} live descendants after recovery");
        }

        Console.WriteLine(
            $"SOAK diagnostics submitted={submitted} completed={completed} restarts={restarts} ipcChurn={ipcChurn} "
            + $"maxObservedExecutionConcurrency={attemptDiagnostics.MaxConcurrency} "
            + $"latencyCount={attemptDiagnostics.Latencies.Count} latencyP50Ms={Percentile(attemptDiagnostics.Latencies, 0.50):F1} "
            + $"latencyP95Ms={Percentile(attemptDiagnostics.Latencies, 0.95):F1} "
            + $"verifierWorkingSetBytes={process.WorkingSet64} verifierHandles={process.HandleCount} "
            + $"descendantProcesses={(descendants?.ToString() ?? "n/a")} dbBytes={new FileInfo(env.Database).Length}");
    }

    private static async Task WriteProbeStateAsync(string path, string value)
    {
        for (int attempt = 0; ; attempt++)
        {
            try
            {
                await File.WriteAllTextAsync(path, value).ConfigureAwait(false);
                return;
            }
            catch (IOException) when (OperatingSystem.IsWindows() && attempt < 100)
            {
                await Task.Delay(10).ConfigureAwait(false);
            }
        }
    }

    private sealed record SoakAttemptDiagnostics(int MaxConcurrency, IReadOnlyList<long> Latencies);

    private static async Task<SoakAttemptDiagnostics> ReadSoakAttemptDiagnosticsAsync(string database)
    {
        SqliteConnection.ClearAllPools();
        await using var connection = new SqliteConnection($"Data Source={database}");
        await connection.OpenAsync();
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            SELECT started_at_ms, ended_at_ms, latency_ms
            FROM attempts
            WHERE ended_at_ms IS NOT NULL
            ORDER BY started_at_ms, attempt_number;
            """;
        var events = new List<(long Time, int Delta)>();
        var latencies = new List<long>();
        await using SqliteDataReader reader = await command.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            long started = reader.GetInt64(0);
            long ended = reader.GetInt64(1);
            events.Add((started, +1));
            events.Add((ended, -1));
            if (!reader.IsDBNull(2))
            {
                latencies.Add(reader.GetInt64(2));
            }
        }

        int active = 0;
        int maximum = 0;
        foreach ((long _, int delta) in events.OrderBy(value => value.Time).ThenBy(value => value.Delta))
        {
            active += delta;
            maximum = Math.Max(maximum, active);
        }
        Check(active == 0, "soak diagnostic attempt intervals did not balance");
        Check(maximum <= 4, $"soak observed physical concurrency {maximum} exceeded maxConcurrent=4");
        latencies.Sort();
        return new SoakAttemptDiagnostics(maximum, latencies);
    }

    private static double Percentile(IReadOnlyList<long> sorted, double fraction)
    {
        if (sorted.Count == 0)
        {
            return 0;
        }
        double index = (sorted.Count - 1) * fraction;
        int lower = (int)Math.Floor(index);
        int upper = (int)Math.Ceiling(index);
        if (lower == upper)
        {
            return sorted[lower];
        }
        double weight = index - lower;
        return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }

    private static int CountLinuxDescendants(int rootPid)
    {
        var parents = new Dictionary<int, int>();
        foreach (string directory in Directory.EnumerateDirectories("/proc"))
        {
            if (!int.TryParse(Path.GetFileName(directory), out int pid))
            {
                continue;
            }
            try
            {
                string stat = File.ReadAllText(Path.Combine(directory, "stat"));
                int closing = stat.LastIndexOf(')');
                if (closing < 0 || closing + 4 >= stat.Length)
                {
                    continue;
                }
                string[] tail = stat[(closing + 2)..].Split(' ', StringSplitOptions.RemoveEmptyEntries);
                if (tail.Length >= 2 && int.TryParse(tail[1], out int parent))
                {
                    parents[pid] = parent;
                }
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
            }
        }

        int descendants = 0;
        foreach (int pid in parents.Keys)
        {
            int cursor = pid;
            var seen = new HashSet<int>();
            while (parents.TryGetValue(cursor, out int parent) && seen.Add(cursor))
            {
                if (parent == rootPid)
                {
                    descendants++;
                    break;
                }
                cursor = parent;
            }
        }
        return descendants;
    }
}
