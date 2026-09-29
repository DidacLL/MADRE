using System.Buffers.Binary;
using System.Diagnostics;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;
using Madre.Kernel.Host;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private const string Slow = "slow-local";
    private const string Fast = "fast-external";
    private static readonly JsonSerializerOptions Json = JsonOptions();
    private static string Root = string.Empty;
    private static string HostDll = string.Empty;
    private static string FixtureDll = string.Empty;
    private static string JavaClasspath = string.Empty;
    private static string Dotnet = string.Empty;

    private static void InitPaths()
    {
        Root = FindRoot();
        HostDll = Path.Combine(Root, "kernel", "src", "Madre.Kernel.Host", "bin", "Release", "net10.0", "Madre.Kernel.Host.dll");
        FixtureDll = Path.Combine(Root, "tests", "Madre.Kernel.ProcessFixture", "bin", "Release", "net10.0", "Madre.Kernel.ProcessFixture.dll");
        JavaClasspath = File.ReadAllText(Path.Combine(Root, "madre-kernel-client", "build", "acceptance-classpath.txt")).Trim();
        Dotnet = Environment.GetEnvironmentVariable("DOTNET_HOST_PATH") ?? "dotnet";
        Check(File.Exists(HostDll) && File.Exists(FixtureDll) && JavaClasspath.Length > 0, "acceptance prerequisites missing");
    }

    private static TestEnv MakeEnv(string dir, bool slowProbe, bool fastProbe)
    {
        var env = new TestEnv(Path.Combine(dir, "kernel.json"), Path.Combine(dir, "kernel.db"), Path.Combine(dir, "slow.state"), Path.Combine(dir, "fast.state"), NewSocketPath());
        WriteConfig(env.Config, env.Database, env.SlowState, env.FastState, true, slowProbe, fastProbe); return env;
    }

    private static void WriteConfig(string config, string db, string slowState, string fastState, bool includeSlow, bool slowProbe, bool fastProbe)
    {
        var caps = new List<object>();
        if (includeSlow) caps.Add(new { capabilityId = Slow, bindingId = "process/slow", bindingVersion = "1", executable = Dotnet, arguments = new[] { FixtureDll, "--delay-ms", "180", "--prefix", "slow:" }, probeArguments = slowProbe ? new[] { FixtureDll, "--probe", slowState } : null, executionLocation = "Local", destination = "local-process", route = "owner-local-process", dataRetention = "process-defined", supportedEffort = "Standard", ownerPreference = 100 });
        caps.Add(new { capabilityId = Fast, bindingId = "process/fast", bindingVersion = "1", executable = Dotnet, arguments = new[] { FixtureDll, "--delay-ms", "20", "--prefix", "fast:" }, probeArguments = fastProbe ? new[] { FixtureDll, "--probe", fastState } : null, executionLocation = "External", destination = "test-external", route = "test-route", dataRetention = "test-retention", supportedEffort = "High", ownerPreference = 10 });
        File.WriteAllText(config, JsonSerializer.Serialize(new { databasePath = db, maxConcurrent = 1, capabilities = caps }, Json));
    }

    private static PhysicalInferenceRequest Req(string input, InferenceEffort effort, WorkUrgency urgency, ExecutionBoundary boundary) => new(input, effort, urgency, null, null, boundary);
    private static InferenceCapability Cap(string id, string binding, InferenceEffort effort, ExecutionBoundary boundary, int preference) => new(
        id,
        binding,
        "1",
        new CapabilityExecutionPath(
            new ConfiguredFact<ExecutionLocation>(boundary == ExecutionBoundary.LocalOnly ? ExecutionLocation.Local : ExecutionLocation.External, FactProvenance.Owner),
            new ConfiguredFact<string>(id, FactProvenance.Owner)),
        new ConfiguredFact<InferenceEffort>(effort, FactProvenance.Owner),
        preference);
    private static Task<string> SubmitAsync(IpcClient c, PhysicalInferenceRequest r) => SubmitCoreAsync(c, r);
    private static async Task<string> SubmitCoreAsync(IpcClient c, PhysicalInferenceRequest r) => (await c.CallAsync<WorkSubmissionResponse>("Submit", r)).WorkId;
    private static Task<WorkInspection> InspectAsync(IpcClient c, string id) => c.CallAsync<WorkInspection>("Inspect", new WorkIdArg(id));

    private static async Task<WorkInspection> WaitStateAsync(IpcClient c, string id, WorkState expected, int timeoutMs = 6000)
    {
        Stopwatch sw = Stopwatch.StartNew(); WorkInspection? latest = null;
        while (sw.ElapsedMilliseconds < timeoutMs) { latest = await InspectAsync(c, id); if (latest.State == expected) return latest; await Task.Delay(20); }
        throw new InvalidOperationException($"Work {id} did not reach {expected}; latest={latest?.State}, failure={latest?.Failure}");
    }

    private static async Task<WorkInspection> WaitStateAsync(KernelEngine engine, string id, WorkState expected, int timeoutMs = 5000)
    {
        Stopwatch sw = Stopwatch.StartNew(); WorkInspection? latest = null;
        while (sw.ElapsedMilliseconds < timeoutMs) { latest = await engine.InspectAsync(id); if (latest?.State == expected) return latest; await Task.Delay(20); }
        throw new InvalidOperationException($"in-process Work {id} did not reach {expected}; latest={latest?.State}");
    }

    private static async Task<CapabilitySnapshot> WaitCapabilityAsync(IpcClient c, string id, CapabilityAvailability expected)
    {
        Stopwatch sw = Stopwatch.StartNew();
        while (sw.Elapsed < TimeSpan.FromSeconds(5)) { CapabilitySnapshot x = (await c.CallAsync<List<CapabilitySnapshot>>("Capabilities", null)).Single(v => v.Capability.CapabilityId == id); if (x.State.Availability == expected) return x; await Task.Delay(30); }
        throw new InvalidOperationException($"Capability {id} did not reach {expected}");
    }

    private static async Task AssertReleasedAsync(string dbPath, string id)
    {
        await using var db = new SqliteConnection($"Data Source={dbPath}"); await db.OpenAsync(); await using SqliteCommand cmd = db.CreateCommand();
        cmd.CommandText = "SELECT prepared_input IS NULL, result_text IS NULL, released FROM work WHERE work_id=$id"; cmd.Parameters.AddWithValue("$id", id);
        await using SqliteDataReader r = await cmd.ExecuteReaderAsync(); Check(await r.ReadAsync() && r.GetInt64(0) == 1 && r.GetInt64(1) == 1 && r.GetInt64(2) == 1, "release retained payload/result");
    }

    private static async Task WaitFileAsync(string path) { Stopwatch sw = Stopwatch.StartNew(); while (sw.Elapsed < TimeSpan.FromSeconds(3)) { if (File.Exists(path)) return; await Task.Delay(20); } throw new InvalidOperationException("marker was not created"); }

    private static async Task<string> JavaAsync(string socket, params string[] args)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "java",
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add("-Dfile.encoding=UTF-8");
        psi.ArgumentList.Add("-Dstdout.encoding=UTF-8");
        psi.ArgumentList.Add("-Dstderr.encoding=UTF-8");
        psi.ArgumentList.Add("-cp"); psi.ArgumentList.Add(JavaClasspath); psi.ArgumentList.Add("io.github.didacll.madre.kernel.client.KernelClientProcess"); psi.ArgumentList.Add(socket); foreach (string a in args) psi.ArgumentList.Add(a);
        using Process p = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Java helper"); string stdout = await p.StandardOutput.ReadToEndAsync(); string stderr = await p.StandardError.ReadToEndAsync(); await p.WaitForExitAsync();
        Check(p.ExitCode == 0, "Java client failed: " + stderr); return stdout;
    }

    private static string NewSocketPath() => Path.Combine(Path.GetTempPath(), "mk-" + Guid.NewGuid().ToString("N")[..8] + ".sock");
    private static string FindRoot() { DirectoryInfo? d = new(Directory.GetCurrentDirectory()); while (d is not null) { if (File.Exists(Path.Combine(d.FullName, "NORTH_STAR.md"))) return d.FullName; d = d.Parent; } throw new InvalidOperationException("repository root not found"); }
    private static JsonSerializerOptions JsonOptions() { var o = new JsonSerializerOptions(JsonSerializerDefaults.Web); o.Converters.Add(new JsonStringEnumConverter()); return o; }
    private static void Check(bool ok, string message) { if (!ok) throw new InvalidOperationException(message); }

    private sealed record TestEnv(string Config, string Database, string SlowState, string FastState, string Socket);
    private sealed record WorkIdArg(string WorkId);
    private sealed record CancelReply(WorkState State);
    private sealed record ReleaseReply(bool Released);
    private sealed record Health(string Status);

    private sealed class IpcClient(string socketPath)
    {
        public async Task<T> CallAsync<T>(string operation, object? payload)
        {
            string id = Guid.NewGuid().ToString("N"); byte[] body = JsonSerializer.SerializeToUtf8Bytes(new { version = KernelProtocol.Version, requestId = id, operation, payload }, Json); Check(body.Length <= KernelProtocol.MaxFrameBytes, "request frame too large");
            using var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified); await socket.ConnectAsync(new UnixDomainSocketEndPoint(socketPath)); using var stream = new NetworkStream(socket, false);
            byte[] header = new byte[4]; BinaryPrimitives.WriteInt32BigEndian(header, body.Length); await stream.WriteAsync(header); await stream.WriteAsync(body); await stream.FlushAsync(); await stream.ReadExactlyAsync(header); int length = BinaryPrimitives.ReadInt32BigEndian(header); Check(length > 0 && length <= KernelProtocol.MaxFrameBytes, "response frame invalid");
            byte[] bytes = new byte[length]; await stream.ReadExactlyAsync(bytes); IpcEnvelope e = JsonSerializer.Deserialize<IpcEnvelope>(bytes, Json) ?? throw new InvalidOperationException("missing IPC response"); Check(e.Version == KernelProtocol.Version && e.RequestId == id, "response correlation mismatch"); if (!e.Ok) throw new InvalidOperationException($"IPC {e.Error?.Code}: {e.Error?.Detail}"); return e.Payload.Deserialize<T>(Json) ?? throw new InvalidOperationException("missing IPC payload");
        }
        private sealed record IpcEnvelope(int Version, string RequestId, bool Ok, JsonElement Payload, IpcError? Error);
        private sealed record IpcError(string Code, string? Detail);
    }

    private sealed class KernelProcess : IAsyncDisposable
    {
        private readonly string? _config; private readonly string _db; private readonly int? _max; private Process? _process; private readonly StringBuilder _log = new();
        private KernelProcess(string? config, string db, string socket, int? max) { _config = config; _db = db; Socket = socket; _max = max; }
        public string Socket { get; }
        public static async Task<KernelProcess> StartAsync(string? config, string db, string socket, int? max = null) { var k = new KernelProcess(config, db, socket, max); await k.StartAsync(); return k; }
        public async Task StartAsync()
        {
            var psi = new ProcessStartInfo { FileName = Dotnet, RedirectStandardOutput = true, RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true };
            psi.ArgumentList.Add(HostDll); if (_config is not null) { psi.ArgumentList.Add("--config"); psi.ArgumentList.Add(_config); } psi.ArgumentList.Add("--db"); psi.ArgumentList.Add(_db); psi.ArgumentList.Add("--ipc-path"); psi.ArgumentList.Add(Socket); if (_max.HasValue) { psi.ArgumentList.Add("--max-concurrent"); psi.ArgumentList.Add(_max.Value.ToString()); }
            _process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Kernel"); _process.OutputDataReceived += (_, e) => { if (e.Data is not null) lock (_log) _log.AppendLine(e.Data); }; _process.ErrorDataReceived += (_, e) => { if (e.Data is not null) lock (_log) _log.AppendLine(e.Data); }; _process.BeginOutputReadLine(); _process.BeginErrorReadLine();
            Stopwatch sw = Stopwatch.StartNew(); var c = new IpcClient(Socket); while (sw.Elapsed < TimeSpan.FromSeconds(7)) { if (_process.HasExited) throw new InvalidOperationException("Kernel exited: " + _log); try { if ((await c.CallAsync<Health>("Health", null)).Status == "ok") return; } catch (Exception ex) when (ex is SocketException or IOException or InvalidOperationException) { } await Task.Delay(30); } throw new InvalidOperationException("Kernel not healthy: " + _log);
        }
        public async Task RestartAsync() { await StopAsync(); await StartAsync(); }
        public async Task StopAsync()
        {
            if (_process is not null) { if (!_process.HasExited) { _process.Kill(entireProcessTree: true); await _process.WaitForExitAsync(); } _process.Dispose(); _process = null; }
            try { if (File.Exists(Socket)) File.Delete(Socket); } catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
        }
        public async ValueTask DisposeAsync() => await StopAsync();
    }

    private sealed class OwnerBinding : IInferenceBinding
    {
        public string BindingId => "owner/custom"; public string BindingVersion => "1";
        public Task<CapabilityAvailability> ProbeAsync(CancellationToken ct) => Task.FromResult(CapabilityAvailability.Unknown);
        public Task<BindingExecutionResult> ExecuteAsync(InferenceExecutionRequest request, CancellationToken ct) => Task.FromResult(BindingExecutionResult.Success("nonsense-but-physically-valid"));
    }

    private sealed class FixedBinding(string id, CapabilityAvailability availability, bool fail) : IInferenceBinding
    {
        public string BindingId => id; public string BindingVersion => "1"; public Task<CapabilityAvailability> ProbeAsync(CancellationToken ct) => Task.FromResult(availability);
        public Task<BindingExecutionResult> ExecuteAsync(InferenceExecutionRequest request, CancellationToken ct) { if (fail) throw new InvalidOperationException("unavailable binding executed"); return Task.FromResult(BindingExecutionResult.Success("ok:" + request.PreparedInput)); }
    }

    private sealed class SlowProbeBinding : IInferenceBinding
    {
        public string BindingId => "probe/slow"; public string BindingVersion => "1";
        public async Task<CapabilityAvailability> ProbeAsync(CancellationToken ct) { await Task.Delay(2000, ct); return CapabilityAvailability.Available; }
        public Task<BindingExecutionResult> ExecuteAsync(InferenceExecutionRequest request, CancellationToken ct) => Task.FromResult(BindingExecutionResult.Success("ok:" + request.PreparedInput));
    }

    private sealed class TempDir : IDisposable
    {
        public TempDir(string prefix) { Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), prefix + "-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(Path); }
        public string Path { get; }
        public void Dispose() { SqliteConnection.ClearAllPools(); try { Directory.Delete(Path, true); } catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { } }
    }
}
