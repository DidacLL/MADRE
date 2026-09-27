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
    private const string Meai = "meai-fast";
    private const string ProcessCapability = "process-local";
    private const string Custom = "owner-custom";
    private static readonly JsonSerializerOptions Json = CreateJson();
    private static string _root = string.Empty;
    private static string _hostDll = string.Empty;
    private static string _fixtureDll = string.Empty;

    public static async Task<int> Main()
    {
        _root = FindRepositoryRoot();
        _hostDll = Path.Combine(_root, "kernel-dotnet", "validation", "Madre.Kernel.ValidationHost", "bin", "Release", "net10.0", "Madre.Kernel.ValidationHost.dll");
        _fixtureDll = Path.Combine(_root, "kernel-dotnet", "validation", "Madre.Kernel.ProcessFixture", "bin", "Release", "net10.0", "Madre.Kernel.ProcessFixture.dll");
        Assert(File.Exists(_hostDll), $"required validation host missing: {_hostDll}");
        Assert(File.Exists(_fixtureDll), $"required process fixture missing: {_fixtureDll}");

        DreChoosesConcreteStrategy();
        await CheckpointHardRestartResumeAsync();
        await CancelledCheckpointDoesNotResumeAsync();
        await IncompatibleCheckpointVersionDoesNotResumeAsync();
        await SimpleInferenceBypassesMafAsync();
        Console.WriteLine("Lane C .NET Slice 2 MAF checkpoint validation passed");
        return 0;
    }

    private static void DreChoosesConcreteStrategy()
    {
        var capability = new InferenceCapability(
            Meai,
            "meai/test",
            "1",
            new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.ExternalAllowed, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.High, FactProvenance.ProviderOrRuntime),
            10);
        var snapshot = new CapabilitySnapshot(
            capability,
            new CapabilityState(Meai, CapabilityAvailability.Available, DateTimeOffset.UtcNow),
            null,
            0,
            0);
        DreDecision decision = new DreSelector().Select(
            new PhysicalInferenceRequest(
                "strategy-selection",
                InferenceEffort.High,
                WorkUrgency.Background,
                null,
                null,
                ExecutionBoundary.ExternalAllowed),
            [snapshot]);
        Assert(decision.Kind == DreDecisionKind.Selected
               && decision.Capability?.CapabilityId == Meai
               && decision.UseCheckpointedTwoStageStrategy,
            "DRE did not choose the concrete checkpointed two-stage physical strategy");
        Console.WriteLine("PASS DRE chooses the concrete MAF-backed two-stage strategy without provider-specific routing");
    }

    private static async Task CheckpointHardRestartResumeAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-maf-restart");
        string db = Path.Combine(temp.Path, "kernel.db");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        string input = "MAF_STAGE_A_MARKER:" + marker;

        KernelProcess kernel = await StartKernelAsync(db, holdCheckpointed: true);
        try
        {
            await SetOnlyAsync(kernel, Meai);
            string workId = await SubmitAsync(kernel, TwoStageRequest(input));
            WorkInspection checkpointed = await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);

            Assert(checkpointed.StrategyType == KernelContract.CheckpointedTwoStageStrategyType
                   && checkpointed.StrategyVersion == KernelContract.CheckpointedTwoStageStrategyVersion,
                "MADRE Work did not retain the selected strategy identity/version");
            Assert(checkpointed.CheckpointSessionId == workId && !string.IsNullOrWhiteSpace(checkpointed.CheckpointId),
                "MADRE Work did not retain only the subordinate checkpoint identity/linkage");
            Assert(checkpointed.Attempts.Count == 1
                   && checkpointed.Attempts[0].Outcome == PhysicalAttemptOutcome.Succeeded
                   && checkpointed.Attempts[0].CapabilityId == Meai,
                "stage A was not retained as one truthful physical capability attempt");
            Assert(MarkerCount(marker) == 1, "stage A marker count was not exactly one at checkpoint");

            string checkpointDirectory = Path.Combine(db + ".maf-checkpoints", workId);
            Assert(File.Exists(Path.Combine(checkpointDirectory, "index.jsonl")),
                "MAF durable checkpoint store was not created");
            Assert(Directory.GetFiles(checkpointDirectory, "*.json").Length > 0,
                "MAF durable subordinate checkpoint payload is missing");

            await kernel.StopAsync(hard: true);
            await kernel.DisposeAsync();

            kernel = await StartKernelAsync(db, holdCheckpointed: false);
            WorkInspection completed = await WaitForStateAsync(kernel, workId, WorkState.Succeeded, timeoutMs: 10000);
            WorkResultSnapshot result = await ResultAsync(kernel, workId);

            Assert(completed.Attempts.Count == 2, "two-stage strategy did not retain two physical inference attempts");
            Assert(completed.Attempts.All(attempt => attempt.Outcome == PhysicalAttemptOutcome.Succeeded && attempt.CapabilityId == Meai),
                "each multi-stage inference was not recorded against the actual selected capability");
            Assert(MarkerCount(marker) == 1, "stage A was silently re-executed after hard restart");
            Assert(result.Result == "meai:meai:" + input,
                "stage B did not consume stage-A physical output as its input");

            CapabilitySnapshot meai = (await CapabilitiesAsync(kernel)).Single(snapshot => snapshot.Capability.CapabilityId == Meai);
            Assert(meai.SuccessfulObservationCount >= 2,
                "multi-stage physical attempts did not remain observable in capability history");
            Console.WriteLine("PASS durable MAF checkpoint + hard Kernel death + MADRE-authorized resume; stage A invocation count remained exactly one");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task CancelledCheckpointDoesNotResumeAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-maf-cancel");
        string db = Path.Combine(temp.Path, "kernel.db");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        string input = "MAF_STAGE_A_MARKER:" + marker;

        KernelProcess kernel = await StartKernelAsync(db, holdCheckpointed: true);
        try
        {
            await SetOnlyAsync(kernel, Meai);
            string workId = await SubmitAsync(kernel, TwoStageRequest(input));
            await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            await CancelAsync(kernel, workId);
            WorkInspection cancelled = await WaitForStateAsync(kernel, workId, WorkState.Cancelled);
            Assert(cancelled.FailureCode == "CANCELLED_WHILE_CHECKPOINTED",
                "checkpoint cancellation was not represented as MADRE Work authority");

            await kernel.StopAsync(hard: true);
            await kernel.DisposeAsync();
            kernel = await StartKernelAsync(db, holdCheckpointed: false);
            await Task.Delay(300);

            WorkInspection afterRestart = await InspectAsync(kernel, workId);
            Assert(afterRestart.State == WorkState.Cancelled && afterRestart.Attempts.Count == 1,
                "cancelled checkpointed Work was resumed after restart");
            Assert(MarkerCount(marker) == 1, "cancelled checkpoint caused stage A replay");
            Console.WriteLine("PASS MADRE cancellation of checkpointed Work prevents subordinate MAF resume");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task IncompatibleCheckpointVersionDoesNotResumeAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-maf-incompatible");
        string db = Path.Combine(temp.Path, "kernel.db");
        string marker = Path.Combine(temp.Path, "stage-a.marker");
        string input = "MAF_STAGE_A_MARKER:" + marker;

        KernelProcess kernel = await StartKernelAsync(db, holdCheckpointed: true);
        try
        {
            await SetOnlyAsync(kernel, Meai);
            string workId = await SubmitAsync(kernel, TwoStageRequest(input));
            await WaitForStateAsync(kernel, workId, WorkState.Checkpointed);
            await kernel.StopAsync(hard: true);
            await kernel.DisposeAsync();

            await using (var connection = new SqliteConnection($"Data Source={db}"))
            {
                await connection.OpenAsync();
                await using SqliteCommand command = connection.CreateCommand();
                command.CommandText = "UPDATE work SET strategy_version='v999' WHERE work_id=$id;";
                command.Parameters.AddWithValue("$id", workId);
                Assert(await command.ExecuteNonQueryAsync() == 1, "failed to prepare incompatible stored strategy version");
            }

            kernel = await StartKernelAsync(db, holdCheckpointed: false);
            WorkInspection failed = await WaitForStateAsync(kernel, workId, WorkState.Failed);
            Assert(failed.FailureCode == "INCOMPATIBLE_STRATEGY_VERSION",
                "incompatible checkpoint strategy version was silently reinterpreted");
            Assert(failed.Attempts.Count == 1 && MarkerCount(marker) == 1,
                "incompatible strategy continuation performed additional physical inference");
            Console.WriteLine("PASS incompatible stored strategy version is rejected without checkpoint reinterpretation");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task SimpleInferenceBypassesMafAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-maf-bypass");
        string db = Path.Combine(temp.Path, "kernel.db");
        await using KernelProcess kernel = await StartKernelAsync(db, holdCheckpointed: false);
        await SetOnlyAsync(kernel, Meai);

        string workId = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "simple-high-normal",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed));
        WorkInspection completed = await WaitForStateAsync(kernel, workId, WorkState.Succeeded);
        Assert(completed.StrategyType == KernelContract.StrategyType
               && completed.StrategyVersion == KernelContract.StrategyVersion
               && completed.CheckpointSessionId is null
               && completed.CheckpointId is null
               && completed.Attempts.Count == 1,
            "simple one-shot inference was routed through the MAF strategy");
        Assert(!Directory.Exists(Path.Combine(db + ".maf-checkpoints", workId)),
            "simple one-shot inference created MAF workflow state");
        Console.WriteLine("PASS simple one-shot inference still bypasses MAF completely");
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
        return submission?.WorkId ?? throw new InvalidOperationException("submission response missing Work id");
    }

    private static async Task<WorkInspection> InspectAsync(KernelProcess kernel, string workId)
    {
        WorkInspection? inspection = await kernel.Client.GetFromJsonAsync<WorkInspection>($"/v1/work/{workId}/inspect", Json);
        return inspection ?? throw new InvalidOperationException($"missing inspection for {workId}");
    }

    private static async Task<WorkInspection> WaitForStateAsync(KernelProcess kernel, string workId, WorkState state, int timeoutMs = 7000)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        WorkInspection? latest = null;
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            latest = await InspectAsync(kernel, workId);
            if (latest.State == state)
            {
                return latest;
            }
            await Task.Delay(25);
        }
        throw new InvalidOperationException($"Work {workId} did not reach {state}; latest={latest?.State}, failure={latest?.FailureCode}");
    }

    private static async Task<WorkResultSnapshot> ResultAsync(KernelProcess kernel, string workId)
    {
        WorkResultSnapshot? result = await kernel.Client.GetFromJsonAsync<WorkResultSnapshot>($"/v1/work/{workId}/result", Json);
        return result ?? throw new InvalidOperationException("missing Work result");
    }

    private static async Task CancelAsync(KernelProcess kernel, string workId)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync($"/v1/work/{workId}/cancel", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task<IReadOnlyList<CapabilitySnapshot>> CapabilitiesAsync(KernelProcess kernel) =>
        await kernel.Client.GetFromJsonAsync<List<CapabilitySnapshot>>("/v1/capabilities", Json)
        ?? throw new InvalidOperationException("capability inspection missing");

    private static async Task SetOnlyAsync(KernelProcess kernel, string enabled)
    {
        foreach (string capability in new[] { Meai, ProcessCapability, Custom })
        {
            using HttpResponseMessage response = await kernel.Client.PutAsJsonAsync(
                $"/_validation/capabilities/{capability}/state",
                new { available = capability == enabled },
                Json);
            response.EnsureSuccessStatusCode();
        }
    }

    private static int MarkerCount(string marker) => File.Exists(marker) ? File.ReadAllLines(marker).Length : 0;

    private static async Task<KernelProcess> StartKernelAsync(string db, bool holdCheckpointed)
    {
        int port = FreePort();
        var psi = DotnetProcess(_hostDll);
        psi.ArgumentList.Add("--db");
        psi.ArgumentList.Add(db);
        psi.ArgumentList.Add("--port");
        psi.ArgumentList.Add(port.ToString());
        psi.ArgumentList.Add("--fixture");
        psi.ArgumentList.Add(_fixtureDll);
        psi.ArgumentList.Add("--max-concurrent");
        psi.ArgumentList.Add("1");
        if (holdCheckpointed)
        {
            psi.ArgumentList.Add("--hold-checkpointed");
        }
        psi.RedirectStandardOutput = true;
        psi.RedirectStandardError = true;
        var process = new KernelProcess(Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Kernel"), port);
        await process.WaitUntilHealthyAsync();
        return process;
    }

    private static ProcessStartInfo DotnetProcess(string dll)
    {
        var psi = new ProcessStartInfo
        {
            FileName = Environment.GetEnvironmentVariable("DOTNET_HOST_PATH") ?? "dotnet",
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add(dll);
        return psi;
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
            if (File.Exists(Path.Combine(current.FullName, "NORTH_STAR.md")))
            {
                return current.FullName;
            }
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
        if (!condition)
        {
            throw new InvalidOperationException(message);
        }
    }

    private sealed class KernelProcess : IAsyncDisposable
    {
        private readonly Process _process;
        private readonly StringBuilder _log = new();
        private bool _stopped;

        public KernelProcess(Process process, int port)
        {
            _process = process;
            BaseUri = $"http://127.0.0.1:{port}";
            Client = new HttpClient { BaseAddress = new Uri(BaseUri), Timeout = TimeSpan.FromSeconds(4) };
            _process.OutputDataReceived += (_, eventArgs) => { if (eventArgs.Data is not null) lock (_log) _log.AppendLine(eventArgs.Data); };
            _process.ErrorDataReceived += (_, eventArgs) => { if (eventArgs.Data is not null) lock (_log) _log.AppendLine(eventArgs.Data); };
            _process.BeginOutputReadLine();
            _process.BeginErrorReadLine();
        }

        public string BaseUri { get; }
        public HttpClient Client { get; }

        public async Task WaitUntilHealthyAsync()
        {
            Stopwatch stopwatch = Stopwatch.StartNew();
            while (stopwatch.ElapsedMilliseconds < 7000)
            {
                if (_process.HasExited)
                {
                    throw new InvalidOperationException($"Kernel exited early ({_process.ExitCode}): {Log()}");
                }
                try
                {
                    using HttpResponseMessage response = await Client.GetAsync("/health");
                    if (response.IsSuccessStatusCode)
                    {
                        return;
                    }
                }
                catch (HttpRequestException)
                {
                }
                catch (TaskCanceledException)
                {
                }
                await Task.Delay(50);
            }
            throw new InvalidOperationException("Kernel did not become healthy: " + Log());
        }

        public async Task StopAsync(bool hard)
        {
            if (_stopped)
            {
                return;
            }
            _stopped = true;
            if (!_process.HasExited)
            {
                _process.Kill(entireProcessTree: hard);
                await _process.WaitForExitAsync();
            }
        }

        public async ValueTask DisposeAsync()
        {
            try
            {
                await StopAsync(hard: true);
            }
            finally
            {
                Client.Dispose();
                _process.Dispose();
            }
        }

        private string Log()
        {
            lock (_log)
            {
                return _log.ToString();
            }
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
