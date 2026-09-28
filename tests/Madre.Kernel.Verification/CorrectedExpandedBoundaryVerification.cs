using System.Buffers.Binary;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using Madre.Kernel;

internal static partial class Program
{
    private static async Task RunCorrectedExpandedIntegrationVerificationAsync(VerificationOptions options)
    {
        _ = options;
        await CapabilityObservationHostilesAsync();
        await ExpandedSqliteEdgesAsync();
        await SimultaneousOwnershipAsync();
        await CorrectedRawIpcEdgesAsync();
        await CorrectedJavaBoundaryAsync();
        await RequestLocalFailureMatrixAsync();
        await PostExecutionPersistenceFailureAsync();
        await ExpandedShutdownMatrixAsync();
        Console.WriteLine("PASS expanded capability/SQLite/ownership/IPC/Java/failure/shutdown verification");
    }

    private static async Task CorrectedRawIpcEdgesAsync()
    {
        using var temp = new TempDir("verify-ipc-expanded-corrected");
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

        string[] connectionHostility =
        [
            "",
            "null",
            "[]",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"missing-operation\",\"payload\":null}}",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"unknown-operation\",\"operation\":\"NoSuchOperation\",\"payload\":null}}",
            $"{{\"version\":{KernelProtocol.Version},\"requestId\":\"missing-payload\",\"operation\":\"Submit\"}}"
        ];
        foreach (string raw in connectionHostility)
        {
            byte[] body = Encoding.UTF8.GetBytes(raw);
            _ = await SendRawFrameAsync(env.Socket, body.Length, body);
        }

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

    private static async Task CorrectedJavaBoundaryAsync()
    {
        using var temp = new TempDir("verify-java-expanded-corrected");
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
        ProcessExit nonterminal = await RunJavaToExitAsync(env.Socket, "result", queued);
        Check(nonterminal.ExitCode == 0
            && nonterminal.Stdout.Contains("Queued", StringComparison.Ordinal)
            && nonterminal.Stdout.Contains("\"result\":null", StringComparison.Ordinal),
            "Java nonterminal Result did not preserve queued/no-result physical truth");
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
}
