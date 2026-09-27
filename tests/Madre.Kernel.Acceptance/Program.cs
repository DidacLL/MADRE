using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;
using Madre.Kernel.Host;
using Microsoft.Data.Sqlite;

internal static class Program
{
    private const string Slow = "slow-local";
    private const string Fast = "fast-external";
    private static readonly JsonSerializerOptions Json = CreateJson();
    private static string _root = string.Empty;
    private static string _hostDll = string.Empty;
    private static string _fixtureDll = string.Empty;
    private static string _javaCliJar = string.Empty;
    private static string _dotnet = string.Empty;

    public static async Task<int> Main()
    {
        _root = FindRepositoryRoot();
        _hostDll = Path.Combine(_root, "kernel", "src", "Madre.Kernel.Host", "bin", "Release", "net10.0", "Madre.Kernel.Host.dll");
        _fixtureDll = Path.Combine(_root, "tests", "Madre.Kernel.ProcessFixture", "bin", "Release", "net10.0", "Madre.Kernel.ProcessFixture.dll");
        _dotnet = Environment.GetEnvironmentVariable("DOTNET_HOST_PATH") ?? "dotnet";
        string[] cliJars = Directory.GetFiles(Path.Combine(_root, "madre-kernel-client", "build", "libs"), "*-cli.jar");
        Assert(cliJars.Length == 1, "exactly one Java physical-client CLI jar must be built");
        _javaCliJar = cliJars[0];
        Assert(File.Exists(_hostDll), $"actual Kernel host missing: {_hostDll}");
        Assert(File.Exists(_fixtureDll), $"process fixture missing: {_fixtureDll}");

        await CapabilityTruthAndDreAsync();
        await WorkLifecycleAsync();
        await ExternalJavaClientAndRestartAsync();
        await BindingOpennessAsync();
        Console.WriteLine("MADRE Lane C current Kernel acceptance passed");
        return 0;
    }

    private static async Task CapabilityTruthAndDreAsync()
    {
        using var temp = new TempDirectory("madre-current-dre");
        TestEnvironment env = CreateEnvironment(temp.Path);
        WriteState(env.SlowState, "unavailable");
        WriteState(env.FastState, "unavailable");

        LoadedKernelConfiguration loaded = KernelConfigurationLoader.Load(env.Config);
        var directStore = new WorkStore(Path.Combine(temp.Path, "configured-only.db"));
        await directStore.InitializeAsync(loaded.Capabilities);
        IReadOnlyList<CapabilitySnapshot> configuredOnly = await directStore.GetCapabilitiesAsync();
        Assert(configuredOnly.All(snapshot => snapshot.State.Availability == CapabilityAvailability.Unknown),
            "configuration fabricated current availability before physical probing");
        Assert(configuredOnly.All(snapshot => snapshot.State.ObservedAt is null),
            "configuration fabricated a current-state observation timestamp");

        await using KernelProcess kernel = await StartKernelAsync(env, maxConcurrent: 1);
        IReadOnlyList<CapabilitySnapshot> probed = await CapabilitiesAsync(kernel);
        Assert(probed.All(snapshot => snapshot.State.Availability == CapabilityAvailability.Unavailable),
            "actual unavailable process probes were not reflected as current state");
        Assert(probed.All(snapshot => snapshot.State.ObservedAt.HasValue),
            "current process state lacks physical observation time");
        Assert(probed.All(snapshot => snapshot.SuccessfulObservationCount == 0 && snapshot.FailureObservationCount == 0),
            "current-state probing was collapsed into historical inference observations");

        string waiting = await SubmitAsync(kernel, Request("wait-for-real-state", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await Task.Delay(150);
        Assert((await InspectAsync(kernel, waiting)).State == WorkState.Queued,
            "unavailable capability was treated as dispatchable");
        WriteState(env.SlowState, "available");
        await RefreshAsync(kernel);
        await WaitForStateAsync(kernel, waiting, WorkState.Succeeded);

        await SetOnlyAsync(kernel, env, Slow);
        string slowSeed = await SubmitAsync(kernel, Request("slow-seed", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        WorkInspection slowDone = await WaitForStateAsync(kernel, slowSeed, WorkState.Succeeded);
        Assert(slowDone.SelectedCapabilityId == Slow, "slow capability seed did not execute");

        await SetOnlyAsync(kernel, env, Fast);
        string fastSeed = await SubmitAsync(kernel, Request("fast-seed", InferenceEffort.High, WorkUrgency.Interactive, ExecutionBoundary.ExternalAllowed));
        WorkInspection fastDone = await WaitForStateAsync(kernel, fastSeed, WorkState.Succeeded);
        Assert(fastDone.SelectedCapabilityId == Fast, "fast capability seed did not execute");
        Assert(slowDone.Attempts.Single().LatencyMs > fastDone.Attempts.Single().LatencyMs + 80,
            "test did not establish distinct observed latency evidence");

        await kernel.StopAsync(hard: true);
        await kernel.RestartAsync();
        await SetBothAsync(kernel, env);
        CapabilitySnapshot slowSnapshot = (await CapabilitiesAsync(kernel)).Single(snapshot => snapshot.Capability.CapabilityId == Slow);
        CapabilitySnapshot fastSnapshot = (await CapabilitiesAsync(kernel)).Single(snapshot => snapshot.Capability.CapabilityId == Fast);
        Assert(slowSnapshot.SuccessfulLatencyMs.HasValue && fastSnapshot.SuccessfulLatencyMs.HasValue,
            "observed physical latency was not durable across Kernel restart");
        Assert(slowSnapshot.Capability.OwnerPreference > fastSnapshot.Capability.OwnerPreference,
            "test requires Owner preference to oppose observed latency");

        string evidenceChoice = await SubmitAsync(kernel, Request("evidence-choice", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.ExternalAllowed));
        Assert((await WaitForStateAsync(kernel, evidenceChoice, WorkState.Succeeded)).SelectedCapabilityId == Fast,
            "persisted physical latency did not change the later Interactive DRE choice");

        string ownerChoice = await SubmitAsync(kernel, Request("owner-choice", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed));
        Assert((await WaitForStateAsync(kernel, ownerChoice, WorkState.Succeeded)).SelectedCapabilityId == Slow,
            "Owner preference was not the normal fallback DRE rule");

        string noLocalHigh = await SubmitAsync(kernel, Request("no-local-high", InferenceEffort.High, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection rejected = await WaitForStateAsync(kernel, noLocalHigh, WorkState.Failed);
        Assert(rejected.FailureCode == "NO_ADMISSIBLE_CAPABILITY" && rejected.Attempts.Count == 0,
            "hard local/external restriction was not enforced before physical dispatch");

        await SetOnlyAsync(kernel, env, Fast);
        string nonsense = await SubmitAsync(kernel, Request("NONSENSE", InferenceEffort.High, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed));
        WorkInspection nonsenseDone = await WaitForStateAsync(kernel, nonsense, WorkState.Succeeded);
        Assert(nonsenseDone.State == WorkState.Succeeded,
            "Kernel semantically judged a physically valid result");

        await SetOnlyAsync(kernel, env, Slow);
        string failure = await SubmitAsync(kernel, Request("FAIL", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection failed = await WaitForStateAsync(kernel, failure, WorkState.Failed);
        Assert(failed.Attempts.Single().Outcome == PhysicalAttemptOutcome.DefiniteFailure
               && failed.Attempts.Single().TechnicalFailure == "PROCESS_EXIT_17",
            "process physical failure was not preserved factually");

        await AssertKnowledgeSeparationAsync(env.Database, kernel);
        Console.WriteLine("PASS configured/current/observed truth, restrictions, Owner preference and persisted-latency DRE");
    }

    private static async Task WorkLifecycleAsync()
    {
        using var temp = new TempDirectory("madre-current-lifecycle");
        TestEnvironment env = CreateEnvironment(temp.Path);
        WriteState(env.SlowState, "available");
        WriteState(env.FastState, "unavailable");
        await using KernelProcess kernel = await StartKernelAsync(env, maxConcurrent: 1);

        string blocker = await SubmitAsync(kernel, Request("SLOW:700", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, blocker, WorkState.Running);

        Stopwatch concurrent = Stopwatch.StartNew();
        Task<HttpResponseMessage>[] healthCalls = Enumerable.Range(0, 8).Select(_ => kernel.Client.GetAsync("/health")).ToArray();
        HttpResponseMessage[] health = await Task.WhenAll(healthCalls);
        concurrent.Stop();
        try
        {
            Assert(health.All(response => response.IsSuccessStatusCode) && concurrent.Elapsed < TimeSpan.FromSeconds(1),
                "one active physical caller froze unrelated local control-plane clients");
        }
        finally
        {
            foreach (HttpResponseMessage response in health) response.Dispose();
        }

        string background = await SubmitAsync(kernel, Request("background", InferenceEffort.Standard, WorkUrgency.Background, ExecutionBoundary.LocalOnly));
        string interactive = await SubmitAsync(kernel, Request("interactive", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        WorkInspection interactiveDone = await WaitForStateAsync(kernel, interactive, WorkState.Succeeded);
        WorkInspection backgroundDone = await WaitForStateAsync(kernel, background, WorkState.Succeeded);
        Assert(interactiveDone.Attempts.Single().StartedAt < backgroundDone.Attempts.Single().StartedAt,
            "urgency did not order eligible physical Work");

        DateTimeOffset now = DateTimeOffset.UtcNow;
        string future = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "future", InferenceEffort.Standard, WorkUrgency.Normal, now.AddSeconds(5), null, ExecutionBoundary.LocalOnly));
        string immediate = await SubmitAsync(kernel, Request("now", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, immediate, WorkState.Succeeded);
        Assert((await InspectAsync(kernel, future)).State == WorkState.Queued, "future Work dispatched before eligibility");
        await WaitForStateAsync(kernel, future, WorkState.Succeeded);

        now = DateTimeOffset.UtcNow;
        string expired = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "never", InferenceEffort.Standard, WorkUrgency.Normal, now.AddMilliseconds(700), now.AddMilliseconds(120), ExecutionBoundary.LocalOnly));
        WorkInspection deadline = await WaitForStateAsync(kernel, expired, WorkState.Failed);
        Assert(deadline.FailureCode == "DEADLINE_EXPIRED" && deadline.Attempts.Count == 0,
            "deadline before dispatch created physical execution");

        string queuedCancel = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "cancel-before", InferenceEffort.Standard, WorkUrgency.Normal, DateTimeOffset.UtcNow.AddSeconds(2), null, ExecutionBoundary.LocalOnly));
        await CancelAsync(kernel, queuedCancel);
        Assert((await WaitForStateAsync(kernel, queuedCancel, WorkState.Cancelled)).Attempts.Count == 0,
            "queued cancellation raced into a physical attempt");

        string runningCancel = await SubmitAsync(kernel, Request("SLOW:2000", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, runningCancel, WorkState.Running);
        await CancelAsync(kernel, runningCancel);
        WorkInspection cancelled = await WaitForStateAsync(kernel, runningCancel, WorkState.Cancelled);
        Assert(cancelled.Attempts.Single().Outcome == PhysicalAttemptOutcome.ConfirmedCancelled
               && cancelled.Attempts.Single().TechnicalFailure == "PROCESS_CANCELLED_CONFIRMED",
            "local process cancellation was not tied to confirmed physical termination");

        string retained = await SubmitAsync(kernel, Request("retained-result", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, retained, WorkState.Succeeded);
        Assert((await ResultAsync(kernel, retained)).Result == "slow:retained-result", "terminal result was not retained");
        await ReleaseAsync(kernel, retained);
        await ReleaseAsync(kernel, retained);
        using HttpResponseMessage gone = await kernel.Client.GetAsync($"/v1/work/{retained}/result");
        Assert(gone.StatusCode == HttpStatusCode.Gone, "released result remained available");
        await AssertReleasedPayloadNullAsync(env.Database, retained);

        string oversized = new('x', KernelContract.MaxPayloadBytes + 32);
        using HttpResponseMessage tooLarge = await kernel.Client.PostAsJsonAsync("/v1/work", Request(
            oversized, InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly), Json);
        Assert(tooLarge.StatusCode == HttpStatusCode.BadRequest, "oversized physical request was accepted");
        Console.WriteLine("PASS eligibility/deadline/urgency, concurrent clients, bounded concurrency, cancellation and release lifecycle");
    }

    private static async Task ExternalJavaClientAndRestartAsync()
    {
        using var temp = new TempDirectory("madre-current-client");
        TestEnvironment env = CreateEnvironment(temp.Path);
        WriteState(env.SlowState, "available");
        WriteState(env.FastState, "unavailable");
        await using KernelProcess kernel = await StartKernelAsync(env, maxConcurrent: 1);

        string callerWork = (await JavaAsync(kernel.BaseUri, "submit", "SLOW:300", "Standard", "Normal", "LocalOnly")).Trim();
        WorkInspection callerDone = await WaitForStateAsync(kernel, callerWork, WorkState.Succeeded);
        Assert(callerDone.Attempts.Count == 1, "Work did not outlive the submitting Java client process");
        string javaInspect = await JavaAsync(kernel.BaseUri, "inspect", callerWork);
        Assert(javaInspect.Contains("Succeeded", StringComparison.Ordinal), "external Java client could not inspect physical state");
        string javaResult = await JavaAsync(kernel.BaseUri, "result", callerWork);
        Assert(javaResult.Contains("slow:SLOW:300", StringComparison.Ordinal), "external Java client could not collect retained result");
        Assert((await JavaAsync(kernel.BaseUri, "release", callerWork)).Trim() == "true", "external Java client could not release retained result");

        string javaCancel = (await JavaAsync(kernel.BaseUri, "submit", "SLOW:2000", "Standard", "Normal", "LocalOnly")).Trim();
        await WaitForStateAsync(kernel, javaCancel, WorkState.Running);
        Assert((await JavaAsync(kernel.BaseUri, "cancel", javaCancel)).Trim() == "Running",
            "external Java client did not issue cancellation against active Work");
        await WaitForStateAsync(kernel, javaCancel, WorkState.Cancelled);

        string queued = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "queued-restart", InferenceEffort.Standard, WorkUrgency.Normal, DateTimeOffset.UtcNow.AddMilliseconds(800), null, ExecutionBoundary.LocalOnly));
        await kernel.StopAsync(hard: true);
        await kernel.RestartAsync();
        Assert((await WaitForStateAsync(kernel, queued, WorkState.Succeeded, 5000)).Attempts.Count == 1,
            "queued Work did not survive Kernel restart");

        string marker = Path.Combine(temp.Path, "active.marker");
        string active = await SubmitAsync(kernel, Request($"SLOW:1800|{marker}", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, active, WorkState.Running);
        await WaitForFileAsync(marker);
        await kernel.StopAsync(hard: true);
        await kernel.RestartAsync();
        WorkInspection unknown = await WaitForStateAsync(kernel, active, WorkState.UnknownCompletion);
        Assert(unknown.Attempts.Count == 1 && unknown.Attempts.Single().Outcome == PhysicalAttemptOutcome.UnknownCompletion,
            "active uncertain attempt did not recover as UNKNOWN_COMPLETION");
        await Task.Delay(1900);
        Assert(File.ReadAllLines(marker).Length == 1, "uncertain physical inference was implicitly duplicated");
        Console.WriteLine("PASS actual Java client boundary, caller disappearance, queued restart and truthful UNKNOWN_COMPLETION");
    }

    private static async Task BindingOpennessAsync()
    {
        using var temp = new TempDirectory("madre-current-binding-open");
        string db = Path.Combine(temp.Path, "kernel.db");
        var capability = new InferenceCapability(
            "owner-unusual",
            "owner/custom",
            "1",
            new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.LocalOnly, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.Low, FactProvenance.Owner),
            1);
        await using var engine = new KernelEngine(new WorkStore(db), [capability], [new OwnerCustomBinding()], 1);
        await engine.InitializeAsync();
        engine.Start();
        string id = await engine.SubmitAsync(Request("NONSENSE", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection completed = await WaitForStateAsync(engine, id, WorkState.Succeeded);
        Assert(completed.SelectedCapabilityId == "owner-unusual", "Owner/custom binding did not use the ordinary capability/DRE path");
        Assert((await engine.ResultAsync(id))?.Result == "nonsense-but-physically-valid",
            "custom binding received privileged semantic result judgment");
        Console.WriteLine("PASS provided process and Owner/custom bindings share the same Kernel binding seam");
    }

    private static async Task AssertKnowledgeSeparationAsync(string db, KernelProcess kernel)
    {
        IReadOnlyList<CapabilitySnapshot> before = await CapabilitiesAsync(kernel);
        CapabilitySnapshot slow = before.Single(snapshot => snapshot.Capability.CapabilityId == Slow);
        await using var connection = new SqliteConnection($"Data Source={db}");
        await connection.OpenAsync();
        foreach (string table in new[] { "capabilities", "capability_state", "attempts" })
        {
            await using SqliteCommand command = connection.CreateCommand();
            command.CommandText = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=$name;";
            command.Parameters.AddWithValue("$name", table);
            Assert(Convert.ToInt32(await command.ExecuteScalarAsync()) == 1, $"missing distinct durable physical truth table {table}");
        }
        Assert(slow.SuccessfulObservationCount + slow.FailureObservationCount > 0,
            "attempt history did not provide observed physical evidence");
    }

    private static async Task AssertReleasedPayloadNullAsync(string db, string workId)
    {
        await using var connection = new SqliteConnection($"Data Source={db}");
        await connection.OpenAsync();
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = "SELECT prepared_input IS NULL, result_text IS NULL, released FROM work WHERE work_id=$id;";
        command.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader reader = await command.ExecuteReaderAsync();
        Assert(await reader.ReadAsync(), "released Work identity disappeared");
        Assert(reader.GetInt32(0) == 1 && reader.GetInt32(1) == 1 && reader.GetInt32(2) == 1,
            "release did not remove retained payload/result while preserving Work identity");
    }

    private static TestEnvironment CreateEnvironment(string path)
    {
        string slowState = Path.Combine(path, "slow.state");
        string fastState = Path.Combine(path, "fast.state");
        string database = Path.Combine(path, "kernel.db");
        string config = Path.Combine(path, "kernel.json");
        WriteConfig(config, database, slowState, fastState, "1");
        return new TestEnvironment(config, database, slowState, fastState);
    }

    private static void WriteConfig(string config, string database, string slowState, string fastState, string fastBindingVersion)
    {
        var value = new
        {
            port = 5187,
            databasePath = database,
            maxConcurrent = 1,
            capabilities = new object[]
            {
                new
                {
                    capabilityId = Slow,
                    bindingId = "process/slow",
                    bindingVersion = "1",
                    executable = _dotnet,
                    arguments = new[] { _fixtureDll, "--delay-ms", "180", "--prefix", "slow:" },
                    probeArguments = new[] { _fixtureDll, "--probe", slowState },
                    executionBoundary = "LocalOnly",
                    supportedEffort = "Standard",
                    ownerPreference = 100
                },
                new
                {
                    capabilityId = Fast,
                    bindingId = "process/fast",
                    bindingVersion = fastBindingVersion,
                    executable = _dotnet,
                    arguments = new[] { _fixtureDll, "--delay-ms", "20", "--prefix", "fast:" },
                    probeArguments = new[] { _fixtureDll, "--probe", fastState },
                    executionBoundary = "ExternalAllowed",
                    supportedEffort = "High",
                    ownerPreference = 10
                }
            }
        };
        File.WriteAllText(config, JsonSerializer.Serialize(value, Json));
    }

    private static PhysicalInferenceRequest Request(string input, InferenceEffort effort, WorkUrgency urgency, ExecutionBoundary boundary) =>
        new(input, effort, urgency, null, null, boundary);

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
        return inspection ?? throw new InvalidOperationException($"missing Work {workId}");
    }

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

    private static async Task<WorkInspection> WaitForStateAsync(KernelEngine engine, string workId, WorkState state, int timeoutMs = 5000)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        WorkInspection? latest = null;
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            latest = await engine.InspectAsync(workId);
            if (latest?.State == state) return latest;
            await Task.Delay(25);
        }
        throw new InvalidOperationException($"in-process Work did not reach {state}; latest={latest?.State}");
    }

    private static async Task<WorkResultSnapshot> ResultAsync(KernelProcess kernel, string workId)
    {
        WorkResultSnapshot? result = await kernel.Client.GetFromJsonAsync<WorkResultSnapshot>($"/v1/work/{workId}/result", Json);
        return result ?? throw new InvalidOperationException("missing result");
    }

    private static async Task CancelAsync(KernelProcess kernel, string workId)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync($"/v1/work/{workId}/cancel", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task ReleaseAsync(KernelProcess kernel, string workId)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync($"/v1/work/{workId}/release", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task RefreshAsync(KernelProcess kernel)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync("/v1/capabilities/refresh", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task<IReadOnlyList<CapabilitySnapshot>> CapabilitiesAsync(KernelProcess kernel) =>
        await kernel.Client.GetFromJsonAsync<List<CapabilitySnapshot>>("/v1/capabilities", Json)
        ?? throw new InvalidOperationException("capability inspection missing");

    private static async Task SetOnlyAsync(KernelProcess kernel, TestEnvironment env, string enabled)
    {
        WriteState(env.SlowState, enabled == Slow ? "available" : "unavailable");
        WriteState(env.FastState, enabled == Fast ? "available" : "unavailable");
        await RefreshAsync(kernel);
    }

    private static async Task SetBothAsync(KernelProcess kernel, TestEnvironment env)
    {
        WriteState(env.SlowState, "available");
        WriteState(env.FastState, "available");
        await RefreshAsync(kernel);
    }

    private static void WriteState(string path, string value) => File.WriteAllText(path, value);

    private static async Task<string> JavaAsync(string baseUri, params string[] arguments)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "java",
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add("-jar");
        psi.ArgumentList.Add(_javaCliJar);
        psi.ArgumentList.Add(baseUri);
        foreach (string argument in arguments) psi.ArgumentList.Add(argument);
        using Process process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Java client");
        string stdout = await process.StandardOutput.ReadToEndAsync();
        string stderr = await process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        Assert(process.ExitCode == 0, $"Java Kernel client failed: {stderr}");
        return stdout;
    }

    private static async Task WaitForFileAsync(string path)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.Elapsed < TimeSpan.FromSeconds(3))
        {
            if (File.Exists(path)) return;
            await Task.Delay(20);
        }
        throw new InvalidOperationException($"marker not created: {path}");
    }

    private static async Task<KernelProcess> StartKernelAsync(TestEnvironment env, int maxConcurrent)
    {
        var process = new KernelProcess(env, maxConcurrent);
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

    private sealed record TestEnvironment(string Config, string Database, string SlowState, string FastState);

    private sealed class OwnerCustomBinding : IInferenceBinding
    {
        public string BindingId => "owner/custom";
        public string BindingVersion => "1";
        public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken) => Task.FromResult(CapabilityAvailability.Available);
        public Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken) =>
            Task.FromResult(BindingExecutionResult.Success("nonsense-but-physically-valid"));
    }

    private sealed class KernelProcess : IAsyncDisposable
    {
        private readonly TestEnvironment _env;
        private readonly int _maxConcurrent;
        private Process? _process;
        private StringBuilder _log = new();

        public KernelProcess(TestEnvironment env, int maxConcurrent)
        {
            _env = env;
            _maxConcurrent = maxConcurrent;
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
            psi.ArgumentList.Add("--max-concurrent");
            psi.ArgumentList.Add(_maxConcurrent.ToString());
            _process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch actual Kernel host");
            _process.OutputDataReceived += (_, e) => { if (e.Data is not null) lock (_log) _log.AppendLine(e.Data); };
            _process.ErrorDataReceived += (_, e) => { if (e.Data is not null) lock (_log) _log.AppendLine(e.Data); };
            _process.BeginOutputReadLine();
            _process.BeginErrorReadLine();
            await WaitUntilHealthyAsync();
        }

        public async Task RestartAsync()
        {
            await StopAsync(hard: true);
            Client.Dispose();
            Port = FreePort();
            BaseUri = $"http://127.0.0.1:{Port}";
            Client = NewClient();
            await StartAsync();
        }

        public async Task StopAsync(bool hard)
        {
            if (_process is null) return;
            if (!_process.HasExited)
            {
                _process.Kill(entireProcessTree: hard);
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
                if (_process?.HasExited == true) throw new InvalidOperationException($"Kernel exited early: {Log()}");
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

        private string Log()
        {
            lock (_log) return _log.ToString();
        }

        public async ValueTask DisposeAsync()
        {
            await StopAsync(hard: true);
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
