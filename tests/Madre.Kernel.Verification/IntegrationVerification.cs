using System.Buffers.Binary;
using System.Diagnostics;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using Madre.Kernel;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static async Task RunIntegrationVerificationAsync(VerificationOptions options)
    {
        _ = options;
        await RestartMatrixAsync();
        await SqliteAndOwnershipAsync();
        await RawIpcAndJavaAsync();
        await RequestLocalFailureAsync();
        await ShutdownRecoveryAsync();
        Console.WriteLine("PASS integration/restart/SQLite/ownership/IPC/Java/failure/shutdown verification");
    }

    private static async Task RestartMatrixAsync()
    {
        using var temp = new TempDir("verify-restart");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available"); File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket); await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        string blocker = await SubmitAsync(client, Req("SLOW:3000", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, blocker, WorkState.Running);
        DateTimeOffset now = DateTimeOffset.UtcNow;
        string immediate = await SubmitAsync(client, Req("immediate", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        string future = await SubmitAsync(client, new("future", InferenceEffort.Standard, WorkUrgency.Normal, now.AddMilliseconds(600), null, ExecutionBoundary.LocalOnly));
        string deadline = await SubmitAsync(client, new("deadline", InferenceEffort.Standard, WorkUrgency.Normal, null, now.AddMilliseconds(200), ExecutionBoundary.LocalOnly));
        string cancelled = await SubmitAsync(client, new("cancelled", InferenceEffort.Standard, WorkUrgency.Normal, now.AddMinutes(5), null, ExecutionBoundary.LocalOnly));
        _ = await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(cancelled));
        await kernel.StopAsync(); await Task.Delay(350); await kernel.StartAsync(); client = new IpcClient(env.Socket);
        Check((await WaitStateAsync(client, blocker, WorkState.UnknownCompletion)).Attempts.Single().Outcome == PhysicalAttemptOutcome.UnknownCompletion, "restart did not recover Running as UnknownCompletion");
        Check((await WaitStateAsync(client, immediate, WorkState.Succeeded)).Attempts.Count == 1, "queued immediate Work replay count wrong");
        Check((await WaitStateAsync(client, future, WorkState.Succeeded)).Attempts.Count == 1, "future Work did not wake after restart");
        WorkInspection expired = await WaitStateAsync(client, deadline, WorkState.Failed);
        Check(expired.Failure?.Kind == PhysicalFailureKind.DeadlineExpired && expired.Attempts.Count == 0, "deadline elapsed while down created attempt");
        Check((await InspectAsync(client, cancelled)).State == WorkState.Cancelled, "cancelled state changed on restart");

        string released = await SubmitAsync(client, Req("released", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, released, WorkState.Succeeded); _ = await client.CallAsync<ReleaseReply>("Release", new WorkIdArg(released));
        string marker = Path.Combine(temp.Path, "side-effect.txt");
        string active = await SubmitAsync(client, Req($"SLOW:2500|{marker}", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, active, WorkState.Running); await WaitFileAsync(marker); await kernel.RestartAsync(); client = new IpcClient(env.Socket);
        Check((await WaitStateAsync(client, active, WorkState.UnknownCompletion)).Attempts.Count == 1 && File.ReadAllLines(marker).Length == 1, "uncertain physical execution was duplicated");
        await AssertReleasedAsync(env.Database, released);
        for (int i = 0; i < 3; i++) { await kernel.RestartAsync(); client = new IpcClient(env.Socket); Check((await InspectAsync(client, active)).State == WorkState.UnknownCompletion, "recovery was not idempotent"); }
    }

    private static async Task SqliteAndOwnershipAsync()
    {
        using var temp = new TempDir("verify-sqlite-owner");
        string db = Path.Combine(temp.Path, "unicøde space", "kernel.db");
        await new WorkStore(db).InitializeAsync([], DateTimeOffset.UtcNow);
        await new WorkStore(db).InitializeAsync([], DateTimeOffset.UtcNow);
        foreach ((string name, byte[] bytes) in new[] { ("corrupt.db", Encoding.UTF8.GetBytes("not sqlite")) })
        { string path = Path.Combine(temp.Path, name); await File.WriteAllBytesAsync(path, bytes); await ExpectInitFailure(path); }
        string wrong = Path.Combine(temp.Path, "wrong.db"); await using (var c = new SqliteConnection($"Data Source={wrong}")) { await c.OpenAsync(); await using var q = c.CreateCommand(); q.CommandText = "PRAGMA user_version=99"; await q.ExecuteNonQueryAsync(); } await ExpectInitFailure(wrong);

        string ownerDb = Path.Combine(temp.Path, "owner.db"); string socket = NewSocketPath();
        await using RawHost winner = await RawHost.StartHealthyAsync(null, ownerDb, socket);
        Check((await RunHostToExitAsync(["--db", ownerDb, "--ipc-path", NewSocketPath()])).ExitCode != 0, "same DB admitted second owner");
        string dotted = Path.Combine(temp.Path, ".", "x", "..", "owner.db");
        Check((await RunHostToExitAsync(["--db", dotted, "--ipc-path", NewSocketPath()])).ExitCode != 0, "normalized alias admitted second owner");
        if (OperatingSystem.IsLinux()) { string link = Path.Combine(temp.Path, "owner-link.db"); File.CreateSymbolicLink(link, ownerDb); Check((await RunHostToExitAsync(["--db", link, "--ipc-path", NewSocketPath()])).ExitCode != 0, "symlink alias admitted second physical owner"); }
        var contenders = Enumerable.Range(0, 8).Select(_ => RawHost.StartConfigured(null, ownerDb, NewSocketPath())).ToArray();
        await Task.Delay(500); int alive = 0; foreach (RawHost h in contenders) { try { _ = await h.WaitForExitAsync(100); } catch (TimeoutException) { alive++; } } Check(alive == 0, "losing owners remained alive while established owner held database"); foreach (RawHost h in contenders) await h.DisposeAsync();
        await winner.DisposeAsync(); await using RawHost successor = await RawHost.StartHealthyAsync(null, ownerDb, socket); Check((await new IpcClient(socket).CallAsync<Health>("Health", null)).Status == "ok", "owner successor failed");
    }

    private static async Task ExpectInitFailure(string path)
    { bool failed = false; try { await new WorkStore(path).InitializeAsync([], DateTimeOffset.UtcNow); } catch (Exception ex) when (ex is InvalidDataException or SqliteException) { failed = true; } Check(failed, $"invalid SQLite file accepted: {path}"); }

    private static async Task RawIpcAndJavaAsync()
    {
        using var temp = new TempDir("verify-ipc-java"); TestEnv env = MakeEnv(temp.Path, true, true); File.WriteAllText(env.SlowState, "available"); File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 2); var client = new IpcClient(env.Socket); await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        foreach (int length in new[] { 0, -1, KernelProtocol.MaxFrameBytes + 1 }) _ = await SendRawFrameAsync(env.Socket, length, ReadOnlyMemory<byte>.Empty);
        await SendDrop(env.Socket, [0, 0]); await SendDrop(env.Socket, [0, 0, 0, 10], Encoding.UTF8.GetBytes("{"));
        foreach (byte[] body in new[] { new byte[] { 0xff }, Encoding.UTF8.GetBytes("{}"), Encoding.UTF8.GetBytes("5"), Encoding.UTF8.GetBytes("{") }) _ = await SendRawFrameAsync(env.Socket, body.Length, body);
        string[] invalid = [
            "{\"requestId\":\"x\",\"operation\":\"Health\",\"payload\":null}",
            $"{{\"version\":999,\"requestId\":\"x\",\"operation\":\"Health\",\"payload\":null}}",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\" \" ,\"operation\":\"Health\",\"payload\":null}}",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"x\",\"operation\":2,\"payload\":null}}",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"x\",\"operation\":\"Submit\",\"payload\":{{\"preparedInput\":\"x\",\"requestedEffort\":0,\"urgency\":\"Normal\",\"executionBoundary\":\"LocalOnly\"}}}}" ];
        foreach (string json in invalid) _ = await RawIpcAsync(env.Socket, json);
        for (int i = 0; i < 200; i++) { try { _ = await client.CallAsync<Health>("Health", null); } catch (InvalidOperationException) { } }
        await AssertCleanIpcWorkAsync(client, "after-ipc-hostility");

        using JsonDocument protocol = JsonDocument.Parse(await JavaAsync(env.Socket, "protocol")); Check(protocol.RootElement.GetProperty("version").GetInt32() == KernelProtocol.Version, "Java protocol mismatch");
        string id = (await JavaAsync(env.Socket, "submit", "java-á😀𐐷", "Standard", "Normal", "LocalOnly")).Trim(); await WaitStateAsync(client, id, WorkState.Succeeded); Check((await JavaAsync(env.Socket, "result", id)).Contains("java-á😀𐐷", StringComparison.Ordinal), "Java Unicode roundtrip failed");
        Check((await RunJavaToExitAsync(env.Socket, "submit-generated", KernelProtocol.MaxPayloadBytes.ToString(), "x", "Standard", "Normal", "LocalOnly")).ExitCode == 0, "Java max payload failed");
        Check((await RunJavaToExitAsync(env.Socket, "submit-generated", (KernelProtocol.MaxPayloadBytes + 1).ToString(), "x", "Standard", "Normal", "LocalOnly")).ExitCode != 0, "Java oversized payload accepted");
        Check((await RunJavaToExitAsync(env.Socket, "sequential-protocol", "50")).ExitCode == 0 && (await RunJavaToExitAsync(env.Socket, "parallel-protocol", "16")).ExitCode == 0, "Java sequential/concurrent calls failed");
        await kernel.RestartAsync(); Check((await JavaAsync(env.Socket, "inspect", id)).Contains("Succeeded", StringComparison.Ordinal), "Java reconnect after restart failed");
    }

    private static async Task SendDrop(string socket, byte[] header, byte[]? body = null)
    { using var s = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified); await s.ConnectAsync(new UnixDomainSocketEndPoint(socket)); await s.SendAsync(header, SocketFlags.None); if (body is not null) await s.SendAsync(body, SocketFlags.None); }

    private static async Task RequestLocalFailureAsync()
    {
        using var temp = new TempDir("verify-local-failure"); TestEnv env = MakeEnv(temp.Path, true, true); File.WriteAllText(env.SlowState, "available"); File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1); var client = new IpcClient(env.Socket); await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        await ExecuteSqlAsync(env.Database, "CREATE TRIGGER fail_submit BEFORE INSERT ON work BEGIN SELECT RAISE(ABORT, 'forced'); END;");
        bool failed = false; try { _ = await SubmitAsync(client, Req("fault", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); } catch (InvalidOperationException ex) when (ex.Message.Contains("InternalFailure", StringComparison.Ordinal)) { failed = true; }
        Check(failed && (await client.CallAsync<Health>("Health", null)).Status == "ok", "request-local persistence failure made host falsely unhealthy"); await ExecuteSqlAsync(env.Database, "DROP TRIGGER fail_submit;"); await AssertCleanIpcWorkAsync(client, "after-local-fault");
    }

    private static async Task ShutdownRecoveryAsync()
    {
        using var temp = new TempDir("verify-shutdown");
        string db = Path.Combine(temp.Path, "shutdown.db");
        var allCancelled = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        int cancellationsObserved = 0;
        var binding = new ControlledBinding("controlled/shutdown", "1")
        {
            ExecuteHandler = async (_, cancellationToken) =>
            {
                var cancelled = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                using (cancellationToken.Register(() =>
                {
                    if (Interlocked.Increment(ref cancellationsObserved) == 4)
                    {
                        allCancelled.TrySetResult(true);
                    }
                    cancelled.TrySetResult(true);
                }))
                {
                    await cancelled.Task.ConfigureAwait(false);
                    return BindingExecutionResult.Cancelled("controlled shutdown cancellation");
                }
            }
        };
        InferenceCapability cap = Capability(
            "shutdown", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        var engine = new KernelEngine(new WorkStore(db), [cap], [binding], 4);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();
        var ids = new List<string>();
        for (int i = 0; i < 8; i++)
        {
            ids.Add(await engine.SubmitAsync(Req(
                $"shutdown-{i}", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)));
        }
        await WaitUntilAsync(() => binding.Active == 4, 5_000, "shutdown fixture did not occupy all four physical slots");

        Task shutdown = engine.DisposeAsync().AsTask();
        await allCancelled.Task.WaitAsync(TimeSpan.FromSeconds(3));
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));
        Check(Volatile.Read(ref cancellationsObserved) == 4,
            "Kernel shutdown cancellation did not reach every occupied controlled execution");

        await using var recovered = new KernelEngine(
            new WorkStore(db), [cap], [new ControlledBinding(binding.BindingId, "1")], 1);
        await recovered.InitializeAsync();
        foreach (string id in ids)
        {
            WorkInspection work = (await recovered.InspectAsync(id))!;
            Check(work.State != WorkState.Running
                && work.Attempts.All(attempt => attempt.Outcome != PhysicalAttemptOutcome.Running),
                "shutdown recovery left Running durable truth");
        }
    }
}
