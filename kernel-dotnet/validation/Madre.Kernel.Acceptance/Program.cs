using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Reflection;
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
    private static string _submitterDll = string.Empty;

    public static async Task<int> Main()
    {
        _root = FindRepositoryRoot();
        _hostDll = Path.Combine(_root, "kernel-dotnet", "validation", "Madre.Kernel.ValidationHost", "bin", "Release", "net10.0", "Madre.Kernel.ValidationHost.dll");
        _fixtureDll = Path.Combine(_root, "kernel-dotnet", "validation", "Madre.Kernel.ProcessFixture", "bin", "Release", "net10.0", "Madre.Kernel.ProcessFixture.dll");
        _submitterDll = Path.Combine(_root, "kernel-dotnet", "validation", "Madre.Kernel.Submitter", "bin", "Release", "net10.0", "Madre.Kernel.Submitter.dll");
        foreach (string path in new[] { _hostDll, _fixtureDll, _submitterDll })
        {
            Assert(File.Exists(path), $"required validation binary missing: {path}");
        }

        ContractArchitectureChecks();
        await ObservationFeedbackAndBindingParityAsync();
        await SchedulingCancellationRetentionAsync();
        await CallerDisappearanceAndRestartAsync();
        Console.WriteLine("Lane C .NET architecture-validation acceptance passed");
        return 0;
    }

    private static void ContractArchitectureChecks()
    {
        string[] requestFields = typeof(PhysicalInferenceRequest).GetProperties().Select(property => property.Name).ToArray();
        AssertSet(requestFields, "PreparedInput", "RequestedEffort", "Urgency", "EligibleAt", "Deadline", "ExecutionBoundary");

        string[] capabilityFields = typeof(InferenceCapability).GetProperties().Select(property => property.Name).ToArray();
        AssertSet(capabilityFields, "CapabilityId", "BindingId", "BindingVersion", "ExecutionBoundary", "SupportedEffort", "OwnerPreference");

        Assert(typeof(DreSelector).GetFields(BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public).Length == 0,
            "DRE must not hold binding/provider implementation dependencies");
        string dreSource = File.ReadAllText(Path.Combine(_root, "kernel-dotnet", "src", "Madre.Kernel", "DreSelector.cs"));
        foreach (string forbidden in new[] { "MeaiInferenceBinding", "ProcessInferenceBinding", "OwnerCustomBinding" })
        {
            Assert(!dreSource.Contains(forbidden, StringComparison.Ordinal), $"DRE contains binding-type branch: {forbidden}");
        }
        Console.WriteLine("PASS contract minimality and no binding-type routing");
    }

    private static async Task ObservationFeedbackAndBindingParityAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-feedback");
        string db = Path.Combine(temp.Path, "kernel.db");
        await using KernelProcess kernel = await StartKernelAsync(db, maxConcurrent: 1);
        await SetOnlyAsync(kernel, ProcessCapability);

        string slow = await SubmitAsync(kernel, Request("latency-process", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        WorkInspection slowDone = await WaitForStateAsync(kernel, slow, WorkState.Succeeded);
        long slowLatency = slowDone.Attempts.Single().LatencyMs!.Value;
        Assert(slowDone.SelectedCapabilityId == ProcessCapability, "process seed did not select process capability");
        Assert((await ResultAsync(kernel, slow)).Result == "latency-process", "preparedInput was not delivered to process binding");

        await SetOnlyAsync(kernel, Meai);
        string fast = await SubmitAsync(kernel, Request("latency-meai", InferenceEffort.High, WorkUrgency.Interactive, ExecutionBoundary.ExternalAllowed));
        WorkInspection fastDone = await WaitForStateAsync(kernel, fast, WorkState.Succeeded);
        long fastLatency = fastDone.Attempts.Single().LatencyMs!.Value;
        Assert(fastDone.SelectedCapabilityId == Meai, "MEAI seed did not select MEAI capability");
        Assert((await ResultAsync(kernel, fast)).Result == "meai:latency-meai", "MEAI binding did not invoke injected IChatClient");
        Assert(slowLatency > fastLatency + 80, $"deterministic latency separation too small: process={slowLatency}, meai={fastLatency}");

        await kernel.StopAsync(hard: true);
        await using KernelProcess restarted = await StartKernelAsync(db, maxConcurrent: 1);
        await SetAvailabilityAsync(restarted, ProcessCapability, true);
        await SetAvailabilityAsync(restarted, Meai, true);
        await SetAvailabilityAsync(restarted, Custom, false);

        IReadOnlyList<CapabilitySnapshot> snapshots = await CapabilitiesAsync(restarted);
        CapabilitySnapshot process = snapshots.Single(snapshot => snapshot.Capability.CapabilityId == ProcessCapability);
        CapabilitySnapshot meai = snapshots.Single(snapshot => snapshot.Capability.CapabilityId == Meai);
        Assert(process.Capability.OwnerPreference.Value > meai.Capability.OwnerPreference.Value,
            "test requires Owner fallback preference to favor slower process capability");
        Assert(process.SuccessfulLatencyMs.HasValue && meai.SuccessfulLatencyMs.HasValue,
            "persisted latency observations missing after restart");

        string evidenceDriven = await SubmitAsync(restarted, Request("after-restart", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.ExternalAllowed));
        WorkInspection evidenceDone = await WaitForStateAsync(restarted, evidenceDriven, WorkState.Succeeded);
        Assert(evidenceDone.SelectedCapabilityId == Meai,
            "INTERACTIVE DRE did not override Owner fallback preference using persisted faster latency evidence");
        Console.WriteLine($"PASS observation feedback: persisted process={process.SuccessfulLatencyMs:F0}ms, meai={meai.SuccessfulLatencyMs:F0}ms -> selected {evidenceDone.SelectedCapabilityId}");

        await SetOnlyAsync(restarted, Custom);
        string nonsense = await SubmitAsync(restarted, Request("NONSENSE", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection nonsenseDone = await WaitForStateAsync(restarted, nonsense, WorkState.Succeeded);
        Assert(nonsenseDone.SelectedCapabilityId == Custom, "Owner custom binding did not enter the common DRE path");
        Assert((await ResultAsync(restarted, nonsense)).Result == "nonsense-but-physically-valid",
            "Kernel reinterpreted a physically valid but semantically useless result");

        await SetOnlyAsync(restarted, ProcessCapability);
        string failure = await SubmitAsync(restarted, Request("FAIL", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection failed = await WaitForStateAsync(restarted, failure, WorkState.Failed);
        Assert(failed.Attempts.Single().Outcome == PhysicalAttemptOutcome.DefiniteFailure, "process failure was not factual definite failure evidence");
        Assert(failed.Attempts.Single().TechnicalFailure == "PROCESS_EXIT_17", "process failure code missing");
        Assert(failed.Attempts.Single().LatencyMs.HasValue, "failed physical attempt did not retain latency evidence");

        await SetAvailabilityAsync(restarted, ProcessCapability, true);
        await SetAvailabilityAsync(restarted, Custom, true);
        await SetAvailabilityAsync(restarted, Meai, true);
        string inadmissible = await SubmitAsync(restarted, Request("no-local-high", InferenceEffort.High, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection noCapability = await WaitForStateAsync(restarted, inadmissible, WorkState.Failed);
        Assert(noCapability.FailureCode == "NO_ADMISSIBLE_CAPABILITY" && noCapability.Attempts.Count == 0,
            "no-admissible capability distinction failed");

        await SetAvailabilityAsync(restarted, Meai, false);
        string unavailable = await SubmitAsync(restarted, new PhysicalInferenceRequest(
            "wait-for-meai", InferenceEffort.High, WorkUrgency.Interactive, null, DateTimeOffset.UtcNow.AddSeconds(3), ExecutionBoundary.ExternalAllowed));
        await Task.Delay(200);
        WorkInspection waiting = await InspectAsync(restarted, unavailable);
        Assert(waiting.State == WorkState.Queued && waiting.Attempts.Count == 0,
            "admissible but unavailable capability should leave Work pending");
        string bypass = await SubmitAsync(restarted, Request("availability-bypass", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        Assert((await WaitForStateAsync(restarted, bypass, WorkState.Succeeded)).SelectedCapabilityId == ProcessCapability,
            "unavailable Work head-of-line blocked an executable Work item");
        await SetAvailabilityAsync(restarted, Meai, true);
        await WaitForStateAsync(restarted, unavailable, WorkState.Succeeded);

        await AssertKnowledgeSeparationAsync(db, restarted);
        Console.WriteLine("PASS MEAI/process/Owner-custom parity, physical-success semantics, failure evidence, and capability knowledge separation");
    }

    private static async Task SchedulingCancellationRetentionAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-scheduling");
        string db = Path.Combine(temp.Path, "kernel.db");
        await using KernelProcess kernel = await StartKernelAsync(db, maxConcurrent: 1);
        await SetOnlyAsync(kernel, ProcessCapability);

        string blocker = await SubmitAsync(kernel, Request("SLOW:700", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, blocker, WorkState.Running);
        string background = await SubmitAsync(kernel, Request("background", InferenceEffort.Standard, WorkUrgency.Background, ExecutionBoundary.LocalOnly));
        string interactive = await SubmitAsync(kernel, Request("interactive", InferenceEffort.Standard, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        WorkInspection interactiveDone = await WaitForStateAsync(kernel, interactive, WorkState.Succeeded);
        WorkInspection backgroundDone = await WaitForStateAsync(kernel, background, WorkState.Succeeded);
        Assert(interactiveDone.Attempts.Single().StartedAt < backgroundDone.Attempts.Single().StartedAt,
            "higher urgency was not honored among eligible Work");

        DateTimeOffset now = DateTimeOffset.UtcNow;
        string delayed = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "future", InferenceEffort.Standard, WorkUrgency.Normal, now.AddMilliseconds(750), null, ExecutionBoundary.LocalOnly));
        string immediate = await SubmitAsync(kernel, Request("now", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, immediate, WorkState.Succeeded);
        Assert((await InspectAsync(kernel, delayed)).State == WorkState.Queued, "future eligibility head-of-line blocked or dispatched early");
        await WaitForStateAsync(kernel, delayed, WorkState.Succeeded);

        now = DateTimeOffset.UtcNow;
        string expired = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "never", InferenceEffort.Standard, WorkUrgency.Normal, now.AddMilliseconds(700), now.AddMilliseconds(150), ExecutionBoundary.LocalOnly));
        WorkInspection expiredDone = await WaitForStateAsync(kernel, expired, WorkState.Failed);
        Assert(expiredDone.FailureCode == "DEADLINE_EXPIRED" && expiredDone.Attempts.Count == 0,
            "deadline before dispatch must create no attempt");

        string queuedCancel = await SubmitAsync(kernel, new PhysicalInferenceRequest(
            "cancel-before", InferenceEffort.Standard, WorkUrgency.Normal, DateTimeOffset.UtcNow.AddSeconds(2), null, ExecutionBoundary.LocalOnly));
        await CancelAsync(kernel, queuedCancel);
        WorkInspection queuedCancelled = await WaitForStateAsync(kernel, queuedCancel, WorkState.Cancelled);
        Assert(queuedCancelled.Attempts.Count == 0, "queued cancellation created a physical attempt");

        string runningCancel = await SubmitAsync(kernel, Request("SLOW:2000", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, runningCancel, WorkState.Running);
        await CancelAsync(kernel, runningCancel);
        WorkInspection processCancelled = await WaitForStateAsync(kernel, runningCancel, WorkState.Cancelled);
        Assert(processCancelled.Attempts.Single().Outcome == PhysicalAttemptOutcome.ConfirmedCancelled,
            "process binding did not preserve confirmed local cancellation truth");

        await SetOnlyAsync(kernel, Meai);
        string uncertainCancel = await SubmitAsync(kernel, Request("MEAI_SLOW", InferenceEffort.High, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed));
        await WaitForStateAsync(kernel, uncertainCancel, WorkState.Running);
        await CancelAsync(kernel, uncertainCancel);
        WorkInspection uncertain = await WaitForStateAsync(kernel, uncertainCancel, WorkState.UnknownCompletion);
        Assert(uncertain.Attempts.Single().Outcome == PhysicalAttemptOutcome.UnknownCompletion,
            "MEAI cancellation incorrectly claimed confirmed remote cancellation");

        await SetOnlyAsync(kernel, ProcessCapability);
        string retained = await SubmitAsync(kernel, Request("retained-result", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitForStateAsync(kernel, retained, WorkState.Succeeded);
        Assert((await ResultAsync(kernel, retained)).Result == "retained-result", "result not retained");
        await ReleaseAsync(kernel, retained);
        await ReleaseAsync(kernel, retained);
        using HttpResponseMessage afterRelease = await kernel.Client.GetAsync($"/v1/work/{retained}/result");
        Assert(afterRelease.StatusCode == HttpStatusCode.Gone, "released result remained externally available");
        await AssertReleasedPayloadNullAsync(db, retained);

        string tooLarge = new('x', KernelContract.MaxPayloadBytes + 16);
        using HttpResponseMessage oversized = await kernel.Client.PostAsJsonAsync(
            "/v1/work",
            Request(tooLarge, InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly),
            Json);
        Assert(oversized.StatusCode == HttpStatusCode.BadRequest, "oversized retained input was accepted");
        Console.WriteLine("PASS eligibility/deadline/urgency, bounded concurrency substrate, cancellation truth, and retention lifecycle");
    }

    private static async Task CallerDisappearanceAndRestartAsync()
    {
        using var temp = new TempDirectory("madre-dotnet-restart");
        string db = Path.Combine(temp.Path, "kernel.db");
        KernelProcess kernel = await StartKernelAsync(db, maxConcurrent: 1);
        try
        {
            await SetOnlyAsync(kernel, ProcessCapability);
            string callerWork = await RunSubmitterAsync(kernel.BaseUri, "SLOW:500");
            WorkInspection callerDone = await WaitForStateAsync(kernel, callerWork, WorkState.Succeeded);
            Assert(callerDone.Attempts.Count == 1, "caller-disappearance Work did not execute independently");
            using var anotherCaller = new HttpClient { BaseAddress = new Uri(kernel.BaseUri) };
            WorkResultSnapshot? recovered = await anotherCaller.GetFromJsonAsync<WorkResultSnapshot>($"/v1/work/{callerWork}/result", Json);
            Assert(recovered?.Result == "SLOW:500", "another caller could not retrieve retained result");

            string queued = await SubmitAsync(kernel, new PhysicalInferenceRequest(
                "queued-restart", InferenceEffort.Standard, WorkUrgency.Normal, DateTimeOffset.UtcNow.AddMilliseconds(1000), null, ExecutionBoundary.LocalOnly));
            await kernel.StopAsync(hard: true);
            await kernel.DisposeAsync();
            kernel = await StartKernelAsync(db, maxConcurrent: 1);
            WorkInspection queuedDone = await WaitForStateAsync(kernel, queued, WorkState.Succeeded, timeoutMs: 5000);
            Assert(queuedDone.Attempts.Count == 1, "future-eligible Work did not survive restart authoritatively");

            string marker = Path.Combine(temp.Path, "active.marker");
            string active = await SubmitAsync(kernel, Request($"SLOW:2200|{marker}", InferenceEffort.Standard, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            await WaitForStateAsync(kernel, active, WorkState.Running);
            await WaitForFileAsync(marker);
            await kernel.StopAsync(hard: true);
            await kernel.DisposeAsync();
            kernel = await StartKernelAsync(db, maxConcurrent: 1);
            WorkInspection unknown = await WaitForStateAsync(kernel, active, WorkState.UnknownCompletion);
            Assert(unknown.Attempts.Count == 1 && unknown.Attempts.Single().Outcome == PhysicalAttemptOutcome.UnknownCompletion,
                "interrupted active attempt did not become truthful UNKNOWN_COMPLETION");
            await Task.Delay(2400);
            int invocations = File.ReadAllLines(marker).Length;
            Assert(invocations == 1, $"interrupted physical action was silently repeated: marker count={invocations}");
            Console.WriteLine("PASS caller disappearance, queued restart, active UNKNOWN_COMPLETION, and no implicit duplicate inference");
        }
        finally
        {
            await kernel.DisposeAsync();
        }
    }

    private static async Task AssertKnowledgeSeparationAsync(string db, KernelProcess kernel)
    {
        IReadOnlyList<CapabilitySnapshot> before = await CapabilitiesAsync(kernel);
        CapabilitySnapshot configured = before.Single(snapshot => snapshot.Capability.CapabilityId == ProcessCapability);
        int preference = configured.Capability.OwnerPreference.Value;
        FactProvenance preferenceSource = configured.Capability.OwnerPreference.Provenance;
        int observations = configured.SuccessfulObservationCount + configured.FailureObservationCount;
        await SetAvailabilityAsync(kernel, ProcessCapability, false);
        CapabilitySnapshot after = (await CapabilitiesAsync(kernel)).Single(snapshot => snapshot.Capability.CapabilityId == ProcessCapability);
        Assert(after.Capability.OwnerPreference.Value == preference && after.Capability.OwnerPreference.Provenance == preferenceSource,
            "current state mutation overwrote configured evidence");
        Assert(after.SuccessfulObservationCount + after.FailureObservationCount == observations,
            "current state mutation overwrote historical observations");

        await using var connection = new SqliteConnection($"Data Source={db}");
        await connection.OpenAsync();
        foreach (string table in new[] { "capabilities", "capability_state", "attempts" })
        {
            await using SqliteCommand command = connection.CreateCommand();
            command.CommandText = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=$name;";
            command.Parameters.AddWithValue("$name", table);
            Assert(Convert.ToInt32(await command.ExecuteScalarAsync()) == 1, $"missing distinct durable evidence table {table}");
        }
        await SetAvailabilityAsync(kernel, ProcessCapability, true);
    }

    private static async Task AssertReleasedPayloadNullAsync(string db, string workId)
    {
        await using var connection = new SqliteConnection($"Data Source={db}");
        await connection.OpenAsync();
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = "SELECT prepared_input IS NULL, result_text IS NULL, released FROM work WHERE work_id=$id;";
        command.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader reader = await command.ExecuteReaderAsync();
        Assert(await reader.ReadAsync(), "released Work missing from SQLite");
        Assert(reader.GetInt32(0) == 1 && reader.GetInt32(1) == 1 && reader.GetInt32(2) == 1,
            "release did not remove retained input/result while preserving Work identity");
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
        return inspection ?? throw new InvalidOperationException($"missing inspection for {workId}");
    }

    private static async Task<WorkInspection> WaitForStateAsync(KernelProcess kernel, string workId, WorkState state, int timeoutMs = 6000)
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
        throw new InvalidOperationException($"Work {workId} did not reach {state}; latest={latest?.State}");
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

    private static async Task ReleaseAsync(KernelProcess kernel, string workId)
    {
        using HttpResponseMessage response = await kernel.Client.PostAsync($"/v1/work/{workId}/release", null);
        response.EnsureSuccessStatusCode();
    }

    private static async Task<IReadOnlyList<CapabilitySnapshot>> CapabilitiesAsync(KernelProcess kernel) =>
        await kernel.Client.GetFromJsonAsync<List<CapabilitySnapshot>>("/v1/capabilities", Json)
        ?? throw new InvalidOperationException("capability inspection missing");

    private static async Task SetOnlyAsync(KernelProcess kernel, string enabled)
    {
        foreach (string capability in new[] { Meai, ProcessCapability, Custom })
        {
            await SetAvailabilityAsync(kernel, capability, capability == enabled);
        }
    }

    private static async Task SetAvailabilityAsync(KernelProcess kernel, string capability, bool available)
    {
        using HttpResponseMessage response = await kernel.Client.PutAsJsonAsync($"/v1/capabilities/{capability}/state", new { available }, Json);
        response.EnsureSuccessStatusCode();
    }

    private static async Task<string> RunSubmitterAsync(string baseUri, string input)
    {
        var psi = DotnetProcess(_submitterDll);
        psi.ArgumentList.Add(baseUri);
        psi.ArgumentList.Add(input);
        psi.RedirectStandardOutput = true;
        psi.RedirectStandardError = true;
        using Process process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch submitter");
        string stdout = await process.StandardOutput.ReadToEndAsync();
        string stderr = await process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        Assert(process.ExitCode == 0, $"submitter failed: {stderr}");
        return stdout.Trim();
    }

    private static async Task WaitForFileAsync(string path)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.ElapsedMilliseconds < 3000)
        {
            if (File.Exists(path))
            {
                return;
            }
            await Task.Delay(20);
        }
        throw new InvalidOperationException($"marker not created: {path}");
    }

    private static async Task<KernelProcess> StartKernelAsync(string db, int maxConcurrent)
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
        psi.ArgumentList.Add(maxConcurrent.ToString());
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

    private static void AssertSet(IEnumerable<string> actual, params string[] expected)
    {
        string[] actualSorted = actual.Order(StringComparer.Ordinal).ToArray();
        string[] expectedSorted = expected.Order(StringComparer.Ordinal).ToArray();
        Assert(actualSorted.SequenceEqual(expectedSorted),
            $"contract fields differ. actual=[{string.Join(',', actualSorted)}], expected=[{string.Join(',', expectedSorted)}]");
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
            Port = port;
            BaseUri = $"http://127.0.0.1:{port}";
            Client = new HttpClient { BaseAddress = new Uri(BaseUri), Timeout = TimeSpan.FromSeconds(3) };
            _process.OutputDataReceived += (_, eventArgs) => { if (eventArgs.Data is not null) lock (_log) _log.AppendLine(eventArgs.Data); };
            _process.ErrorDataReceived += (_, eventArgs) => { if (eventArgs.Data is not null) lock (_log) _log.AppendLine(eventArgs.Data); };
            _process.BeginOutputReadLine();
            _process.BeginErrorReadLine();
        }

        public int Port { get; }
        public string BaseUri { get; }
        public HttpClient Client { get; }

        public async Task WaitUntilHealthyAsync()
        {
            Stopwatch stopwatch = Stopwatch.StartNew();
            while (stopwatch.ElapsedMilliseconds < 6000)
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
