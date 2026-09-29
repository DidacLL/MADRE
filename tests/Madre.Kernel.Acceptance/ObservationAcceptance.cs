using Madre.Kernel;

internal static partial class Program
{
    private static async Task ObservationDemandAndProvenanceAsync()
    {
        using var temp = new TempDir("madre-observe-audit");
        var capability = Cap("demand", "audit/demand", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        var binding = new CountingBinding("audit/demand", "1", CapabilityAvailability.Unavailable);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "demand.db")),
            [capability],
            [binding],
            1,
            timing: new KernelTimingOptions(TimeSpan.FromMilliseconds(80), TimeSpan.FromMilliseconds(100))))
        {
            await engine.InitializeAsync();
            engine.Start();
            _ = await WaitSnapshotAsync(
                engine,
                "demand",
                snapshot => snapshot.State.Availability == CapabilityAvailability.Unavailable && snapshot.State.ObservedAt.HasValue,
                2000);
            int startupProbes = binding.ProbeCount;
            await Task.Delay(350);
            Check(binding.ProbeCount == startupProbes,
                "idle Kernel kept periodically probing a capability");

            binding.Availability = CapabilityAvailability.Available;
            string id = await engine.SubmitAsync(Req("demand", InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly));
            await WaitStateAsync(engine, id, WorkState.Succeeded, 4000);
            Check(binding.ProbeCount > startupProbes,
                "pending relevant Work did not demand re-observation");
        }

        var timeoutCapability = Cap("timeout", "probe/slow", InferenceEffort.Low, ExecutionBoundary.LocalOnly, 1);
        await using (var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "timeout.db")),
            [timeoutCapability],
            [new SlowProbeBinding()],
            1,
            timing: new KernelTimingOptions(TimeSpan.FromMilliseconds(80), TimeSpan.FromMilliseconds(100))))
        {
            await engine.InitializeAsync();
            engine.Start();
            CapabilitySnapshot snapshot = await WaitSnapshotAsync(engine, "timeout", s => s.State.ObservedAt.HasValue, 2000);
            Check(snapshot.State.Availability == CapabilityAvailability.Unknown,
                "probe timeout fabricated Unavailable instead of Unknown");
        }

        string evidenceDb = Path.Combine(temp.Path, "evidence.db");
        var v1 = new InferenceCapability(
            "stable-id",
            "audit/versioned",
            "1",
            new CapabilityExecutionPath(
                new ConfiguredFact<ExecutionLocation>(ExecutionLocation.Local, FactProvenance.Owner),
                new ConfiguredFact<string>("owner-local-versioned-test", FactProvenance.Owner)),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.Low, FactProvenance.Owner),
            1);
        await using (var first = new KernelEngine(
            new WorkStore(evidenceDb),
            [v1],
            [new VersionedBinding("audit/versioned", "1", 120)],
            1))
        {
            await first.InitializeAsync();
            first.Start();
            string id = await first.SubmitAsync(Req("v1", InferenceEffort.Low, WorkUrgency.Interactive, ExecutionBoundary.LocalOnly));
            await WaitStateAsync(first, id, WorkState.Succeeded);
            Check((await first.CapabilitiesAsync()).Single().SuccessfulLatencyMs.HasValue,
                "first binding did not establish latency evidence");
        }

        var v2 = v1 with { BindingVersion = "2" };
        await using (var second = new KernelEngine(
            new WorkStore(evidenceDb),
            [v2],
            [new VersionedBinding("audit/versioned", "2", 1)],
            1))
        {
            await second.InitializeAsync();
            Check((await second.CapabilitiesAsync()).Single().SuccessfulLatencyMs is null,
                "stale latency from a previous binding/version leaked into current DRE evidence");
        }

        Console.WriteLine("PASS demand-driven observation, timeout Unknown, and binding-scoped latency evidence");
    }
}
