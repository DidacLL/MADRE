using System.Text;
using Madre.Kernel;
using Microsoft.Extensions.AI;

internal static partial class Program
{
    private static async Task RunRegressionVerificationAsync(VerificationOptions options)
    {
        _ = options;
        await RequestBoundaryVerificationAsync();
        await DreMatrixVerificationAsync();
        await WorkLifecycleVerificationAsync();
        await DeterministicRaceVerificationAsync();
        await BindingExecutorVerificationAsync();
        await MeaiVerificationAsync();
        Console.WriteLine("PASS deterministic physical regression verification");
    }

    private static async Task RequestBoundaryVerificationAsync()
    {
        using var temp = new TempDir("madre-verify-boundary");
        DateTimeOffset now = new(2026, 9, 28, 0, 0, 0, TimeSpan.Zero);
        var clock = new ManualKernelClock(now);
        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "boundary.db")),
            [],
            [],
            1,
            clock);
        await engine.InitializeAsync();

        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            string.Empty, InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly)));
        await AssertAcceptedQueuedAsync(engine, new PhysicalInferenceRequest(
            "x", InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly));
        await AssertAcceptedQueuedAsync(engine, new PhysicalInferenceRequest(
            new string('a', KernelProtocol.MaxPayloadBytes - 1), InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly));
        await AssertAcceptedQueuedAsync(engine, new PhysicalInferenceRequest(
            new string('a', KernelProtocol.MaxPayloadBytes), InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly));
        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            new string('a', KernelProtocol.MaxPayloadBytes + 1), InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly)));

        string exactMultibyte = new('é', KernelProtocol.MaxPayloadBytes / 2);
        Check(Encoding.UTF8.GetByteCount(exactMultibyte) == KernelProtocol.MaxPayloadBytes,
            "test fixture did not create exact UTF-8 request boundary");
        await AssertAcceptedQueuedAsync(engine, new PhysicalInferenceRequest(
            exactMultibyte, InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly));
        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            exactMultibyte + "x", InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly)));

        foreach (string input in new[]
        {
            "a\0b",
            "line1\nline2\r\t\u0001",
            "emoji-😀-astral-𐐷"
        })
        {
            await AssertAcceptedQueuedAsync(engine, new PhysicalInferenceRequest(
                input, InferenceEffort.Low, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly));
        }

        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            "bad-effort", (InferenceEffort)999, WorkUrgency.Normal, null, null, ExecutionBoundary.LocalOnly)));
        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            "bad-urgency", InferenceEffort.Low, (WorkUrgency)999, null, null, ExecutionBoundary.LocalOnly)));
        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            "bad-boundary", InferenceEffort.Low, WorkUrgency.Normal, null, null, (ExecutionBoundary)999)));

        foreach (DateTimeOffset eligible in new[]
        {
            now.AddMinutes(-1),
            now,
            now.AddMinutes(1),
            now.AddYears(20)
        })
        {
            string id = await engine.SubmitAsync(new PhysicalInferenceRequest(
                "eligible-" + eligible.ToUnixTimeMilliseconds(),
                InferenceEffort.Low,
                WorkUrgency.Normal,
                eligible,
                null,
                ExecutionBoundary.LocalOnly));
            WorkInspection inspection = (await engine.InspectAsync(id))!;
            Check(inspection.EligibleAt == eligible, "eligibility boundary changed during persistence");
        }

        string elapsedDeadline = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "elapsed-deadline",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            null,
            now.AddSeconds(-1),
            ExecutionBoundary.LocalOnly));
        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            "equal-deadline",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            now.AddMinutes(2),
            now.AddMinutes(2),
            ExecutionBoundary.LocalOnly)));
        await AssertRejectAsync(() => engine.SubmitAsync(new PhysicalInferenceRequest(
            "before-deadline",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            now.AddMinutes(3),
            now.AddMinutes(2),
            ExecutionBoundary.LocalOnly)));
        await AssertAcceptedQueuedAsync(engine, new PhysicalInferenceRequest(
            "distant-deadline",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            null,
            now.AddYears(20),
            ExecutionBoundary.LocalOnly));

        engine.Start();
        WorkInspection elapsed = await WaitStateAsync(engine, elapsedDeadline, WorkState.Failed);
        Check(elapsed.Failure?.Kind == PhysicalFailureKind.DeadlineExpired && elapsed.Attempts.Count == 0,
            "already-elapsed deadline created physical execution");

        string zeroCapability = await engine.SubmitAsync(Req(
            "zero-capabilities",
            InferenceEffort.Low,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        WorkInspection impossible = await WaitStateAsync(engine, zeroCapability, WorkState.Failed);
        Check(impossible.Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability
            && impossible.Attempts.Count == 0,
            "zero-capability Kernel fabricated physical availability");

        Console.WriteLine("PASS physical request boundaries including UTF-8 byte limits and deterministic time semantics");
    }

    private static async Task AssertAcceptedQueuedAsync(KernelEngine engine, PhysicalInferenceRequest request)
    {
        string id = await engine.SubmitAsync(request);
        WorkInspection? inspection = await engine.InspectAsync(id);
        Check(inspection?.State == WorkState.Queued, "accepted physical request was not durably queued");
    }

    private static async Task AssertRejectAsync(Func<Task> action)
    {
        bool rejected = false;
        try
        {
            await action().ConfigureAwait(false);
        }
        catch (ArgumentException)
        {
            rejected = true;
        }
        Check(rejected, "invalid physical request was accepted");
    }

    private static async Task DreMatrixVerificationAsync()
    {
        var selector = new DreSelector();
        var request = Req("dre", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.ExternalAllowed);

        foreach (InferenceEffort requested in Enum.GetValues<InferenceEffort>())
        {
            foreach (InferenceEffort supported in Enum.GetValues<InferenceEffort>())
            {
                InferenceCapability capability = Capability(
                    $"effort-{requested}-{supported}",
                    "controlled/effort",
                    "1",
                    supported,
                    ExecutionBoundary.LocalOnly,
                    1);
                DreDecision decision = selector.Select(
                    request with { RequestedEffort = requested },
                    [Snapshot(capability, CapabilityAvailability.Available)]);
                bool expected = requested switch
                {
                    InferenceEffort.Low => true,
                    InferenceEffort.Standard => supported is InferenceEffort.Standard or InferenceEffort.High,
                    InferenceEffort.High => supported == InferenceEffort.High,
                    _ => false
                };
                Check((decision.Kind == DreDecisionKind.Selected) == expected,
                    $"effort compatibility mismatch requested={requested} supported={supported}");
            }
        }

        InferenceCapability local = Capability("local", "controlled/local", "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 10);
        InferenceCapability external = Capability("external", "controlled/external", "1", InferenceEffort.High, ExecutionBoundary.ExternalAllowed, 1000);
        Check(selector.Select(request with { ExecutionBoundary = ExecutionBoundary.LocalOnly }, [Snapshot(local, CapabilityAvailability.Available)]).Kind == DreDecisionKind.Selected,
            "LocalOnly rejected local capability");
        Check(selector.Select(request with { ExecutionBoundary = ExecutionBoundary.LocalOnly }, [Snapshot(external, CapabilityAvailability.Available)]).Kind == DreDecisionKind.NoAdmissibleCapability,
            "LocalOnly admitted external-only capability");
        Check(selector.Select(request, [Snapshot(local, CapabilityAvailability.Available)]).Kind == DreDecisionKind.Selected,
            "ExternalAllowed rejected local capability");
        Check(selector.Select(request, [Snapshot(external, CapabilityAvailability.Available)]).Kind == DreDecisionKind.Selected,
            "ExternalAllowed rejected external capability");
        Check(selector.Select(
            request with { RequestedEffort = InferenceEffort.High, ExecutionBoundary = ExecutionBoundary.LocalOnly },
            [Snapshot(external, CapabilityAvailability.Available), Snapshot(local, CapabilityAvailability.Available)])
            .Capability?.CapabilityId == local.CapabilityId,
            "inadmissible higher-preference capability won DRE selection");

        InferenceCapability preferred = Capability("preferred", "controlled/preferred", "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 100);
        InferenceCapability alternate = Capability("alternate", "controlled/alternate", "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 10);
        AssertSelected(selector, request, preferred, Snapshot(preferred, CapabilityAvailability.Available), Snapshot(alternate, CapabilityAvailability.Unknown));
        AssertSelected(selector, request, preferred, Snapshot(preferred, CapabilityAvailability.Available), Snapshot(alternate, CapabilityAvailability.Unavailable));
        AssertSelected(selector, request, alternate, Snapshot(preferred, CapabilityAvailability.Unavailable), Snapshot(alternate, CapabilityAvailability.Unknown));
        AssertSelected(selector, request, preferred, Snapshot(preferred, CapabilityAvailability.Available), Snapshot(alternate, CapabilityAvailability.Available));
        AssertSelected(selector, request, preferred, Snapshot(preferred, CapabilityAvailability.Unknown), Snapshot(alternate, CapabilityAvailability.Unknown));
        Check(selector.Select(request, [Snapshot(preferred, CapabilityAvailability.Unavailable), Snapshot(alternate, CapabilityAvailability.Unavailable)]).Kind == DreDecisionKind.WaitForAvailability,
            "all known-unavailable capabilities did not wait for observation");
        Check(selector.Select(request with { RequestedEffort = InferenceEffort.High },
            [Snapshot(Capability("low-only", "controlled/low", "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 999), CapabilityAvailability.Available)]).Kind == DreDecisionKind.NoAdmissibleCapability,
            "no-admissible case invented compatibility");

        foreach (WorkUrgency urgency in new[] { WorkUrgency.Background, WorkUrgency.Normal })
        {
            AssertSelected(
                selector,
                request with { Urgency = urgency },
                preferred,
                Snapshot(preferred, CapabilityAvailability.Available, 900),
                Snapshot(alternate, CapabilityAvailability.Available, 10));
        }

        AssertSelected(
            selector,
            request with { Urgency = WorkUrgency.Interactive },
            alternate,
            Snapshot(preferred, CapabilityAvailability.Available, 900),
            Snapshot(alternate, CapabilityAvailability.Available, 10));
        AssertSelected(
            selector,
            request with { Urgency = WorkUrgency.Interactive },
            preferred,
            Snapshot(preferred, CapabilityAvailability.Available, null),
            Snapshot(alternate, CapabilityAvailability.Available, 10));

        var tied = new[]
        {
            Capability("tie-a", "controlled/tie-a", "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 50),
            Capability("tie-b", "controlled/tie-b", "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 50),
            Capability("tie-c", "controlled/tie-c", "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 50)
        };
        string? deterministic = null;
        foreach (IReadOnlyList<InferenceCapability> permutation in Permutations(tied))
        {
            DreDecision decision = selector.Select(
                request with { Urgency = WorkUrgency.Interactive },
                permutation.Select(capability => Snapshot(capability, CapabilityAvailability.Available, 25)).ToArray());
            deterministic ??= decision.Capability?.CapabilityId;
            Check(decision.Capability?.CapabilityId == deterministic,
                "equal-evidence DRE selection changed with candidate input order");
        }

        await VerifyLatencyEvidenceProvenanceAsync();
        Console.WriteLine("PASS exhaustive DRE effort/boundary/state/evidence matrix and deterministic ties");
    }

    private static void AssertSelected(
        DreSelector selector,
        PhysicalInferenceRequest request,
        InferenceCapability expected,
        params CapabilitySnapshot[] snapshots)
    {
        DreDecision decision = selector.Select(request, snapshots);
        Check(decision.Kind == DreDecisionKind.Selected && decision.Capability?.CapabilityId == expected.CapabilityId,
            $"DRE selected {decision.Capability?.CapabilityId ?? decision.Kind.ToString()} instead of {expected.CapabilityId}");
    }

    private static async Task VerifyLatencyEvidenceProvenanceAsync()
    {
        using var temp = new TempDir("madre-verify-latency");
        string db = Path.Combine(temp.Path, "latency.db");
        var bindingV1 = new ControlledBinding("controlled/versioned", "1")
        {
            ExecuteHandler = (request, _) => Task.FromResult(request.PreparedInput switch
            {
                "failed" => BindingExecutionResult.Fail(PhysicalFailureKind.IoFailure),
                "cancelled" => BindingExecutionResult.Cancelled(),
                "unknown" => BindingExecutionResult.Unknown(),
                _ => BindingExecutionResult.Success(request.PreparedInput)
            })
        };
        InferenceCapability v1 = Capability("versioned", bindingV1.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(new WorkStore(db), [v1], [bindingV1], 1))
        {
            await engine.InitializeAsync();
            engine.Start();
            foreach (string input in new[] { "failed", "cancelled", "unknown" })
            {
                string id = await engine.SubmitAsync(Req(input, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
                _ = await WaitTerminalAsync(engine, id);
                CapabilitySnapshot snapshot = (await engine.CapabilitiesAsync()).Single();
                Check(snapshot.SuccessfulLatencyMs is null,
                    $"{input} attempt became successful-latency evidence");
            }
            string success = await engine.SubmitAsync(Req("success", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            Check((await WaitTerminalAsync(engine, success)).State == WorkState.Succeeded, "successful latency seed failed");
            Check((await engine.CapabilitiesAsync()).Single().SuccessfulLatencyMs.HasValue,
                "successful attempt did not become latency evidence");
        }

        var bindingV2 = new ControlledBinding("controlled/versioned", "2");
        InferenceCapability v2 = Capability("versioned", bindingV2.BindingId, "2", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var restarted = new KernelEngine(new WorkStore(db), [v2], [bindingV2], 1);
        await restarted.InitializeAsync();
        CapabilitySnapshot current = (await restarted.CapabilitiesAsync()).Single();
        Check(current.SuccessfulLatencyMs is null,
            "binding-version change reused stale successful-latency evidence");
    }

    private static async Task WorkLifecycleVerificationAsync()
    {
        using var temp = new TempDir("madre-verify-lifecycle");
        var started = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var runningCompletion = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var binding = new ControlledBinding("controlled/lifecycle", "1")
        {
            ExecuteHandler = async (request, cancellationToken) =>
            {
                switch (request.PreparedInput)
                {
                    case "running":
                        started.TrySetResult(true);
                        using (cancellationToken.Register(() => runningCompletion.TrySetResult(BindingExecutionResult.Cancelled("controlled cancellation"))))
                        {
                            return await runningCompletion.Task.ConfigureAwait(false);
                        }
                    case "failed":
                        return BindingExecutionResult.Fail(PhysicalFailureKind.IoFailure, "controlled failure");
                    case "unknown":
                        return BindingExecutionResult.Unknown("controlled uncertainty");
                    default:
                        return BindingExecutionResult.Success("result:" + request.PreparedInput);
                }
            }
        };
        InferenceCapability cap = Capability("life", binding.BindingId, "1", InferenceEffort.High, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "life.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        DateTimeOffset future = DateTimeOffset.UtcNow.AddMinutes(10);
        string queued = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "queued", InferenceEffort.Low, WorkUrgency.Normal, future, null, ExecutionBoundary.LocalOnly));
        WorkInspection queuedInspection = (await engine.InspectAsync(queued))!;
        Check(queuedInspection.State == WorkState.Queued && queuedInspection.Attempts.Count == 0, "queued lifecycle setup failed");
        WorkResultSnapshot queuedResult = (await engine.ResultAsync(queued))!;
        Check(queuedResult.State == WorkState.Queued && queuedResult.Result is null, "Result fabricated nonterminal result");
        Check(await engine.ReleaseAsync(queued) == false, "release before terminal unexpectedly succeeded");
        Check((await engine.InspectAsync(queued))?.State == WorkState.Queued, "failed release altered queued Work");

        WorkState?[] queuedCancels = await Task.WhenAll(
            Enumerable.Range(0, 8).Select(_ => engine.CancelAsync(queued)));
        Check(queuedCancels.All(state => state == WorkState.Cancelled), "duplicate queued cancellation was not idempotent");
        WorkInspection cancelledQueued = (await engine.InspectAsync(queued))!;
        Check(cancelledQueued.State == WorkState.Cancelled && cancelledQueued.Attempts.Count == 0,
            "queued cancellation created physical attempt");

        string running = await engine.SubmitAsync(Req("running", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await started.Task.WaitAsync(TimeSpan.FromSeconds(5));
        Check((await engine.InspectAsync(running))?.State == WorkState.Running, "running lifecycle setup failed");
        Check(await engine.ReleaseAsync(running) == false, "running Work was released");
        WorkState?[] runningCancels = await Task.WhenAll(
            Enumerable.Range(0, 8).Select(_ => engine.CancelAsync(running)));
        Check(runningCancels.All(state => state is WorkState.Running or WorkState.Cancelled),
            "concurrent running cancellation produced impossible state");
        WorkInspection cancelledRunning = await WaitStateAsync(engine, running, WorkState.Cancelled);
        Check(cancelledRunning.Attempts.Count == 1
            && cancelledRunning.Attempts.Single().Outcome == PhysicalAttemptOutcome.ConfirmedCancelled,
            "running cancellation did not preserve one confirmed physical attempt");

        string succeeded = await engine.SubmitAsync(Req("succeeded", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        string failed = await engine.SubmitAsync(Req("failed", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        string unknown = await engine.SubmitAsync(Req("unknown", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        Check((await WaitTerminalAsync(engine, succeeded)).State == WorkState.Succeeded, "success state setup failed");
        Check((await WaitTerminalAsync(engine, failed)).State == WorkState.Failed, "failure state setup failed");
        Check((await WaitTerminalAsync(engine, unknown)).State == WorkState.UnknownCompletion, "unknown state setup failed");

        foreach ((string id, WorkState expected) in new[]
        {
            (queued, WorkState.Cancelled),
            (running, WorkState.Cancelled),
            (succeeded, WorkState.Succeeded),
            (failed, WorkState.Failed),
            (unknown, WorkState.UnknownCompletion)
        })
        {
            WorkInspection before = (await engine.InspectAsync(id))!;
            int attempts = before.Attempts.Count;
            WorkState? cancelTerminal = await engine.CancelAsync(id);
            Check(cancelTerminal == expected, $"cancel terminal Work {id} changed state {expected}->{cancelTerminal}");
            bool?[] releases = await Task.WhenAll(
                Enumerable.Range(0, 8).Select(_ => engine.ReleaseAsync(id)));
            Check(releases.All(value => value == true), $"repeated/concurrent release failed for {expected}");
            WorkInspection after = (await engine.InspectAsync(id))!;
            WorkResultSnapshot result = (await engine.ResultAsync(id))!;
            Check(after.State == expected && after.Released && after.Attempts.Count == attempts,
                $"release resurrected or altered {expected} Work");
            Check(result.State == expected && result.Released && result.Result is null,
                $"released {expected} Work retained result payload");
        }

        Check(binding.Invocations.Count(value => value == "running") == 1, "running Work executed more than once");
        Check(binding.Invocations.Count(value => value == "succeeded") == 1, "successful Work executed more than once");
        Console.WriteLine("PASS complete Work lifecycle, cancellation/release idempotence and no state resurrection");
    }

    private static async Task DeterministicRaceVerificationAsync()
    {
        await CancelBeforeClaimRaceAsync();
        await ClaimAndCancellationRaceAsync();
        await EligibilityAndDeadlineRacesAsync();
        await SchedulerWakeAndSlotRacesAsync();
        await CompletionObservationRacesAsync();
        await CapabilityProbeRacesAsync();
        Console.WriteLine("PASS deterministic scheduler/cancellation/completion/probe race verification");
    }

    private static async Task CancelBeforeClaimRaceAsync()
    {
        using var temp = new TempDir("madre-race-cancel-before");
        DateTimeOffset now = new(2026, 9, 28, 1, 0, 0, TimeSpan.Zero);
        var clock = new ManualKernelClock(now);
        var binding = new ControlledBinding("controlled/race-before", "1");
        InferenceCapability cap = Capability("race-before", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "race.db")), [cap], [binding], 1, clock);
        await engine.InitializeAsync();
        engine.Start();
        string id = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "cancel-before-claim", InferenceEffort.Low, WorkUrgency.Normal, now.AddMinutes(1), null, ExecutionBoundary.LocalOnly));
        Check(await engine.CancelAsync(id) == WorkState.Cancelled, "cancel-before-claim did not win queued state");
        clock.Advance(TimeSpan.FromMinutes(2));
        WorkInspection cancelled = await WaitStateAsync(engine, id, WorkState.Cancelled);
        Check(cancelled.Attempts.Count == 0 && binding.ExecutionCount == 0,
            "cancel-before-claim created physical execution");
    }

    private static async Task ClaimAndCancellationRaceAsync()
    {
        using var temp = new TempDir("madre-race-claim");
        var started = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var completion = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var binding = new ControlledBinding("controlled/race-claim", "1")
        {
            ExecuteHandler = async (_, cancellationToken) =>
            {
                started.TrySetResult(true);
                using (cancellationToken.Register(() => completion.TrySetResult(BindingExecutionResult.Cancelled())))
                {
                    return await completion.Task.ConfigureAwait(false);
                }
            }
        };
        InferenceCapability cap = Capability("race-claim", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "race.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        string id = await engine.SubmitAsync(Req("claim-cancel", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        Task cancel = Task.Run(async () => _ = await engine.CancelAsync(id));
        engine.Start();
        await cancel.ConfigureAwait(false);
        WorkInspection result = await WaitTerminalAsync(engine, id);
        Check(result.State == WorkState.Cancelled && result.Attempts.Count <= 1,
            $"claim/cancel race produced invalid outcome {result.State} attempts={result.Attempts.Count}");

        var started2 = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var completion2 = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        binding.ExecuteHandler = async (_, cancellationToken) =>
        {
            started2.TrySetResult(true);
            using (cancellationToken.Register(() => completion2.TrySetResult(BindingExecutionResult.Cancelled())))
            {
                return await completion2.Task.ConfigureAwait(false);
            }
        };
        string afterClaim = await engine.SubmitAsync(Req("after-claim-cancel", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await started2.Task.WaitAsync(TimeSpan.FromSeconds(5));
        _ = await engine.CancelAsync(afterClaim);
        WorkInspection after = await WaitTerminalAsync(engine, afterClaim);
        Check(after.State == WorkState.Cancelled && after.Attempts.Count == 1,
            "cancel immediately after claim did not preserve one physical attempt");
    }

    private static async Task EligibilityAndDeadlineRacesAsync()
    {
        using var temp = new TempDir("madre-race-time");
        DateTimeOffset now = new(2026, 9, 28, 2, 0, 0, TimeSpan.Zero);
        var clock = new ManualKernelClock(now);
        var binding = new ControlledBinding("controlled/race-time", "1");
        InferenceCapability cap = Capability("race-time", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "race.db")), [cap], [binding], 1, clock);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        string future = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "eligibility-cross", InferenceEffort.Low, WorkUrgency.Normal, now.AddMinutes(1), null, ExecutionBoundary.LocalOnly));
        await Task.Yield();
        Check((await engine.InspectAsync(future))?.State == WorkState.Queued, "future Work ran before deterministic eligibility");
        clock.Advance(TimeSpan.FromMinutes(1));
        Check((await WaitTerminalAsync(engine, future)).State == WorkState.Succeeded,
            "eligibility boundary wake stranded runnable Work");

        var gate = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        binding.ExecuteHandler = (_, _) => gate.Task;
        string deadlineRace = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "deadline-race", InferenceEffort.Low, WorkUrgency.Normal, null, clock.UtcNow.AddSeconds(1), ExecutionBoundary.LocalOnly));
        Task advance = Task.Run(() => clock.Advance(TimeSpan.FromSeconds(1)));
        await advance.ConfigureAwait(false);
        WorkInspection current = (await engine.InspectAsync(deadlineRace))!;
        if (current.State == WorkState.Running)
        {
            gate.TrySetResult(BindingExecutionResult.Success("won-claim"));
        }
        WorkInspection terminal = await WaitTerminalAsync(engine, deadlineRace);
        Check(terminal.State is WorkState.Succeeded or WorkState.Failed,
            $"deadline/claim race produced illegal state {terminal.State}");
        Check(terminal.Attempts.Count <= 1, "deadline/claim race duplicated physical execution");
        if (terminal.State == WorkState.Failed)
        {
            Check(terminal.Failure?.Kind == PhysicalFailureKind.DeadlineExpired && terminal.Attempts.Count == 0,
                "deadline winner created a physical attempt");
        }
    }

    private static async Task SchedulerWakeAndSlotRacesAsync()
    {
        using var temp = new TempDir("madre-race-slot");
        var blockerStarted = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var blockerRelease = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var binding = new ControlledBinding("controlled/race-slot", "1")
        {
            ExecuteHandler = (request, _) =>
            {
                if (request.PreparedInput == "blocker")
                {
                    blockerStarted.TrySetResult(true);
                    return blockerRelease.Task;
                }
                return Task.FromResult(BindingExecutionResult.Success(request.PreparedInput));
            }
        };
        InferenceCapability cap = Capability("race-slot", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "race.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        await Task.Delay(20);
        string idleSubmit = await engine.SubmitAsync(Req("submit-while-waiting", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        Check((await WaitTerminalAsync(engine, idleSubmit)).State == WorkState.Succeeded,
            "submit concurrent with scheduler wait was stranded");

        string blocker = await engine.SubmitAsync(Req("blocker", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await blockerStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var competitors = new List<string>();
        for (int i = 0; i < 32; i++)
        {
            competitors.Add(await engine.SubmitAsync(Req($"competitor-{i}", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)));
        }
        Task<string> arrival = engine.SubmitAsync(Req("arrival-on-release", InferenceEffort.Low, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
        blockerRelease.TrySetResult(BindingExecutionResult.Success("released"));
        competitors.Add(await arrival.ConfigureAwait(false));
        Check((await WaitTerminalAsync(engine, blocker)).State == WorkState.Succeeded, "slot blocker failed");
        await WaitAllTerminalAsync(engine, competitors);
        Check(binding.MaxActive == 1, $"final-free-slot competition exceeded maxConcurrent: {binding.MaxActive}");

        var sync = new ControlledBinding("controlled/sync", "1");
        InferenceCapability syncCap = Capability("sync", sync.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var syncEngine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "sync.db")), [syncCap], [sync], 4);
        await syncEngine.InitializeAsync();
        await syncEngine.RefreshCapabilityStatesAsync();
        syncEngine.Start();
        var synchronousIds = new List<string>();
        for (int i = 0; i < 100; i++)
        {
            synchronousIds.Add(await syncEngine.SubmitAsync(Req($"sync-{i}", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)));
        }
        await WaitAllTerminalAsync(syncEngine, synchronousIds);
        IReadOnlyList<WorkInspection> inspections = await InspectAllAsync(syncEngine, synchronousIds);
        Check(inspections.All(work => work.State == WorkState.Succeeded && work.Attempts.Count == 1),
            "synchronous completion during running-task registration lost or duplicated Work");
    }

    private static async Task CompletionObservationRacesAsync()
    {
        using var temp = new TempDir("madre-race-completion");
        var started = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var completion = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var binding = new ControlledBinding("controlled/race-completion", "1")
        {
            ExecuteHandler = async (request, cancellationToken) =>
            {
                started.TrySetResult(true);
                if (request.PreparedInput == "cancel-success")
                {
                    Task winner = await Task.WhenAny(
                        completion.Task,
                        Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken)).ConfigureAwait(false);
                    return winner == completion.Task
                        ? await completion.Task.ConfigureAwait(false)
                        : BindingExecutionResult.Cancelled();
                }
                return await completion.Task.ConfigureAwait(false);
            }
        };
        InferenceCapability cap = Capability("race-completion", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "race.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        string id = await engine.SubmitAsync(Req("cancel-success", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await started.Task.WaitAsync(TimeSpan.FromSeconds(5));
        Task<WorkState?> cancel = engine.CancelAsync(id);
        completion.TrySetResult(BindingExecutionResult.Success("physical-success"));
        _ = await cancel.ConfigureAwait(false);
        WorkInspection terminal = await WaitTerminalAsync(engine, id);
        Check(terminal.State is WorkState.Succeeded or WorkState.Cancelled,
            $"cancel/success race fabricated state {terminal.State}");
        Check(terminal.Attempts.Count == 1, "cancel/success race duplicated attempt");

        var startedFail = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var completionFail = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        binding.ExecuteHandler = async (_, cancellationToken) =>
        {
            startedFail.TrySetResult(true);
            Task winner = await Task.WhenAny(
                completionFail.Task,
                Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken)).ConfigureAwait(false);
            return winner == completionFail.Task
                ? await completionFail.Task.ConfigureAwait(false)
                : BindingExecutionResult.Cancelled();
        };
        string failRace = await engine.SubmitAsync(Req("cancel-fail", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await startedFail.Task.WaitAsync(TimeSpan.FromSeconds(5));
        Task<WorkState?> cancelFail = engine.CancelAsync(failRace);
        completionFail.TrySetResult(BindingExecutionResult.Fail(PhysicalFailureKind.IoFailure));
        _ = await cancelFail.ConfigureAwait(false);
        WorkInspection failed = await WaitTerminalAsync(engine, failRace);
        Check(failed.State is WorkState.Failed or WorkState.Cancelled,
            $"cancel/failure race fabricated state {failed.State}");
        Check(failed.Attempts.Count == 1, "cancel/failure race duplicated attempt");

        var commitStarted = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var commit = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        binding.ExecuteHandler = (_, _) =>
        {
            commitStarted.TrySetResult(true);
            return commit.Task;
        };
        string observed = await engine.SubmitAsync(Req("observe-commit", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await commitStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        Task[] readers = Enumerable.Range(0, 64)
            .Select(async _ =>
            {
                _ = await engine.InspectAsync(observed).ConfigureAwait(false);
                _ = await engine.ResultAsync(observed).ConfigureAwait(false);
            })
            .ToArray();
        commit.TrySetResult(BindingExecutionResult.Success("done"));
        await Task.WhenAll(readers).ConfigureAwait(false);
        WorkInspection done = await WaitStateAsync(engine, observed, WorkState.Succeeded);
        bool?[] release = await Task.WhenAll(Enumerable.Range(0, 16).Select(_ => engine.ReleaseAsync(observed)));
        Check(release.All(value => value == true) && done.Attempts.Count == 1,
            "inspect/result/release around completion altered attempt truth");
    }

    private static async Task CapabilityProbeRacesAsync()
    {
        using var temp = new TempDir("madre-race-probe");
        var probeEntered = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var probeRelease = new TaskCompletionSource<CapabilityAvailability>(TaskCreationOptions.RunContinuationsAsynchronously);
        var binding = new ControlledBinding("controlled/race-probe", "1", CapabilityAvailability.Unavailable)
        {
            ProbeHandler = async cancellationToken =>
            {
                probeEntered.TrySetResult(true);
                return await probeRelease.Task.WaitAsync(cancellationToken).ConfigureAwait(false);
            }
        };
        InferenceCapability cap = Capability("race-probe", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "race.db")),
            [cap],
            [binding],
            1,
            timing: new KernelTimingOptions(TimeSpan.FromSeconds(5), TimeSpan.FromMilliseconds(20)));
        await engine.InitializeAsync();
        engine.Start();
        await probeEntered.Task.WaitAsync(TimeSpan.FromSeconds(5));

        Task<IReadOnlyList<CapabilitySnapshot>>[] refreshes = Enumerable.Range(0, 64)
            .Select(_ => engine.RefreshCapabilityStatesAsync())
            .ToArray();
        string demand = await engine.SubmitAsync(Req("probe-demand", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        probeRelease.TrySetResult(CapabilityAvailability.Available);
        await Task.WhenAll(refreshes).ConfigureAwait(false);
        Check(binding.ProbeCount <= 2,
            $"concurrent explicit/demand capability refresh was not bounded/deduplicated: probes={binding.ProbeCount}");
        Check((await WaitTerminalAsync(engine, demand)).State == WorkState.Succeeded,
            "capability becoming Available under demand did not unblock Work");

        var disappearing = new ControlledBinding("controlled/probe-disappear", "1", CapabilityAvailability.Unavailable);
        InferenceCapability disappearingCap = Capability("probe-disappear", disappearing.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var disappearEngine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "disappear.db")),
            [disappearingCap],
            [disappearing],
            1,
            timing: new KernelTimingOptions(TimeSpan.FromMilliseconds(100), TimeSpan.FromMilliseconds(25)));
        await disappearEngine.InitializeAsync();
        await disappearEngine.RefreshCapabilityStatesAsync();
        disappearEngine.Start();
        int before = disappearing.ProbeCount;
        string queued = await disappearEngine.SubmitAsync(Req("disappearing-demand", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        _ = await disappearEngine.CancelAsync(queued);
        await Task.Delay(150).ConfigureAwait(false);
        Check(disappearing.ProbeCount <= before + 1,
            "cancelled demand kept unavailable capability in continuous reprobe loop");
    }

    private static async Task BindingExecutorVerificationAsync()
    {
        using var temp = new TempDir("madre-verify-binding");
        var delayedCancel = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var ignoredCancel = new TaskCompletionSource<BindingExecutionResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        string exact = new('x', KernelProtocol.MaxPayloadBytes);
        string exactMulti = new('é', KernelProtocol.MaxPayloadBytes / 2);
        var binding = new ControlledBinding("controlled/hostile", "1")
        {
            ExecuteHandler = async (request, cancellationToken) => request.PreparedInput switch
            {
                "sync-success" => BindingExecutionResult.Success("sync"),
                "async-success" => await DelayedSuccessAsync("async", cancellationToken).ConfigureAwait(false),
                "empty-result" => BindingExecutionResult.Success(string.Empty),
                "null-success" => new BindingExecutionResult(PhysicalAttemptOutcome.Succeeded, null),
                "max-result" => BindingExecutionResult.Success(exact),
                "max-plus-one" => BindingExecutionResult.Success(exact + "x"),
                "multibyte-max" => BindingExecutionResult.Success(exactMulti),
                "generic-exception" => throw new InvalidOperationException("controlled generic"),
                "io-exception" => throw new IOException("controlled io"),
                "oce" => throw new OperationCanceledException(cancellationToken),
                "definite-failure" => BindingExecutionResult.Fail(PhysicalFailureKind.IoFailure),
                "confirmed-cancel" => BindingExecutionResult.Cancelled(),
                "unknown" => BindingExecutionResult.Unknown(),
                "delayed-cancel" => await DelayedCancellationAsync(cancellationToken, delayedCancel).ConfigureAwait(false),
                "ignored-cancel" => await ignoredCancel.Task.ConfigureAwait(false),
                _ => BindingExecutionResult.Success("ordinary:" + request.PreparedInput)
            }
        };
        InferenceCapability cap = Capability("hostile", binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "binding.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        await engine.RefreshCapabilityStatesAsync();
        engine.Start();

        var cases = new Dictionary<string, (WorkState State, PhysicalFailureKind? Kind)>
        {
            ["sync-success"] = (WorkState.Succeeded, null),
            ["async-success"] = (WorkState.Succeeded, null),
            ["empty-result"] = (WorkState.Succeeded, null),
            ["null-success"] = (WorkState.Failed, PhysicalFailureKind.InvalidBindingResult),
            ["max-result"] = (WorkState.Succeeded, null),
            ["max-plus-one"] = (WorkState.Failed, PhysicalFailureKind.PayloadLimitExceeded),
            ["multibyte-max"] = (WorkState.Succeeded, null),
            ["generic-exception"] = (WorkState.UnknownCompletion, PhysicalFailureKind.CompletionUnknown),
            ["io-exception"] = (WorkState.UnknownCompletion, PhysicalFailureKind.CompletionUnknown),
            ["oce"] = (WorkState.UnknownCompletion, PhysicalFailureKind.CompletionUnknown),
            ["definite-failure"] = (WorkState.Failed, PhysicalFailureKind.IoFailure),
            ["confirmed-cancel"] = (WorkState.Cancelled, PhysicalFailureKind.Cancelled),
            ["unknown"] = (WorkState.UnknownCompletion, PhysicalFailureKind.CompletionUnknown)
        };

        foreach ((string input, (WorkState expectedState, PhysicalFailureKind? expectedKind)) in cases)
        {
            string id = await engine.SubmitAsync(Req(input, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            WorkInspection terminal = await WaitTerminalAsync(engine, id);
            Check(terminal.State == expectedState,
                $"hostile binding {input} classified {terminal.State}, expected {expectedState}");
            if (expectedKind.HasValue)
            {
                Check(terminal.Failure?.Kind == expectedKind,
                    $"hostile binding {input} failure kind {terminal.Failure?.Kind}, expected {expectedKind}");
            }
            await ProveSoleSlotRecoveredAsync(engine, "after-" + input);
        }

        string delayed = await engine.SubmitAsync(Req("delayed-cancel", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, delayed, WorkState.Running);
        _ = await engine.CancelAsync(delayed);
        delayedCancel.TrySetResult(true);
        Check((await WaitTerminalAsync(engine, delayed)).State == WorkState.Cancelled,
            "delayed confirmed cancellation was not preserved");
        await ProveSoleSlotRecoveredAsync(engine, "after-delayed-cancel");

        string ignored = await engine.SubmitAsync(Req("ignored-cancel", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        await WaitStateAsync(engine, ignored, WorkState.Running);
        _ = await engine.CancelAsync(ignored);
        ignoredCancel.TrySetResult(BindingExecutionResult.Success("physically-finished"));
        WorkInspection ignoredTerminal = await WaitTerminalAsync(engine, ignored);
        Check(ignoredTerminal.State == WorkState.Succeeded,
            "ignored cancellation fabricated confirmed cancellation despite physical success");
        await ProveSoleSlotRecoveredAsync(engine, "after-ignored-cancel");

        Console.WriteLine("PASS BindingExecutor hostile binding classifications and sole-slot recovery");
    }

    private static async Task<BindingExecutionResult> DelayedSuccessAsync(string result, CancellationToken cancellationToken)
    {
        await Task.Yield();
        cancellationToken.ThrowIfCancellationRequested();
        return BindingExecutionResult.Success(result);
    }

    private static async Task<BindingExecutionResult> DelayedCancellationAsync(
        CancellationToken cancellationToken,
        TaskCompletionSource<bool> release)
    {
        var cancelled = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        using (cancellationToken.Register(() => cancelled.TrySetResult(true)))
        {
            await cancelled.Task.ConfigureAwait(false);
            await release.Task.ConfigureAwait(false);
            return BindingExecutionResult.Cancelled("controlled delayed cancellation confirmation");
        }
    }

    private static async Task ProveSoleSlotRecoveredAsync(KernelEngine engine, string input)
    {
        string clean = await engine.SubmitAsync(Req(input, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        WorkInspection done = await WaitTerminalAsync(engine, clean);
        Check(done.State == WorkState.Succeeded && done.Attempts.Count == 1,
            $"sole physical slot did not recover after {input}");
    }

    private static async Task MeaiVerificationAsync()
    {
        await RunMeaiCaseAsync("normal", "normal-response", WorkState.Succeeded, null);
        await RunMeaiCaseAsync("empty", string.Empty, WorkState.Succeeded, null);
        await RunMeaiCaseAsync("unicode", "á😀𐐷\n", WorkState.Succeeded, null);
        await RunMeaiCaseAsync("max", new string('m', KernelProtocol.MaxPayloadBytes), WorkState.Succeeded, null);
        await RunMeaiCaseAsync("oversized", new string('m', KernelProtocol.MaxPayloadBytes + 1), WorkState.Failed, PhysicalFailureKind.PayloadLimitExceeded);

        await RunMeaiExceptionalCaseAsync(
            "exception",
            (_, _, _) => throw new IOException("controlled MEAI client failure"),
            WorkState.UnknownCompletion);
        await RunMeaiExceptionalCaseAsync(
            "cancel",
            async (_, _, cancellationToken) =>
            {
                await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken).ConfigureAwait(false);
                return new ChatResponse(new ChatMessage(ChatRole.Assistant, "never"));
            },
            WorkState.UnknownCompletion,
            cancelAfterRunning: true);

        using var temp = new TempDir("madre-meai-probe");
        var client = new LocalChatClient((_, _, _) =>
            Task.FromResult(new ChatResponse(new ChatMessage(ChatRole.Assistant, "probe"))));
        foreach (CapabilityAvailability expected in Enum.GetValues<CapabilityAvailability>())
        {
            string suffix = expected.ToString().ToLowerInvariant();
            var binding = new MeaiInferenceBinding(
                "meai/probe-" + suffix,
                "1",
                client,
                _ => Task.FromResult(expected));
            InferenceCapability cap = Capability("meai-" + suffix, binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
            await using var engine = new KernelEngine(
                new WorkStore(Path.Combine(temp.Path, suffix + ".db")), [cap], [binding], 1);
            await engine.InitializeAsync();
            CapabilitySnapshot snapshot = (await engine.RefreshCapabilityStatesAsync()).Single();
            Check(snapshot.State.Availability == expected,
                $"MEAI explicit probe {expected} became {snapshot.State.Availability}");
        }

        var noProbe = new MeaiInferenceBinding("meai/no-probe", "1", client);
        InferenceCapability noProbeCap = Capability("meai-no-probe", noProbe.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var noProbeEngine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "no-probe.db")), [noProbeCap], [noProbe], 1);
        await noProbeEngine.InitializeAsync();
        Check((await noProbeEngine.RefreshCapabilityStatesAsync()).Single().State.Availability == CapabilityAvailability.Unknown,
            "MEAI binding without probe fabricated availability");

        Console.WriteLine("PASS MEAI physical binding integration with local IChatClient doubles");
    }

    private static Task RunMeaiCaseAsync(
        string name,
        string response,
        WorkState expectedState,
        PhysicalFailureKind? expectedFailure) =>
        RunMeaiExceptionalCaseAsync(
            name,
            (_, _, _) => Task.FromResult(new ChatResponse(new ChatMessage(ChatRole.Assistant, response))),
            expectedState,
            expectedFailure);

    private static async Task RunMeaiExceptionalCaseAsync(
        string name,
        Func<IEnumerable<ChatMessage>, ChatOptions?, CancellationToken, Task<ChatResponse>> callback,
        WorkState expectedState,
        PhysicalFailureKind? expectedFailure = null,
        bool cancelAfterRunning = false)
    {
        using var temp = new TempDir("madre-meai-" + name);
        var client = new LocalChatClient(callback);
        var binding = new MeaiInferenceBinding("meai/" + name, "1", client);
        InferenceCapability cap = Capability("meai-" + name, binding.BindingId, "1", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "meai.db")), [cap], [binding], 1);
        await engine.InitializeAsync();
        engine.Start();
        string id = await engine.SubmitAsync(Req(name, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
        if (cancelAfterRunning)
        {
            await WaitStateAsync(engine, id, WorkState.Running);
            _ = await engine.CancelAsync(id);
        }
        WorkInspection terminal = await WaitTerminalAsync(engine, id);
        Check(terminal.State == expectedState,
            $"MEAI {name} classified {terminal.State}, expected {expectedState}");
        if (expectedFailure.HasValue)
        {
            Check(terminal.Failure?.Kind == expectedFailure,
                $"MEAI {name} failure {terminal.Failure?.Kind}, expected {expectedFailure}");
        }
    }
}
