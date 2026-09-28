using System.Buffers.Binary;
using System.Net.Sockets;
using Madre.Kernel;
using Madre.Kernel.Host;

internal static partial class Program
{
    private static async Task RunExpandedQualificationVerificationAsync(VerificationOptions options)
    {
        int group = Math.Min(2_000, Math.Max(500, ScaleWorkCount(options.Scale) / 2));
        await SchedulerStormMatrixAsync(group);
        await MixedCapabilityObservationScaleAsync(Math.Min(100, Math.Max(1, group)));
        await IpcConcurrencyLoadAsync(Math.Min(750, Math.Max(200, group)));
        Console.WriteLine($"PASS expanded qualification scheduler/capability/IPC load group={group}");
    }

    private static async Task RunExpandedStressVerificationAsync(VerificationOptions options)
    {
        await MixedCapabilityObservationScaleAsync(1_000);
        await IpcConcurrencyLoadAsync(Math.Min(2_000, Math.Max(500, ScaleWorkCount(options.Scale))));
        Console.WriteLine($"PASS expanded stress capability/IPC load seed={options.Seed}");
    }

    private static async Task SchedulerStormMatrixAsync(int group)
    {
        using var temp = new TempDir("verify-scheduler-storms");
        DateTimeOffset start = new(2033, 4, 5, 6, 0, 0, TimeSpan.Zero);

        var deadlineClock = new ManualKernelClock(start);
        var unavailable = new ControlledBinding(
            "controlled/deadline-storm", "1", CapabilityAvailability.Unavailable);
        InferenceCapability unavailableCap = Capability(
            "deadline-storm", unavailable.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "deadline.db")),
            [unavailableCap],
            [unavailable],
            8,
            deadlineClock,
            new KernelTimingOptions(TimeSpan.FromMilliseconds(100), TimeSpan.FromMinutes(5))))
        {
            await engine.InitializeAsync();
            await engine.RefreshCapabilityStatesAsync();
            engine.Start();
            var ids = new List<string>(group);
            DateTimeOffset sharedDeadline = start.AddMinutes(1);
            for (int i = 0; i < group; i++)
            {
                ids.Add(await engine.SubmitAsync(new PhysicalInferenceRequest(
                    $"deadline-storm-{i}",
                    InferenceEffort.Low,
                    (WorkUrgency)(i % 3),
                    null,
                    sharedDeadline,
                    ExecutionBoundary.LocalOnly)));
            }
            await Task.Delay(20);
            Check(unavailable.ExecutionCount == 0, "known-unavailable deadline storm consumed a physical slot");
            deadlineClock.Advance(TimeSpan.FromMinutes(1));
            await WaitAllTerminalAsync(engine, ids, 30_000);
            IReadOnlyList<WorkInspection> expired = await InspectAllAsync(engine, ids);
            Check(expired.All(work => work.State == WorkState.Failed
                    && work.Failure?.Kind == PhysicalFailureKind.DeadlineExpired
                    && work.Attempts.Count == 0),
                "shared deadline storm created attempts or stranded Work");
        }

        var effortBinding = new ControlledBinding("controlled/inadmissible-mix", "1");
        InferenceCapability lowOnly = Capability(
            "low-only", effortBinding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "inadmissible.db")),
            [lowOnly],
            [effortBinding],
            16))
        {
            await engine.InitializeAsync();
            await engine.RefreshCapabilityStatesAsync();
            engine.Start();
            var impossible = new List<string>(group);
            var runnable = new List<string>(group);
            for (int i = 0; i < group; i++)
            {
                impossible.Add(await engine.SubmitAsync(Req(
                    $"inadmissible-{i}", InferenceEffort.High, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly)));
                runnable.Add(await engine.SubmitAsync(Req(
                    $"runnable-{i}", InferenceEffort.Low, WorkUrgency.Background, ExecutionBoundary.LocalOnly)));
            }
            await WaitAllTerminalAsync(engine, impossible.Concat(runnable), 45_000);
            Check((await InspectAllAsync(engine, impossible)).All(work =>
                    work.State == WorkState.Failed
                    && work.Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability
                    && work.Attempts.Count == 0),
                "large inadmissible group invented capability or attempt");
            Check((await InspectAllAsync(engine, runnable)).All(work => work.State == WorkState.Succeeded),
                "large inadmissible group fixed-head-starved later runnable Work");
        }

        var localWaiting = new ControlledBinding(
            "controlled/local-waiting", "1", CapabilityAvailability.Unavailable);
        var externalRunnable = new ControlledBinding(
            "controlled/external-runnable", "1", CapabilityAvailability.Available);
        InferenceCapability localCap = Capability(
            "local-waiting", localWaiting.BindingId, "1", InferenceEffort.Standard, ExecutionBoundary.LocalOnly, 100);
        InferenceCapability externalCap = Capability(
            "external-runnable", externalRunnable.BindingId, "1", InferenceEffort.High, ExecutionBoundary.ExternalAllowed, 10);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "waiting-mix.db")),
            [localCap, externalCap],
            [localWaiting, externalRunnable],
            8,
            timing: new KernelTimingOptions(TimeSpan.FromMilliseconds(100), TimeSpan.FromMilliseconds(100))))
        {
            await engine.InitializeAsync();
            await engine.RefreshCapabilityStatesAsync();
            engine.Start();
            var waiting = new List<string>(group);
            var runnable = new List<string>(group);
            for (int i = 0; i < group; i++)
            {
                waiting.Add(await engine.SubmitAsync(Req(
                    $"waiting-{i}", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly)));
                runnable.Add(await engine.SubmitAsync(Req(
                    $"external-{i}", InferenceEffort.High, WorkUrgency.Background, ExecutionBoundary.ExternalAllowed)));
            }
            await WaitAllTerminalAsync(engine, runnable, 45_000);
            Check((await InspectAllAsync(engine, runnable)).All(work => work.State == WorkState.Succeeded),
                "large waiting group prevented later physically runnable Work");
            Check(localWaiting.ExecutionCount == 0
                && (await InspectAllAsync(engine, waiting)).All(work => work.State == WorkState.Queued && work.Attempts.Count == 0),
                "known-unavailable waiting Work consumed physical execution");
            foreach (string id in waiting)
            {
                _ = await engine.CancelAsync(id);
            }
        }

        var closeClock = new ManualKernelClock(start);
        var changing = new ControlledBinding(
            "controlled/near-deadline", "1", CapabilityAvailability.Unavailable);
        InferenceCapability changingCap = Capability(
            "near-deadline", changing.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "near-deadline.db")),
            [changingCap],
            [changing],
            1,
            closeClock,
            new KernelTimingOptions(TimeSpan.FromMilliseconds(100), TimeSpan.FromHours(1))))
        {
            await engine.InitializeAsync();
            await engine.RefreshCapabilityStatesAsync();
            engine.Start();
            string id = await engine.SubmitAsync(new PhysicalInferenceRequest(
                "near-deadline", InferenceEffort.Low, WorkUrgency.Interactive,
                null, start.AddMinutes(1), ExecutionBoundary.LocalOnly));
            closeClock.Advance(TimeSpan.FromSeconds(59));
            changing.Availability = CapabilityAvailability.Available;
            _ = await engine.RefreshCapabilityStatesAsync();
            WorkInspection terminal = await WaitTerminalAsync(engine, id);
            Check(terminal.State == WorkState.Succeeded && terminal.Attempts.Count == 1,
                "capability becoming Available close to deadline did not execute truthfully");
        }
    }

    private static async Task MixedCapabilityObservationScaleAsync(int count)
    {
        using var temp = new TempDir($"verify-capability-mixed-{count}");
        var bindings = new List<ControlledBinding>(count);
        var capabilities = new List<InferenceCapability>(count);
        for (int i = 0; i < count; i++)
        {
            var binding = new ControlledBinding(
                $"controlled/mixed-{i}",
                "1",
                (CapabilityAvailability)(i % 3));
            bindings.Add(binding);
            capabilities.Add(Capability(
                $"mixed-{i}", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, i));
        }

        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "mixed.db")),
            capabilities,
            bindings,
            8);
        await engine.InitializeAsync();
        IReadOnlyList<CapabilitySnapshot> first = await engine.RefreshCapabilityStatesAsync();
        Check(first.Count == count, "mixed capability observation lost catalogue entries");
        for (int i = 0; i < count; i++)
        {
            Check(first.Single(snapshot => snapshot.Capability.CapabilityId == $"mixed-{i}").State.Availability
                    == (CapabilityAvailability)(i % 3),
                $"mixed capability {i} observation mismatch");
            bindings[i].Availability = i % 2 == 0
                ? CapabilityAvailability.Available
                : CapabilityAvailability.Unavailable;
        }
        IReadOnlyList<CapabilitySnapshot> second = await engine.RefreshCapabilityStatesAsync();
        Check(second.Count(snapshot => snapshot.State.Availability == CapabilityAvailability.Available) == (count + 1) / 2,
            "alternating capability re-observation did not reconcile current physical truth");
    }

    private static async Task IpcConcurrencyLoadAsync(int operations)
    {
        using var temp = new TempDir("verify-ipc-load-expanded");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);

        for (int offset = 0; offset < operations; offset += 16)
        {
            int batch = Math.Min(16, operations - offset);
            Health[] health = await Task.WhenAll(
                Enumerable.Range(0, batch).Select(_ => client.CallAsync<Health>("Health", null)));
            Check(health.All(value => value.Status == "ok"),
                "healthy bounded concurrent Health batch failed");
        }

        var stalled = new List<Socket>();
        try
        {
            byte[] header = new byte[4];
            BinaryPrimitives.WriteInt32BigEndian(header, 100);
            for (int i = 0; i < KernelIpcDefaults.MaxActiveClients; i++)
            {
                var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
                await socket.ConnectAsync(new UnixDomainSocketEndPoint(env.Socket));
                await socket.SendAsync(header, SocketFlags.None);
                await socket.SendAsync(new byte[] { (byte)'{' }, SocketFlags.None);
                stalled.Add(socket);
            }

            using var overflow = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
            await overflow.ConnectAsync(new UnixDomainSocketEndPoint(env.Socket));
            await Task.Delay(50);

            var backlogAttempts = Enumerable.Range(0, KernelIpcDefaults.ListenBacklog + 16)
                .Select(async _ =>
                {
                    var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
                    try
                    {
                        using var timeout = new CancellationTokenSource(TimeSpan.FromMilliseconds(400));
                        await socket.ConnectAsync(new UnixDomainSocketEndPoint(env.Socket), timeout.Token);
                        await socket.SendAsync(header, SocketFlags.None, timeout.Token);
                        await socket.SendAsync(new byte[] { (byte)'{' }, SocketFlags.None, timeout.Token);
                        return socket;
                    }
                    catch (Exception ex) when (ex is SocketException or OperationCanceledException or IOException)
                    {
                        socket.Dispose();
                        return null;
                    }
                })
                .ToArray();
            Socket?[] extra = await Task.WhenAll(backlogAttempts);
            stalled.AddRange(extra.Where(socket => socket is not null).Select(socket => socket!));
        }
        finally
        {
            foreach (Socket socket in stalled)
            {
                socket.Dispose();
            }
        }

        await Task.Delay(150);
        Check((await client.CallAsync<Health>("Health", null)).Status == "ok",
            "stalled/backlog IPC pressure permanently denied later valid client");

        int workCount = Math.Min(250, Math.Max(50, operations / 4));
        var ids = new List<string>(workCount);
        for (int i = 0; i < workCount; i++)
        {
            ids.Add(await SubmitAsync(client, Req(
                $"SLOW:{10 + (i % 20)}", InferenceEffort.Standard, (WorkUrgency)(i % 3), ExecutionBoundary.LocalOnly)));
        }
        Task[] traffic = ids.Select((id, index) => Task.Run(async () =>
        {
            try
            {
                _ = await InspectAsync(client, id);
                if (index % 7 == 0)
                {
                    _ = await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(id));
                }
                try
                {
                    _ = await client.CallAsync<WorkResultSnapshot>("Result", new WorkIdArg(id));
                }
                catch (InvalidOperationException)
                {
                }
            }
            catch (Exception ex) when (ex is IOException or SocketException or InvalidOperationException)
            {
            }
        })).ToArray();
        await Task.WhenAll(traffic);
        foreach (string id in ids)
        {
            _ = await WaitIpcTerminalAsync(client, id, 30_000);
        }

        var attached = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
        await attached.ConnectAsync(new UnixDomainSocketEndPoint(env.Socket));
        byte[] partial = new byte[4];
        BinaryPrimitives.WriteInt32BigEndian(partial, 100);
        await attached.SendAsync(partial, SocketFlags.None);
        await kernel.RestartAsync();
        attached.Dispose();
        client = new IpcClient(env.Socket);
        await AssertCleanIpcWorkAsync(client, "after-ipc-load-restart");
    }
}
