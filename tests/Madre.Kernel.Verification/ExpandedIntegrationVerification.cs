using System.Buffers.Binary;
using System.Diagnostics;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using Madre.Kernel;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static async Task RunExpandedIntegrationVerificationAsync(VerificationOptions options)
    {
        _ = options;
        await CapabilityObservationHostilesAsync();
        await ExpandedSqliteEdgesAsync();
        await SimultaneousOwnershipAsync();
        await ExpandedRawIpcEdgesAsync();
        await ExpandedJavaBoundaryAsync();
        await RequestLocalFailureMatrixAsync();
        await PostExecutionPersistenceFailureAsync();
        await ExpandedShutdownMatrixAsync();
        Console.WriteLine("PASS expanded capability/SQLite/ownership/IPC/Java/failure/shutdown verification");
    }

    private static async Task CapabilityObservationHostilesAsync()
    {
        using var temp = new TempDir("verify-probe-hostiles");

        var throwing = new ControlledBinding("controlled/probe-throw", "1")
        {
            ProbeHandler = _ => throw new IOException("controlled probe failure")
        };
        InferenceCapability throwingCap = Capability(
            "probe-throw", throwing.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "throw.db")),
            [throwingCap],
            [throwing],
            1))
        {
            await engine.InitializeAsync();
            CapabilitySnapshot snapshot = (await engine.RefreshCapabilityStatesAsync()).Single();
            Check(snapshot.State.Availability == CapabilityAvailability.Unknown && snapshot.State.ObservedAt.HasValue,
                "throwing probe did not become observed Unknown");
        }

        var ignoring = new ControlledBinding("controlled/probe-ignore-cancel", "1")
        {
            ProbeHandler = async _ =>
            {
                await Task.Delay(350).ConfigureAwait(false);
                return CapabilityAvailability.Available;
            }
        };
        InferenceCapability ignoringCap = Capability(
            "probe-ignore-cancel", ignoring.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "ignore.db")),
            [ignoringCap],
            [ignoring],
            1,
            timing: new KernelTimingOptions(TimeSpan.FromMilliseconds(30), TimeSpan.FromMilliseconds(50))))
        {
            await engine.InitializeAsync();
            Stopwatch elapsed = Stopwatch.StartNew();
            CapabilitySnapshot snapshot = (await engine.RefreshCapabilityStatesAsync()).Single();
            elapsed.Stop();
            Check(snapshot.State.Availability == CapabilityAvailability.Unknown,
                "probe ignoring cancellation fabricated availability after technical timeout");
            Check(elapsed.Elapsed < TimeSpan.FromSeconds(1),
                "probe ignoring cancellation held explicit refresh hostage");
        }

        DateTimeOffset start = new(2032, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var clock = new ManualKernelClock(start);
        var progressing = new ControlledBinding("controlled/probe-time", "1", CapabilityAvailability.Unavailable);
        InferenceCapability progressingCap = Capability(
            "probe-time", progressing.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "timestamps.db")),
            [progressingCap],
            [progressing],
            1,
            clock))
        {
            await engine.InitializeAsync();
            CapabilitySnapshot first = (await engine.RefreshCapabilityStatesAsync()).Single();
            clock.Advance(TimeSpan.FromSeconds(1));
            progressing.Availability = CapabilityAvailability.Available;
            CapabilitySnapshot second = (await engine.RefreshCapabilityStatesAsync()).Single();
            Check(first.State.ObservedAt == start
                && second.State.ObservedAt == start.AddSeconds(1)
                && second.State.ObservedAt > first.State.ObservedAt,
                "capability observation timestamp did not progress with re-observation");
        }
    }

    private static async Task ExpandedSqliteEdgesAsync()
    {
        using var temp = new TempDir("verify-sqlite-expanded");

        string structurallyBad = Path.Combine(temp.Path, "current-but-bad.db");
        await using (var connection = new SqliteConnection($"Data Source={structurallyBad}"))
        {
            await connection.OpenAsync();
            await using SqliteCommand command = connection.CreateCommand();
            command.CommandText = """
                PRAGMA user_version=1;
                CREATE TABLE capabilities (x INTEGER);
                CREATE TABLE capability_state (x INTEGER);
                CREATE TABLE work (x INTEGER);
                CREATE TABLE attempts (x INTEGER);
                """;
            await command.ExecuteNonQueryAsync();
        }
        bool structuralFailure = false;
        try
        {
            await new WorkStore(structurallyBad).InitializeAsync([], DateTimeOffset.UtcNow);
        }
        catch (Exception ex) when (ex is SqliteException or InvalidDataException)
        {
            structuralFailure = true;
        }
        Check(structuralFailure, "schema identity claiming current accepted structurally unusable database");

        string parentFile = Path.Combine(temp.Path, "not-a-directory");
        File.WriteAllText(parentFile, "blocking file");
        bool parentRejected = false;
        try
        {
            _ = new WorkStore(Path.Combine(parentFile, "kernel.db"));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or SqliteException)
        {
            parentRejected = true;
        }
        Check(parentRejected, "unusable database parent path was accepted");

        string trafficDb = Path.Combine(temp.Path, "many reader writer.db");
        var binding = new ControlledBinding("controlled/sqlite-traffic", "1")
        {
            ExecuteHandler = async (request, cancellationToken) =>
            {
                if (request.PreparedInput.EndsWith("7", StringComparison.Ordinal))
                {
                    await Task.Delay(2, cancellationToken).ConfigureAwait(false);
                }
                return BindingExecutionResult.Success(request.PreparedInput);
            }
        };
        InferenceCapability cap = Capability(
            "sqlite-traffic", binding.BindingId, "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(trafficDb), [cap], [binding], 8);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();
        Task<string>[] submissions = Enumerable.Range(0, 500)
            .Select(async i =>
            {
                string id = await engine.SubmitAsync(Req(
                    $"traffic-{i}", InferenceEffort.Low, (WorkUrgency)(i % 3), ExecutionBoundary.LocalOnly));
                _ = await engine.InspectAsync(id);
                if (i % 29 == 0)
                {
                    _ = await engine.CancelAsync(id);
                }
                return id;
            })
            .ToArray();
        string[] ids = await Task.WhenAll(submissions);
        await WaitAllTerminalAsync(engine, ids, 30_000);
        IReadOnlyList<WorkInspection> works = await InspectAllAsync(engine, ids);
        AssertGlobalPhysicalInvariants(works, binding.MaxActive, 8);
        Check(works.Select(work => work.WorkId).Distinct(StringComparer.Ordinal).Count() == works.Count,
            "concurrent SQLite traffic collapsed distinct durable Work identities");
    }

    private static async Task SimultaneousOwnershipAsync()
    {
        using var temp = new TempDir("verify-owner-simultaneous");
        string database = Path.Combine(temp.Path, "one physical owner.db");
        var contenders = new List<(RawHost Host, string Socket)>();
        for (int i = 0; i < 12; i++)
        {
            string socket = NewSocketPath();
            contenders.Add((RawHost.StartConfigured(null, database, socket), socket));
        }

        await Task.Delay(750);
        var survivors = new List<(RawHost Host, string Socket)>();
        foreach ((RawHost host, string socket) in contenders)
        {
            try
            {
                _ = await host.WaitForExitAsync(150);
            }
            catch (TimeoutException)
            {
                survivors.Add((host, socket));
            }
        }
        Check(survivors.Count == 1, $"simultaneous Kernel startups produced {survivors.Count} owners instead of one");
        Check((await new IpcClient(survivors[0].Socket).CallAsync<Health>("Health", null)).Status == "ok",
            "losing ownership contenders damaged the winning Kernel");

        string relative = Path.GetRelativePath(Directory.GetCurrentDirectory(), database);
        HostExit relativeAlias = await RunHostToExitAsync(["--db", relative, "--ipc-path", NewSocketPath()]);
        Check(relativeAlias.ExitCode != 0, "equivalent relative database path acquired a second owner");

        foreach ((RawHost host, _) in contenders)
        {
            await host.DisposeAsync();
        }
        string successorSocket = NewSocketPath();
        await using RawHost successor = await RawHost.StartHealthyAsync(null, database, successorSocket);
        Check((await new IpcClient(successorSocket).CallAsync<Health>("Health", null)).Status == "ok",
            "clean winning-owner exit did not allow immediate successor");
    }

    private static async Task ExpandedRawIpcEdgesAsync()
    {
        using var temp = new TempDir("verify-ipc-expanded");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);

        await SendDrop(env.Socket, [0]);
        await SendDrop(env.Socket, [0, 0, 0]);
        byte[] headerOnly = new byte[4];
        BinaryPrimitives.WriteInt32BigEndian(headerOnly, 20);
        await SendDrop(env.Socket, headerOnly);
        await SendDrop(env.Socket, headerOnly, Encoding.UTF8.GetBytes("{}"));

        byte[] health = RawRequestJson("one-byte", "Health");
        byte[]? oneByteResponse = await SendRawFrameAsync(
            env.Socket, health.Length, health, bodyChunkBytes: 1);
        Check(oneByteResponse is not null && RawResponseOk(oneByteResponse),
            "one-byte-at-a-time IPC request did not complete normally");

        byte[] extra = new byte[health.Length + 5];
        health.CopyTo(extra, 0);
        Encoding.ASCII.GetBytes("EXTRA").CopyTo(extra, health.Length);
        byte[]? extraResponse = await SendRawFrameAsync(env.Socket, health.Length, extra);
        Check(extraResponse is not null && RawResponseOk(extraResponse),
            "extra bytes after one complete request corrupted the completed request");

        string[] malformedOrIncomplete =
        [
            "",
            "null",
            "[]",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"missing-operation\",\"payload\":null}}",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"unknown-operation\",\"operation\":\"NoSuchOperation\",\"payload\":null}}"
        ];
        foreach (string raw in malformedOrIncomplete)
        {
            byte[] body = Encoding.UTF8.GetBytes(raw);
            _ = await SendRawFrameAsync(env.Socket, body.Length, body);
        }

        await AssertRawIpcErrorAsync(env.Socket,
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"missing-payload\",\"operation\":\"Submit\"}}",
            "InvalidRequest");
        await AssertRawIpcErrorAsync(env.Socket,
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"unexpected\",\"operation\":\"Submit\",\"payload\":{{\"preparedInput\":\"x\",\"requestedEffort\":\"Low\",\"urgency\":\"Normal\",\"executionBoundary\":\"LocalOnly\",\"unexpected\":1}}}}",
            "InvalidRequest");
        await AssertRawIpcErrorAsync(env.Socket,
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"numeric-enum\",\"operation\":\"Submit\",\"payload\":{{\"preparedInput\":\"x\",\"requestedEffort\":0,\"urgency\":\"Normal\",\"executionBoundary\":\"LocalOnly\"}}}}",
            "InvalidRequest");
        await AssertRawIpcErrorAsync(env.Socket,
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"missing-field\",\"operation\":\"Submit\",\"payload\":{{\"preparedInput\":\"x\",\"urgency\":\"Normal\",\"executionBoundary\":\"LocalOnly\"}}}}",
            "InvalidRequest");

        DateTimeOffset eligible = DateTimeOffset.UtcNow.AddMinutes(2);
        DateTimeOffset deadline = eligible.AddMinutes(-1);
        string invalidDates = JsonSerializer.Serialize(new
        {
            version = KernelProtocol.Version,
            requestId = "bad-dates",
            operation = "Submit",
            payload = new
            {
                preparedInput = "x",
                requestedEffort = "Low",
                urgency = "Normal",
                eligibleAt = eligible,
                deadline,
                executionBoundary = "LocalOnly"
            }
        });
        await AssertRawIpcErrorAsync(env.Socket, invalidDates, "InvalidRequest");

        string oversized = JsonSerializer.Serialize(new
        {
            version = KernelProtocol.Version,
            requestId = "oversized-input",
            operation = "Submit",
            payload = new
            {
                preparedInput = new string('x', KernelProtocol.MaxPayloadBytes + 1),
                requestedEffort = "Low",
                urgency = "Normal",
                executionBoundary = "LocalOnly"
            }
        });
        await AssertRawIpcErrorAsync(env.Socket, oversized, "InvalidRequest");

        foreach (string operation in new[] { "Inspect", "Result", "Cancel", "Release" })
        {
            string unknown = JsonSerializer.Serialize(new
            {
                version = KernelProtocol.Version,
                requestId = "unknown-" + operation,
                operation,
                payload = new { workId = "missing-work-id" }
            });
            await AssertRawIpcErrorAsync(env.Socket, unknown, "NotFound");
        }

        string queued = await SubmitAsync(client, new PhysicalInferenceRequest(
            "queued-release", InferenceEffort.Standard, WorkUrgency.Normal,
            DateTimeOffset.UtcNow.AddMinutes(5), null, ExecutionBoundary.LocalOnly));
        await AssertRawIpcErrorAsync(
            env.Socket,
            JsonSerializer.Serialize(new
            {
                version = KernelProtocol.Version,
                requestId = "release-queued",
                operation = "Release",
                payload = new { workId = queued }
            }),
            "WorkNotTerminal");

        string running = await SubmitAsync(client, Req(
            "SLOW:1500", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, running, WorkState.Running);
        await AssertRawIpcErrorAsync(
            env.Socket,
            JsonSerializer.Serialize(new
            {
                version = KernelProtocol.Version,
                requestId = "release-running",
                operation = "Release",
                payload = new { workId = running }
            }),
            "WorkNotTerminal");
        _ = await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(running));
        _ = await WaitIpcTerminalAsync(client, running);
        _ = await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(queued));

        _ = await SendRawFrameAsync(env.Socket, health.Length, health, readResponse: false);
        await Task.Delay(50);
        await AssertCleanIpcWorkAsync(client, "after-expanded-ipc-hostility");
    }

    private static bool RawResponseOk(byte[] response)
    {
        using JsonDocument document = JsonDocument.Parse(response);
        return document.RootElement.TryGetProperty("ok", out JsonElement ok) && ok.GetBoolean();
    }

    private static async Task AssertRawIpcErrorAsync(string socket, string json, string expectedCode)
    {
        byte[] body = Encoding.UTF8.GetBytes(json);
        byte[]? response = await SendRawFrameAsync(socket, body.Length, body);
        Check(response is not null, $"IPC {expectedCode} case closed without structured response");
        using JsonDocument document = JsonDocument.Parse(response!);
        JsonElement root = document.RootElement;
        string? actual = root.GetProperty("error").GetProperty("code").GetString();
        Check(!root.GetProperty("ok").GetBoolean() && actual == expectedCode,
            $"IPC error was {actual ?? "<none>"}, expected {expectedCode}");
    }

    private static async Task ExpandedJavaBoundaryAsync()
    {
        using var temp = new TempDir("verify-java-expanded");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);

        Check((await RunJavaToExitAsync(env.Socket, "capabilities")).ExitCode == 0,
            "Java capabilities operation failed");
        Check((await RunJavaToExitAsync(env.Socket, "refresh")).ExitCode == 0,
            "Java refresh-capabilities operation failed");
        Check((await RunJavaToExitAsync(env.Socket, "inspect", "unknown-work-id")).ExitCode != 0,
            "Java unknown Work inspect unexpectedly succeeded");

        string queued = await SubmitAsync(client, new PhysicalInferenceRequest(
            "java-nonterminal", InferenceEffort.Standard, WorkUrgency.Normal,
            DateTimeOffset.UtcNow.AddMinutes(2), null, ExecutionBoundary.LocalOnly));
        Check((await RunJavaToExitAsync(env.Socket, "result", queued)).ExitCode != 0,
            "Java nonterminal result unexpectedly succeeded");
        Check((await RunJavaToExitAsync(env.Socket, "cancel", queued)).ExitCode == 0,
            "Java cancel operation failed");
        Check((await RunJavaToExitAsync(env.Socket, "release", queued)).ExitCode == 0,
            "Java release of cancelled Work failed");

        string succeeded = (await JavaAsync(env.Socket, "submit", "java-release", "Standard", "Normal", "LocalOnly")).Trim();
        await WaitStateAsync(client, succeeded, WorkState.Succeeded);
        Check((await RunJavaToExitAsync(env.Socket, "inspect", succeeded)).ExitCode == 0,
            "Java inspect operation failed");
        Check((await RunJavaToExitAsync(env.Socket, "result", succeeded)).ExitCode == 0,
            "Java result operation failed");
        Check((await RunJavaToExitAsync(env.Socket, "release", succeeded)).ExitCode == 0,
            "Java release operation failed");

        string abruptSocket = NewSocketPath();
        using (var listener = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified))
        {
            listener.Bind(new UnixDomainSocketEndPoint(abruptSocket));
            listener.Listen(1);
            Task<ProcessExit> call = RunJavaToExitAsync(abruptSocket, "protocol");
            using Socket peer = await listener.AcceptAsync().WaitAsync(TimeSpan.FromSeconds(3));
            peer.Dispose();
            ProcessExit exit = await call.WaitAsync(TimeSpan.FromSeconds(8));
            Check(exit.ExitCode != 0, "Java client treated abrupt local-peer close as success");
        }
        try
        {
            if (File.Exists(abruptSocket))
            {
                File.Delete(abruptSocket);
            }
        }
        catch (IOException)
        {
        }
    }

    private static async Task RequestLocalFailureMatrixAsync()
    {
        using var temp = new TempDir("verify-request-local-matrix");

        string readDb = Path.Combine(temp.Path, "read.db");
        await using (var engine = new KernelEngine(new WorkStore(readDb), [], [], 1))
        {
            await engine.InitializeAsync();
            string id = await engine.SubmitAsync(Req(
                "read-failure", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            await ExecuteSqlAsync(readDb, "ALTER TABLE work RENAME TO work_hidden;");
            bool readFailed = false;
            try
            {
                _ = await engine.InspectAsync(id);
            }
            catch (SqliteException)
            {
                readFailed = true;
            }
            finally
            {
                await ExecuteSqlAsync(readDb, "ALTER TABLE work_hidden RENAME TO work;");
            }
            Check(readFailed, "deterministic inspect/read persistence fault was not surfaced request-locally");
            Check((await engine.InspectAsync(id))?.State == WorkState.Queued,
                "request-local read failure damaged durable Work truth after storage recovery");
        }

        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);

        string cancelId = await SubmitAsync(client, new PhysicalInferenceRequest(
            "cancel-fault", InferenceEffort.Standard, WorkUrgency.Normal,
            DateTimeOffset.UtcNow.AddMinutes(5), null, ExecutionBoundary.LocalOnly));
        await ExecuteSqlAsync(env.Database, $"""
            CREATE TRIGGER fail_cancel_request
            BEFORE UPDATE OF cancel_requested ON work
            WHEN OLD.work_id = '{cancelId}' AND NEW.cancel_requested = 1
            BEGIN
                SELECT RAISE(ABORT, 'forced cancel persistence failure');
            END;
            """);
        bool cancelFailed = false;
        try
        {
            _ = await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(cancelId));
        }
        catch (InvalidOperationException ex) when (ex.Message.Contains("InternalFailure", StringComparison.Ordinal))
        {
            cancelFailed = true;
        }
        Check(cancelFailed && (await client.CallAsync<Health>("Health", null)).Status == "ok",
            "request-local cancel persistence failure made Kernel falsely unhealthy");
        await ExecuteSqlAsync(env.Database, "DROP TRIGGER fail_cancel_request;");
        Check((await client.CallAsync<CancelReply>("Cancel", new WorkIdArg(cancelId))).State == WorkState.Cancelled,
            "Kernel could not cancel ordinary Work after request-local cancel failure");

        string releaseId = await SubmitAsync(client, Req(
            "release-fault", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, releaseId, WorkState.Succeeded);
        await ExecuteSqlAsync(env.Database, $"""
            CREATE TRIGGER fail_release_request
            BEFORE UPDATE OF released ON work
            WHEN OLD.work_id = '{releaseId}' AND NEW.released = 1
            BEGIN
                SELECT RAISE(ABORT, 'forced release persistence failure');
            END;
            """);
        bool releaseFailed = false;
        try
        {
            _ = await client.CallAsync<ReleaseReply>("Release", new WorkIdArg(releaseId));
        }
        catch (InvalidOperationException ex) when (ex.Message.Contains("InternalFailure", StringComparison.Ordinal))
        {
            releaseFailed = true;
        }
        Check(releaseFailed && (await client.CallAsync<Health>("Health", null)).Status == "ok",
            "request-local release persistence failure made Kernel falsely unhealthy");
        await ExecuteSqlAsync(env.Database, "DROP TRIGGER fail_release_request;");
        Check((await client.CallAsync<ReleaseReply>("Release", new WorkIdArg(releaseId))).Released,
            "Kernel could not release ordinary Work after request-local release failure");
        await AssertCleanIpcWorkAsync(client, "after-request-local-matrix");
    }

    private static async Task PostExecutionPersistenceFailureAsync()
    {
        using var temp = new TempDir("verify-post-execution-fatal");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        string workId;

        await using (RawHost host = await RawHost.StartHealthyAsync(env.Config, env.Database, env.Socket, 1))
        {
            var client = new IpcClient(env.Socket);
            await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
            await ExecuteSqlAsync(env.Database, $"""
                CREATE TRIGGER fail_post_execution_capability_state
                BEFORE UPDATE ON capability_state
                WHEN OLD.capability_id = '{Slow}'
                  AND OLD.availability = 'Available'
                  AND NEW.availability = 'Available'
                BEGIN
                    SELECT RAISE(ABORT, 'forced post-execution capability-state failure');
                END;
                """);
            workId = await SubmitAsync(client, Req(
                "post-execution-fatal", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            HostExit exit = await host.WaitForExitAsync(7_000);
            Check(exit.ExitCode != 0,
                "post-execution authoritative capability persistence failure left host falsely healthy");
        }

        await ExecuteSqlAsync(env.Database, "DROP TRIGGER fail_post_execution_capability_state;");
        await using RawHost restarted = await RawHost.StartHealthyAsync(env.Config, env.Database, env.Socket, 1);
        WorkInspection recovered = await new IpcClient(env.Socket).CallAsync<WorkInspection>(
            "Inspect", new WorkIdArg(workId));
        Check(recovered.State != WorkState.Running
            && recovered.Attempts.All(attempt => attempt.Outcome != PhysicalAttemptOutcome.Running),
            "restart after post-execution persistence failure retained false Running truth");
    }

    private static async Task ExpandedShutdownMatrixAsync()
    {
        using var temp = new TempDir("verify-shutdown-expanded");

        string idleDb = Path.Combine(temp.Path, "idle.db");
        await using (var idle = new KernelEngine(new WorkStore(idleDb), [], [], 1))
        {
            await idle.InitializeAsync();
            idle.Start();
        }
        await using (var idleRestart = new KernelEngine(new WorkStore(idleDb), [], [], 1))
        {
            await idleRestart.InitializeAsync();
        }

        string futureDb = Path.Combine(temp.Path, "future.db");
        DateTimeOffset now = new(2034, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var clock = new ManualKernelClock(now);
        string futureId;
        await using (var future = new KernelEngine(new WorkStore(futureDb), [], [], 1, clock))
        {
            await future.InitializeAsync();
            future.Start();
            futureId = await future.SubmitAsync(new PhysicalInferenceRequest(
                "future-shutdown", InferenceEffort.Low, WorkUrgency.Normal,
                now.AddHours(1), null, ExecutionBoundary.LocalOnly));
        }
        await using (var futureRestart = new KernelEngine(new WorkStore(futureDb), [], [], 1, clock))
        {
            await futureRestart.InitializeAsync();
            Check((await futureRestart.InspectAsync(futureId))?.State == WorkState.Queued,
                "orderly shutdown altered future queued Work");
        }

        var probeEntered = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var probe = new ControlledBinding("controlled/shutdown-probe", "1")
        {
            ProbeHandler = async cancellationToken =>
            {
                probeEntered.TrySetResult(true);
                await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken).ConfigureAwait(false);
                return CapabilityAvailability.Available;
            }
        };
        InferenceCapability probeCap = Capability(
            "shutdown-probe", probe.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        string probeDb = Path.Combine(temp.Path, "probe.db");
        var probeEngine = new KernelEngine(
            new WorkStore(probeDb), [probeCap], [probe], 1,
            timing: new KernelTimingOptions(TimeSpan.FromSeconds(10), TimeSpan.FromSeconds(1)));
        await probeEngine.InitializeAsync();
        probeEngine.Start();
        await probeEntered.Task.WaitAsync(TimeSpan.FromSeconds(3));
        await probeEngine.DisposeAsync();
        await using (var probeRestart = new KernelEngine(
            new WorkStore(probeDb), [probeCap], [new ControlledBinding(probe.BindingId, "1")], 1))
        {
            await probeRestart.InitializeAsync();
        }

        var unavailable = new ControlledBinding(
            "controlled/shutdown-unavailable", "1", CapabilityAvailability.Unavailable);
        InferenceCapability unavailableCap = Capability(
            "shutdown-unavailable", unavailable.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        string unavailableDb = Path.Combine(temp.Path, "unavailable.db");
        string pendingId;
        await using (var pending = new KernelEngine(
            new WorkStore(unavailableDb), [unavailableCap], [unavailable], 1,
            timing: new KernelTimingOptions(TimeSpan.FromMilliseconds(100), TimeSpan.FromMilliseconds(50))))
        {
            await pending.InitializeAsync();
            await pending.RefreshCapabilityStatesAsync();
            pending.Start();
            pendingId = await pending.SubmitAsync(Req(
                "pending-unavailable", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            await Task.Delay(50);
            Check((await pending.InspectAsync(pendingId))?.State == WorkState.Queued
                && unavailable.ExecutionCount == 0,
                "unavailable pending Work consumed physical execution before shutdown");
        }
        var available = new ControlledBinding(unavailable.BindingId, "1", CapabilityAvailability.Available);
        await using var resumed = new KernelEngine(
            new WorkStore(unavailableDb), [unavailableCap], [available], 1);
        await resumed.InitializeAsync();
        await resumed.RefreshCapabilityStatesAsync();
        resumed.Start();
        Check((await WaitTerminalAsync(resumed, pendingId)).State == WorkState.Succeeded,
            "pending unavailable Work could not resume after orderly shutdown/restart");
    }
}
