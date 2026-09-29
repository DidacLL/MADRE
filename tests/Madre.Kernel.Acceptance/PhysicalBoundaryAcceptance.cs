using System.Text.Json;
using Madre.Kernel;

internal static partial class Program
{
    private static async Task PhysicalBoundaryContractAsync()
    {
        await PhysicalEligibilityContractAsync();
        await JavaPhysicalBoundaryContractAsync();
        Console.WriteLine("PASS semantic-to-physical admissibility and factual execution-path boundary");
    }

    private static async Task PhysicalEligibilityContractAsync()
    {
        using var temp = new TempDir("madre-physical-boundary");
        var capabilityA = new InferenceCapability(
            "external-a",
            "boundary/a",
            "1",
            new CapabilityExecutionPath(
                new ConfiguredFact<ExecutionLocation>(ExecutionLocation.External, FactProvenance.Owner),
                new ConfiguredFact<string>("provider-a.example", FactProvenance.Owner),
                new ConfiguredFact<string>("owner-router-a", FactProvenance.Owner),
                new ConfiguredFact<string>("provider-retains-30d", FactProvenance.ProviderOrRuntime)),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.High, FactProvenance.Owner),
            10);
        var capabilityB = new InferenceCapability(
            "external-b",
            "boundary/b",
            "1",
            new CapabilityExecutionPath(
                new ConfiguredFact<ExecutionLocation>(ExecutionLocation.External, FactProvenance.Owner),
                new ConfiguredFact<string>("provider-b.example", FactProvenance.Owner),
                new ConfiguredFact<string>("direct-provider-b", FactProvenance.Owner),
                new ConfiguredFact<string>("provider-policy-b", FactProvenance.ProviderOrRuntime)),
            new ConfiguredFact<InferenceEffort>(InferenceEffort.High, FactProvenance.Owner),
            100);

        await using var engine = new KernelEngine(
            new WorkStore(Path.Combine(temp.Path, "boundary.db")),
            [capabilityA, capabilityB],
            [
                new FixedBinding("boundary/a", CapabilityAvailability.Available, false),
                new FixedBinding("boundary/b", CapabilityAvailability.Available, false)
            ],
            1);
        await engine.InitializeAsync();
        engine.Start();

        string unrestricted = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "unrestricted",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed));
        Check((await WaitStateAsync(engine, unrestricted, WorkState.Succeeded)).SelectedCapabilityId == "external-b",
            "unrestricted physical Work did not preserve ordinary DRE selection");

        string exact = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "exact-a",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed,
            ["external-a"]));
        WorkInspection exactInspection = await WaitStateAsync(engine, exact, WorkState.Succeeded);
        Check(exactInspection.SelectedCapabilityId == "external-a",
            "singleton eligible capability did not preserve exact physical selection");
        Check(exactInspection.ExecutionBoundary == ExecutionBoundary.ExternalAllowed
            && exactInspection.EligibleCapabilityIds is { Count: 1 }
            && exactInspection.EligibleCapabilityIds[0] == "external-a",
            "durable Work inspection lost physical admissibility restrictions");

        string eligibleSet = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "eligible-set",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed,
            ["external-a", "external-b"]));
        Check((await WaitStateAsync(engine, eligibleSet, WorkState.Succeeded)).SelectedCapabilityId == "external-b",
            "eligible capability set prevented DRE from selecting within the admissible physical space");

        string missing = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "missing-capability",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.ExternalAllowed,
            ["not-configured"]));
        WorkInspection missingInspection = await WaitStateAsync(engine, missing, WorkState.Failed);
        Check(missingInspection.Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability
            && missingInspection.Attempts.Count == 0,
            "unknown eligible capability restriction did not fail as no admissible physical capability");

        string localOnlyExternal = await engine.SubmitAsync(new PhysicalInferenceRequest(
            "local-only-external",
            InferenceEffort.High,
            WorkUrgency.Normal,
            null,
            null,
            ExecutionBoundary.LocalOnly,
            ["external-a"]));
        WorkInspection localOnlyInspection = await WaitStateAsync(engine, localOnlyExternal, WorkState.Failed);
        Check(localOnlyInspection.Failure?.Kind == PhysicalFailureKind.NoAdmissibleCapability
            && localOnlyInspection.Attempts.Count == 0,
            "request-side LocalOnly constraint admitted a factually external capability");

        IReadOnlyList<CapabilitySnapshot> snapshots = await engine.CapabilitiesAsync();
        CapabilitySnapshot a = snapshots.Single(snapshot => snapshot.Capability.CapabilityId == "external-a");
        Check(a.Capability.ExecutionPath.Location.Value == ExecutionLocation.External,
            "capability snapshot did not expose factual execution location");
        Check(a.Capability.ExecutionPath.Destination.Value == "provider-a.example",
            "capability snapshot did not expose factual destination");
        Check(a.Capability.ExecutionPath.Route?.Value == "owner-router-a",
            "capability snapshot did not expose configured route fact");
        Check(a.Capability.ExecutionPath.DataRetention?.Value == "provider-retains-30d"
            && a.Capability.ExecutionPath.DataRetention?.Provenance == FactProvenance.ProviderOrRuntime,
            "capability snapshot lost retention fact provenance");
    }

    private static async Task JavaPhysicalBoundaryContractAsync()
    {
        using var temp = new TempDir("madre-java-physical-boundary");
        TestEnv env = MakeEnv(temp.Path, slowProbe: true, fastProbe: true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "available");

        await using var kernel = await KernelProcess.StartAsync(env.Config, env.Database, env.Socket);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);
        await WaitCapabilityAsync(client, Fast, CapabilityAvailability.Available);

        string workId = (await JavaAsync(
            env.Socket,
            "submit",
            "java-exact-external",
            "High",
            "Normal",
            "ExternalAllowed",
            Fast)).Trim();
        WorkInspection inspection = await WaitStateAsync(client, workId, WorkState.Succeeded);
        Check(inspection.SelectedCapabilityId == Fast
            && inspection.EligibleCapabilityIds is { Count: 1 }
            && inspection.EligibleCapabilityIds[0] == Fast,
            "Java physical client did not preserve singleton eligible capability restriction");

        using JsonDocument capabilities = JsonDocument.Parse(await JavaAsync(env.Socket, "capabilities"));
        JsonElement fast = capabilities.RootElement.EnumerateArray()
            .Single(element => element.GetProperty("capability").GetProperty("capabilityId").GetString() == Fast);
        JsonElement executionPath = fast.GetProperty("capability").GetProperty("executionPath");
        Check(executionPath.GetProperty("location").GetProperty("value").GetString() == "External",
            "Java capability contract lost factual execution location");
        Check(executionPath.GetProperty("destination").GetProperty("value").GetString() == "test-external",
            "Java capability contract lost factual destination");
        Check(executionPath.GetProperty("route").GetProperty("value").GetString() == "test-route"
            && executionPath.GetProperty("dataRetention").GetProperty("value").GetString() == "test-retention",
            "Java capability contract lost configured route/retention facts");
    }
}
