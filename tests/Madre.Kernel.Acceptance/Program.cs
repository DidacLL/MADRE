using System.Buffers.Binary;
using System.Diagnostics;
using System.Net.Sockets;
using System.Text.Json;
using Madre.Kernel;
using Madre.Kernel.Host;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static async Task<int> Main()
    {
        InitPaths();
        await EmptyKernelAndIpcAsync();
        await CapabilityTruthAsync();
        await LifecycleAndDreAsync();
        await JavaAndRestartAsync();
        await ReconcileAndOpenBindingAsync();
        await NoHeadStarvationAsync();
        await AuditBlockersAsync();
        Console.WriteLine("MADRE Lane C convergence acceptance passed");
        return 0;
    }

    private static async Task EmptyKernelAndIpcAsync()
    {
        using var temp = new TempDir("madre-empty");
        string socket = NewSocketPath();
        await using var kernel = await KernelProcess.StartAsync(null, Path.Combine(temp.Path, "empty.db"), socket);
        var client = new IpcClient(socket);
        KernelProtocolInfo info = await client.CallAsync<KernelProtocolInfo>("ProtocolInfo", null);
        Check(info.Version == KernelProtocol.Version && info.MaxPayloadBytes == KernelProtocol.MaxPayloadBytes && info.MaxFrameBytes == KernelProtocol.MaxFrameBytes, "protocol parity failed");
        Check((await client.CallAsync<List<CapabilitySnapshot>>("Capabilities", null)).Count == 0, "empty Kernel fabricated capabilities");
        using JsonDocument java = JsonDocument.Parse(await JavaAsync(socket, "protocol"));
        Check(java.RootElement.GetProperty("version").GetInt32() == KernelProtocol.Version && java.RootElement.GetProperty("maxPayloadBytes").GetInt32() == KernelProtocol.MaxPayloadBytes && java.RootElement.GetProperty("maxFrameBytes").GetInt32() == KernelProtocol.MaxFrameBytes, "Java/.NET protocol constants diverged");

        bool oversizedRejected = false;
        try
        {
            await client.CallAsync<WorkSubmissionResponse>(
                "Submit",
                Req(new string('x', KernelProtocol.MaxPayloadBytes + 1), InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        }
        catch (InvalidOperationException exception) when (exception.Message.Contains("InvalidRequest", StringComparison.Ordinal))
        {
            oversizedRejected = true;
        }
        Check(oversizedRejected, "prepared input payload bound was not enforced");

        using Socket stalled = new(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
        await stalled.ConnectAsync(new UnixDomainSocketEndPoint(socket));
        byte[] header = new byte[4]; BinaryPrimitives.WriteInt32BigEndian(header, 100);
        await stalled.SendAsync(header, SocketFlags.None); await stalled.SendAsync(new byte[] { (byte)'{' }, SocketFlags.None);
        Stopwatch sw = Stopwatch.StartNew();
        Check((await client.CallAsync<Health>("Health", null)).Status == "ok" && sw.Elapsed < TimeSpan.FromSeconds(1), "stalled caller blocked another caller");
        using (Socket vanished = new(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified))
        {
            await vanished.ConnectAsync(new UnixDomainSocketEndPoint(socket));
            BinaryPrimitives.WriteInt32BigEndian(header, 50); await vanished.SendAsync(header, SocketFlags.None);
        }
        Check((await client.CallAsync<Health>("Health", null)).Status == "ok", "disappearing caller damaged Kernel");

        string host = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel.Host", "Program.cs")) + File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel.Host", "KernelIpcServer.cs"));
        Check(!host.Contains("--port", StringComparison.OrdinalIgnoreCase) && !host.Contains("Kestrel", StringComparison.OrdinalIgnoreCase) && !host.Contains("127.0.0.1", StringComparison.Ordinal), "network web control plane remains");
        Console.WriteLine("PASS empty startup and local framed IPC");
    }

    private static async Task CapabilityTruthAsync()
    {
        using var temp = new TempDir("madre-cap");
        TestEnv env = MakeEnv(temp.Path, slowProbe: true, fastProbe: false);
        File.WriteAllText(env.SlowState, "unavailable"); File.WriteAllText(env.FastState, "available");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Unavailable);
        CapabilitySnapshot unknown = await WaitCapabilityAsync(client, Fast, CapabilityAvailability.Unknown);
        Check(unknown.State.ObservedAt.HasValue, "optional probe did not remain truthfully Unknown");

        string waiting = await SubmitAsync(client, Req("auto", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await Task.Delay(150); Check((await InspectAsync(client, waiting)).State == WorkState.Queued, "unavailable capability dispatched");
        File.WriteAllText(env.SlowState, "available");
        Check((await WaitStateAsync(client, waiting, WorkState.Succeeded, 5000)).SelectedCapabilityId == Slow, "unavailable capability was not automatically re-observed");

        string optional = await SubmitAsync(client, Req("unknown", InferenceEffort.High, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed));
        Check((await WaitStateAsync(client, optional, WorkState.Succeeded)).SelectedCapabilityId == Fast, "Unknown configured capability was unusable");
        Check((await WaitCapabilityAsync(client, Fast, CapabilityAvailability.Available)).State.Availability == CapabilityAvailability.Available, "execution did not provide availability evidence");

        var cap = Cap("slow-probe", "probe/slow", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "probe.db")), [cap], [new SlowProbeBinding()], 1);
        Stopwatch startup = Stopwatch.StartNew(); await engine.InitializeAsync(); engine.Start(); startup.Stop();
        Check(startup.Elapsed < TimeSpan.FromMilliseconds(1500), "startup awaited a slow probe");
        string id = await engine.SubmitAsync(Req("while-probing", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        Check((await WaitStateAsync(engine, id, WorkState.Succeeded)).SelectedCapabilityId == "slow-probe", "truthful Unknown could not execute while probe was slow");
        Console.WriteLine("PASS capability truth and automatic re-observation");
    }

    private static async Task LifecycleAndDreAsync()
    {
        using var temp = new TempDir("madre-life");
        TestEnv env = MakeEnv(temp.Path, true, true); File.WriteAllText(env.SlowState, "available"); File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket); await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available); await WaitCapabilityAsync(client, Fast, CapabilityAvailability.Unavailable);

        string blocker = await SubmitAsync(client, Req("SLOW:600", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); await WaitStateAsync(client, blocker, WorkState.Running);
        string bg = await SubmitAsync(client, Req("bg", InferenceEffort.Standard, WorkUrgency.Background, ExecutionBoundary.LocalOnly));
        string hi = await SubmitAsync(client, Req("hi", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        WorkInspection highDone = await WaitStateAsync(client, hi, WorkState.Succeeded); WorkInspection bgDone = await WaitStateAsync(client, bg, WorkState.Succeeded);
        Check(highDone.Attempts.Single().StartedAt < bgDone.Attempts.Single().StartedAt, "explicit urgency ordering failed");

        DateTimeOffset now = DateTimeOffset.UtcNow;
        string future = await SubmitAsync(client, new PhysicalInferenceRequest("future", InferenceEffort.Standard, WorkUrgency.Normal, now.AddMilliseconds(450), null, ExecutionBoundary.LocalOnly));
        await Task.Delay(150); Check((await InspectAsync(client, future)).State == WorkState.Queued, "eligibility ignored"); await WaitStateAsync(client, future, WorkState.Succeeded);
        string deadlineBlocker = await SubmitAsync(client, Req("SLOW:600", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, deadlineBlocker, WorkState.Running);
        string expired = await SubmitAsync(client, new PhysicalInferenceRequest("expired", InferenceEffort.Standard, WorkUrgency.Normal, null, DateTimeOffset.UtcNow.AddMilliseconds(120), ExecutionBoundary.LocalOnly));
        WorkInspection dead = await WaitStateAsync(client, expired, WorkState.Failed); Check(dead.Failure?.Kind == PhysicalFailureKind.DeadlineExpired && dead.Attempts.Count == 0, "deadline behavior failed");
        await WaitStateAsync(client, deadlineBlocker, WorkState.Succeeded);

        string queued = await SubmitAsync(client, new PhysicalInferenceRequest("cancel", InferenceEffort.Standard, WorkUrgency.Normal, DateTimeOffset.UtcNow.AddSeconds(2), null, ExecutionBoundary.LocalOnly));
        await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(queued)); Check((await WaitStateAsync(client, queued, WorkState.Cancelled)).Attempts.Count == 0, "queued cancellation raced");
        string running = await SubmitAsync(client, Req("SLOW:2000", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); await WaitStateAsync(client, running, WorkState.Running);
        await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(running)); WorkInspection cancelled = await WaitStateAsync(client, running, WorkState.Cancelled); Check(cancelled.Attempts.Single().Outcome == PhysicalAttemptOutcome.ConfirmedCancelled, "running process cancellation was not confirmed");

        string fail = await SubmitAsync(client, Req("FAIL", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); WorkInspection failed = await WaitStateAsync(client, fail, WorkState.Failed);
        Check(failed.Failure?.Kind == PhysicalFailureKind.ProcessExited && failed.Failure?.Detail?.Contains("17", StringComparison.Ordinal) == true, "typed process failure lost detail");

        string retained = await SubmitAsync(client, Req("retain", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); await WaitStateAsync(client, retained, WorkState.Succeeded);
        Check((await client.CallAsync<WorkResultSnapshot>("Result", new WorkIdArg(retained))).Result == "slow:retain", "result not retained");
        Check((await client.CallAsync<ReleaseReply>("Release", new WorkIdArg(retained))).Released, "release failed"); await AssertReleasedAsync(env.Database, retained);

        File.WriteAllText(env.FastState, "available"); await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
        string fastSeed = await SubmitAsync(client, Req("seed", InferenceEffort.High, WorkUrgency.Interactive, ExecutionBoundary.ExternalAllowed)); await WaitStateAsync(client, fastSeed, WorkState.Succeeded);
        string interactive = await SubmitAsync(client, Req("latency", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.ExternalAllowed)); Check((await WaitStateAsync(client, interactive, WorkState.Succeeded)).SelectedCapabilityId == Fast, "observed latency did not affect Interactive DRE");
        string normal = await SubmitAsync(client, Req("owner", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed)); Check((await WaitStateAsync(client, normal, WorkState.Succeeded)).SelectedCapabilityId == Slow, "Owner preference did not affect normal DRE");
        string impossible = await SubmitAsync(client, Req("none", InferenceEffort.High, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); Check((await WaitStateAsync(client, impossible, WorkState.Failed)).Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability, "hard admissibility failed");
        Console.WriteLine("PASS DRE, eligibility/deadline, cancellation, typed failures and release");
    }

    private static async Task JavaAndRestartAsync()
    {
        using var temp = new TempDir("madre-java");
        TestEnv env = MakeEnv(temp.Path, true, true); File.WriteAllText(env.SlowState, "available"); File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket); await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        string id = (await JavaAsync(env.Socket, "submit", "SLOW:250", "Standard", "Normal", "LocalOnly")).Trim();
        await WaitStateAsync(client, id, WorkState.Succeeded);
        Check((await JavaAsync(env.Socket, "inspect", id)).Contains("Succeeded", StringComparison.Ordinal), "Java inspect failed");
        Check((await JavaAsync(env.Socket, "result", id)).Contains("slow:SLOW:250", StringComparison.Ordinal), "Java result failed");
        Check((await JavaAsync(env.Socket, "release", id)).Trim() == "true", "Java release failed");

        string javaCancel = (await JavaAsync(env.Socket, "submit", "SLOW:2000", "Standard", "Normal", "LocalOnly")).Trim();
        await WaitStateAsync(client, javaCancel, WorkState.Running);
        Check((await JavaAsync(env.Socket, "cancel", javaCancel)).Trim() == "Running", "Java cancel did not reach active Work");
        await WaitStateAsync(client, javaCancel, WorkState.Cancelled);

        string queued = await SubmitAsync(client, new PhysicalInferenceRequest("restart-queued", InferenceEffort.Standard, WorkUrgency.Normal, DateTimeOffset.UtcNow.AddMilliseconds(500), null, ExecutionBoundary.LocalOnly));
        await kernel.RestartAsync(); client = new IpcClient(env.Socket); Check((await WaitStateAsync(client, queued, WorkState.Succeeded, 5000)).Attempts.Count == 1, "queued Work did not survive restart");

        string marker = Path.Combine(temp.Path, "marker.txt");
        string active = await SubmitAsync(client, Req($"SLOW:1300|{marker}", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); await WaitStateAsync(client, active, WorkState.Running); await WaitFileAsync(marker);
        await kernel.RestartAsync(); client = new IpcClient(env.Socket); WorkInspection unknown = await WaitStateAsync(client, active, WorkState.UnknownCompletion);
        Check(unknown.Attempts.Single().Outcome == PhysicalAttemptOutcome.UnknownCompletion, "restart lost UnknownCompletion truth");
        await Task.Delay(1400); Check(File.ReadAllLines(marker).Length == 1, "uncertain physical inference was duplicated");
        Console.WriteLine("PASS Java socket operation, cancellation, caller disappearance and restart recovery");
    }

    private static async Task ReconcileAndOpenBindingAsync()
    {
        using var temp = new TempDir("madre-reconcile");
        TestEnv env = MakeEnv(temp.Path, true, true); File.WriteAllText(env.SlowState, "available"); File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket);
        var client = new IpcClient(env.Socket); await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        string old = await SubmitAsync(client, Req("historical", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)); await WaitStateAsync(client, old, WorkState.Succeeded);
        await kernel.StopAsync();
        WriteConfig(env.Config, env.Database, env.SlowState, env.FastState, includeSlow: false, slowProbe: true, fastProbe: false);
        await kernel.StartAsync(); client = new IpcClient(env.Socket);
        Check((await client.CallAsync<List<CapabilitySnapshot>>("Capabilities", null)).All(x => x.Capability.CapabilityId != Slow), "stale configured capability survived restart");
        await using (var db = new SqliteConnection($"Data Source={env.Database}"))
        {
            await db.OpenAsync(); await using SqliteCommand cmd = db.CreateCommand(); cmd.CommandText = "SELECT COUNT(*) FROM attempts WHERE capability_id=$id"; cmd.Parameters.AddWithValue("$id", Slow);
            Check(Convert.ToInt32(await cmd.ExecuteScalarAsync()) == 1, "historical attempt evidence disappeared with configuration");
        }

        var cap = Cap("owner", "owner/custom", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "custom.db")), [cap], [new OwnerBinding()], 1); await engine.InitializeAsync(); engine.Start();
        string custom = await engine.SubmitAsync(Req("NONSENSE", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        Check((await WaitStateAsync(engine, custom, WorkState.Succeeded)).SelectedCapabilityId == "owner" && (await engine.ResultAsync(custom))?.Result == "nonsense-but-physically-valid", "open binding seam diverged from ordinary execution");
        Console.WriteLine("PASS catalogue reconciliation and open binding execution");
    }

    private static async Task NoHeadStarvationAsync()
    {
        using var temp = new TempDir("madre-starve");
        var waiting = Cap("waiting", "fixed/wait", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 100);
        var runnable = Cap("runnable", "fixed/run", InferenceEffort.Low, ExecutionBoundary.ExternalAllowed, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "starve.db")), [waiting, runnable], [new FixedBinding("fixed/wait", CapabilityAvailability.Unavailable, true), new FixedBinding("fixed/run", CapabilityAvailability.Available, false)], 1);
        await engine.InitializeAsync(); await engine.RefreshCapabilityStatesAsync();
        var ids = new List<string>(); for (int i = 0; i < 65; i++) ids.Add(await engine.SubmitAsync(Req($"wait-{i}", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)));
        string later = await engine.SubmitAsync(Req("later", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed)); engine.Start();
        Check((await WaitStateAsync(engine, later, WorkState.Succeeded)).SelectedCapabilityId == "runnable", "fixed-head starvation remains");
        foreach (string id in ids) Check((await engine.InspectAsync(id))?.State == WorkState.Queued, "waiting Work was incorrectly dispatched");
        string store = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "WorkStore.cs"));
        string scheduler = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "KernelEngine.cs"));
        string sqlite = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "SqliteDatabase.cs"));
        Check(!store.Contains("LIMIT -1", StringComparison.OrdinalIgnoreCase) && !store.Contains("CASE urgency", StringComparison.OrdinalIgnoreCase) && !scheduler.Contains("Task.Delay(25", StringComparison.Ordinal), "policy/polling leakage remains");
        Check(File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "Contracts.cs")).Contains("WorkUrgencyPolicy", StringComparison.Ordinal)
            && File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "Timing.cs")).Contains("KernelTimingOptions", StringComparison.Ordinal)
            && sqlite.Contains("BusyTimeoutMilliseconds", StringComparison.Ordinal), "retained policies have no explicit code owner");
        Console.WriteLine("PASS no fixed-head starvation and explicit policy ownership");
    }
}
