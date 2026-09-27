using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;
using Microsoft.Data.Sqlite;

internal static class Program
{
    private const string Capability = "checkpoint-capability";
    private static readonly JsonSerializerOptions Json = CreateJson();
    private static string _root = string.Empty;
    private static string _hostDll = string.Empty;
    private static string _fixtureDll = string.Empty;
    private static string _dotnet = string.Empty;

    public static async Task<int> Main()
    {
        _root = FindRepositoryRoot();
        _hostDll = Path.Combine(_root, "kernel", "src", "Madre.Kernel.Host", "bin", "Release", "net10.0", "Madre.Kernel.Host.dll");
        _fixtureDll = Path.Combine(_root, "tests", "Madre.Kernel.ProcessFixture", "bin", "Release", "net10.0", "Madre.Kernel.ProcessFixture.dll");
        _dotnet = Environment.GetEnvironmentVariable("DOTNET_HOST_PATH") ?? "dotnet";

        DreChoosesConcreteStrategy();
        await CheckpointHardRestartResumeAsync();
        await CheckpointDeadlinePreventsResumeAsync();
        await CancelledCheckpointDoesNotResumeAsync();
        await IncompatibleStrategyDoesNotResumeAsync();
        await IncompatibleBindingDoesNotResumeAsync();
        await SimpleInferenceBypassesMafAsync();
        Console.WriteLine("MADRE Lane C MAF checkpoint/restart acceptance passed");
        return 0;
    }

    private static void DreChoosesConcreteStrategy()
    {
        var capability = new InferenceCapability(
            Capability,
            "process/checkpoint",
            "1",
            new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.ExternalAllowed, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.High, FactProvenance.Owner),
            10);
        var snapshot = new CapabilitySnapshot(
            capability,
            new CapabilityState(Capability, CapabilityAvailability.Available, DateTimeOffset.UtcNow),
            null,
            0,
            0);
        DreDecision decision = new DreSelector().Select(
            TwoStageRequest("strategy-selection"), [snapshot]);
        Assert(decision.Kind == DreDecisionKind.Selected && decision.UseCheckpointedTwoStageStrategy,
            "DRE did not choose the concrete two-stage physical strategy");
    }

    private static async Task CheckpointHardRestartResumeAsync()
    {
        using var temp = new TempDirectory("madre-maf-restart");
        TestEnvironment env = CreateEnvironment(temp.Path, "1");
        WriteState(env.StateFile, "available");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        KernelProcess kernel = await StartKernelAsync(env);
        try
        {
            string workId = await SubmitAsync(kernel, TwoStageRequest("MAF_STAGE_A_MARKER:" + marker));
            await WaitForFileAsync(marker);
            WriteState(env.StateFile, "unavailable");
            await RefreshAsync(kernel);
            WorkInspection checkpointed = await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            Assert(checkpointed.Attempts.Count == 1 && MarkerCount(marker) == 1,
                "stage A did not reach one durable physical attempt before checkpoint");
            Assert(!string.IsNullOrWhiteSpace(checkpointed.CheckpointId), "subordinate MAF checkpoint was not linked from Work");

            await kernel.StopAsync();
            await kernel.RestartAsync();
            Assert((await InspectAsync(kernel, workId)).State == WorkState.Checkpointed,
                "unavailable selected capability was ignored during checkpoint recovery");
            WriteState(env.StateFile, "available");
            await RefreshAsync(kernel);
            WorkInspection completed = await WaitForStateAsync(kernel, workId, WorkState.Succeeded, 10000);
            WorkResultSnapshot result = await ResultAsync(kernel, workId);
            Assert(completed.Attempts.Count == 2 && completed.Attempts.All(attempt => attempt.Outcome == PhysicalAttemptOutcome.Succeeded),
                "two-stage strategy did not retain two truthful physical attempts");
            Assert(MarkerCount(marker) == 1, "stage A replayed after hard Kernel death");
            Assert(result.Result == "stage:stage:MAF_STAGE_A_MARKER:" + marker,
                "stage B did not consume the durable stage-A physical output");
            Console.WriteLine("PASS MAF durable checkpoint, hard Kernel death and MADRE-authorized resume without stage-A replay");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task CheckpointDeadlinePreventsResumeAsync()
    {
        using var temp = new TempDirectory("madre-maf-deadline");
        TestEnvironment env = CreateEnvironment(temp.Path, "1");
        WriteState(env.StateFile, "available");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        DateTimeOffset deadline = DateTimeOffset.UtcNow.AddSeconds(5);
        KernelProcess kernel = await StartKernelAsync(env);
        try
        {
            string workId = await SubmitAsync(kernel, new PhysicalInferenceRequest(
                "MAF_STAGE_A_MARKER:" + marker,
                InferenceEffort.High,
                WorkUrgency.Background,
                null,
                deadline,
                ExecutionBoundary.ExternalAllowed));
            await WaitForFileAsync(marker);
            WriteState(env.StateFile, "unavailable");
            await RefreshAsync(kernel);
            await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            await kernel.StopAsync();
            TimeSpan wait = deadline - DateTimeOffset.UtcNow + TimeSpan.FromMilliseconds(250);
            if (wait > TimeSpan.Zero) await Task.Delay(wait);
            await kernel.RestartAsync();
            WorkInspection failed = await WaitForStateAsync(kernel, workId, WorkState.Failed);
            Assert(failed.FailureCode == "DEADLINE_EXPIRED" && failed.Attempts.Count == 1 && MarkerCount(marker) == 1,
                "expired checkpoint resumed or rewrote physical evidence");
            Console.WriteLine("PASS checkpoint deadline remains MADRE authority before MAF resume");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task CancelledCheckpointDoesNotResumeAsync()
    {
        using var temp = new TempDirectory("madre-maf-cancel");
        TestEnvironment env = CreateEnvironment(temp.Path, "1");
        WriteState(env.StateFile, "available");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        KernelProcess kernel = await StartKernelAsync(env);
        try
        {
            string workId = await SubmitAsync(kernel, TwoStageRequest("MAF_STAGE_A_MARKER:" + marker));
            await WaitForFileAsync(marker);
            WriteState(env.StateFile, "unavailable");
            await RefreshAsync(kernel);
            await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            await CancelAsync(kernel, workId);
            await WaitForStateAsync(kernel, workId, WorkState.Cancelled);
            await kernel.StopAsync();
            WriteState(env.StateFile, "available");
            await kernel.RestartAsync();
            await Task.Delay(250);
            WorkInspection after = await InspectAsync(kernel, workId);
            Assert(after.State == WorkState.Cancelled && after.Attempts.Count == 1 && MarkerCount(marker) == 1,
                "cancelled checkpoint resumed subordinate execution");
            Console.WriteLine("PASS cancelled checkpoint cannot resume");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task IncompatibleStrategyDoesNotResumeAsync()
    {
        using var temp = new TempDirectory("madre-maf-strategy-version");
        TestEnvironment env = CreateEnvironment(temp.Path, "1");
        WriteState(env.StateFile, "available");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        KernelProcess kernel = await StartKernelAsync(env);
        try
        {
            string workId = await SubmitAsync(kernel, TwoStageRequest("MAF_STAGE_A_MARKER:" + marker));
            await WaitForFileAsync(marker);
            WriteState(env.StateFile, "unavailable");
            await RefreshAsync(kernel);
            await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            await kernel.StopAsync();
            await using (var connection = new SqliteConnection($"Data Source={env.Database}"))
            {
                await connection.OpenAsync();
                await using SqliteCommand command = connection.CreateCommand();
                command.CommandText = "UPDATE work SET strategy_version='v999' WHERE work_id=$id;";
                command.Parameters.AddWithValue("$id", workId);
                Assert(await command.ExecuteNonQueryAsync() == 1, "failed to prepare incompatible strategy evidence");
            }
            WriteState(env.StateFile, "available");
            await kernel.RestartAsync();
            WorkInspection failed = await WaitForStateAsync(kernel, workId, WorkState.Failed);
            Assert(failed.FailureCode == "INCOMPATIBLE_STRATEGY_VERSION" && failed.Attempts.Count == 1 && MarkerCount(marker) == 1,
                "incompatible strategy continuation was reinterpreted");
            Console.WriteLine("PASS incompatible strategy version rejected without inference replay");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task IncompatibleBindingDoesNotResumeAsync()
    {
        using var temp = new TempDirectory("madre-maf-binding-version");
        TestEnvironment env = CreateEnvironment(temp.Path, "1");
        WriteState(env.StateFile, "available");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        KernelProcess kernel = await StartKernelAsync(env);
        try
        {
            string workId = await SubmitAsync(kernel, TwoStageRequest("MAF_STAGE_A_MARKER:" + marker));
            await WaitForFileAsync(marker);
            WriteState(env.StateFile, "unavailable");
            await RefreshAsync(kernel);
            await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            await kernel.StopAsync();
            WriteConfig(env.Config, env.Database, env.StateFile, "2");
            WriteState(env.StateFile, "available");
            await kernel.RestartAsync();
            WorkInspection failed = await WaitForStateAsync(kernel, workId, WorkState.Failed);
            Assert(failed.FailureCode == "CHECKPOINT_BINDING_INCOMPATIBLE" && failed.Attempts.Count == 1 && MarkerCount(marker) == 1,
                "incompatible capability/binding continuation was executed");
            Console.WriteLine("PASS incompatible binding version rejected before MAF continuation");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task SimpleInferenceBypassesMafAsync()
    {
        using var temp = new TempDirectory("madre-maf-bypass");
        TestEnvironment env = CreateEnvironment(temp.Path, "1");
        WriteState(env.StateFile, "available");
        await using KernelProcess kernel = await StartKernelAsync(env);
        string workId = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "simple-high-normal",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed));
        WorkInspection completed = await WaitForStateAsync(kernel, workId, WorkState.Succeeded);
        Assert(completed.StrategyType == KernelContract.StrategyType
               && completed.CheckpointId is null
               && completed.Attempts.Count == 1,
            "simple physical inference was forced through MAF");
        Assert(!Directory.Exists(Path.Combine(env.Database + ".maf-checkpoints", workId)),
            "simple inference created subordinate MAF state");
        Console.WriteLine("PASS simple inference bypasses MAF completely");
    }

    private static TestEnvironment CreateEnvironment(string path, string bindingVersion)
    {
        string state = Path.Combine(path, "capability.state");
        string database = Path.Combine(path, "kernel.db");
        string config = Path.Combine(path, "kernel.json");
        WriteConfig(config, database, state, bindingVersion);
        return new TestEnvironment(config, database, state);
    }

    private static void WriteConfig(string config, string database, string stateFile, string bindingVersion)
    {
        var value = new
        {
            databasePath = database,
            maxConcurrent = 1,
            capabilities = new[]
            {
                new
                {
                    capabilityId = Capability,
                    bindingId = "process/checkpoint",
                    bindingVersion,
                    executable = _dotnet,
                    arguments = new[] { _fixtureDll, "--delay-ms", "20", "--prefix", "stage:" },
                    probeArguments = new[] { _fixtureDll, "--probe", stateFile },
                    executionBoundary = "ExternalAllowed",
                    supportedEffort = "High",
                    ownerPreference = 10
                }
            }
        };
        File.WriteAllText(config, JsonSerializer.Serialize(value, Json));
    }

    private static PhysicalInferenceRequest TwoStageRequest(string input) => new(
        input,
        InferenceEffort.High,
        WorkUrgency.Background,
        null,
        null,
        ExecutionBoundary.ExternalAllowed);

    private static async Task<string> SubmitAsync(KernelProcess kernel, PhysicalInferenceRequest request)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsJsonAsync("/v1/work", request, Json);
        response.EnsureSuccessStatusCode();
        WorkSubmissionResponse? submission = await response.Content.ReadFromJsonAsync<WorkSubmissionResponse>(Json);
        return submission?.WorkId ?? throw new InvalidOperationException("missing Work id");
    }

    private static async Task<WorkInspection> InspectAsync(KernelProcess kernel, string workId) =>
        await kernel.Client.GetFromJsonAsync<WorkInspection>($"/v1/work/{workId}/inspect", Json)
        ?? throw new InvalidOperationException("missing Work inspection");

    private static async Task<WorkInspection> WaitForStateAsync(KernelProcess kernel, string workId, WorkState state, int timeoutMs = 7000)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        WorkInspection? latest = null;
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            latest = await InspectAsync(kernel, workId);
            if (latest.State == state) return latest;
            await Task.Delay(25);
        }
        throw new InvalidOperationException($"Work {workId} did not reach {state}; latest={latest?.State}, failure={latest?.FailureCode}");
    }

    private static async Task<WorkResultSnapshot> ResultAsync(KernelProcess kernel, string workId) =>
        await kernel.Client.GetFromJsonAsync<WorkResultSnapshot>($"/v1/work/{workId}/result", Json)
        ?? throw new InvalidOperationException("missing result");

    private static async Task CancelAsync(KernelProcess kernel, string workId)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync($"/v1/work/{workId}/cancel", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task RefreshAsync(KernelProcess kernel)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync("/v1/capabilities/refresh", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task WaitForFileAsync(string path)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.Elapsed < TimeSpan.FromSeconds(3))
        {
            if (File.Exists(path)) return;
            await Task.Delay(20);
        }
        throw new InvalidOperationException("stage-A marker was not created");
    }

    private static int MarkerCount(string path) => File.Exists(path) ? File.ReadAllLines(path).Length : 0;
    private static void WriteState(string path, string state) => File.WriteAllText(path, state);

    private static async Task<KernelProcess> StartKernelAsync(TestEnvironment env)
    {
        var process = new KernelProcess(env);
        await process.StartAsync();
        return process;
    }

    private static int FreePort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        int port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    private static string FindRepositoryRoot()
    {
        DirectoryInfo? current = new(Directory.GetCurrentDirectory());
        while (current is not null)
        {
            if (File.Exists(Path.Combine(current.FullName, "NORTH_STAR.md"))) return current.FullName;
            current = current.Parent;
        }
        throw new InvalidOperationException("repository root not found");
    }

    private static JsonSerializerOptions CreateJson()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web);
        options.Converters.Add(new JsonStringEnumConverter());
        return options;
    }

    private static void Assert(bool condition, string message)
    {
        if (!condition) throw new InvalidOperationException(message);
    }

    private sealed record TestEnvironment(string Config, string Database, string StateFile);

    private sealed class KernelProcess : IAsyncDisposable
    {
        private readonly TestEnvironment _env;
        private Process? _process;
        private StringBuilder _log = new();

        public KernelProcess(TestEnvironment env)
        {
            _env = env;
            Port = FreePort();
            BaseUri = $"http://127.0.0.1:{Port}";
            Client = NewClient();
        }

        public int Port { get; private set; }
        public string BaseUri { get; private set; }
        public HttpClient Client { get; private set; }

        public async Task StartAsync()
        {
            _log = new StringBuilder();
            var psi = new ProcessStartInfo
            {
                FileName = _dotnet,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            psi.ArgumentList.Add(_hostDll);
            psi.ArgumentList.Add("--config");
            psi.ArgumentList.Add(_env.Config);
            psi.ArgumentList.Add("--port");
            psi.ArgumentList.Add(Port.ToString());
            _process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Kernel");
            _process.OutputDataReceived += (_, e) => { if (e.Data is not null) lock (_log) _log.AppendLine(e.Data); };
            _process.ErrorDataReceived += (_, e) => { if (e.Data is not null) lock (_log) _log.AppendLine(e.Data); };
            _process.BeginOutputReadLine();
            _process.BeginErrorReadLine();
            await WaitUntilHealthyAsync();
        }

        public async Task RestartAsync()
        {
            await StopAsync();
            Client.Dispose();
            Port = FreePort();
            BaseUri = $"http://127.0.0.1:{Port}";
            Client = NewClient();
            await StartAsync();
        }

        public async Task StopAsync()
        {
            if (_process is null) return;
            if (!_process.HasExited)
            {
                _process.Kill(entireProcessTree: true);
                await _process.WaitForExitAsync();
            }
            _process.Dispose();
            _process = null;
        }

        private async Task WaitUntilHealthyAsync()
        {
            Stopwatch stopwatch = Stopwatch.StartNew();
            while (stopwatch.Elapsed < TimeSpan.FromSeconds(7))
            {
                if (_process?.HasExited == true) throw new InvalidOperationException("Kernel exited early: " + Log());
                try
                {
                    using HttpResponseMessage response = await Client.GetAsync("/health");
                    if (response.IsSuccessStatusCode) return;
                }
                catch (HttpRequestException) { }
                catch (TaskCanceledException) { }
                await Task.Delay(50);
            }
            throw new InvalidOperationException("Kernel did not become healthy: " + Log());
        }

        private HttpClient NewClient() => new() { BaseAddress = new Uri(BaseUri), Timeout = TimeSpan.FromSeconds(5) };
        private string Log() { lock (_log) return _log.ToString(); }

        public async ValueTask DisposeAsync()
        {
            await StopAsync();
            Client.Dispose();
        }
    }

    private sealed class TempDirectory : IDisposable
    {
        public TempDirectory(string prefix)
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), prefix + "-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }
        public string Path { get; }
        public void Dispose()
        {
            try { Directory.Delete(Path, recursive: true); } catch (IOException) { } catch (UnauthorizedAccessException) { }
        }
    }
}
