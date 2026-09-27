namespace Madre.Kernel;

public enum InferenceEffort
{
    Low = 0,
    Standard = 1,
    High = 2
}

public enum WorkUrgency
{
    Background = 0,
    Normal = 1,
    Interactive = 2
}

public enum ExecutionBoundary
{
    LocalOnly = 0,
    ExternalAllowed = 1
}

public enum FactProvenance
{
    Owner = 0,
    ProviderOrRuntime = 1
}

public enum CapabilityAvailability
{
    Unavailable = 0,
    Available = 1
}

public enum WorkState
{
    Queued,
    Running,
    Succeeded,
    Failed,
    Cancelled,
    UnknownCompletion
}

public enum PhysicalAttemptOutcome
{
    Running,
    Succeeded,
    DefiniteFailure,
    ConfirmedCancelled,
    UnknownCompletion
}

public sealed record ConfiguredFact<T>(T Value, FactProvenance Provenance);

public sealed record PhysicalInferenceRequest(
    string PreparedInput,
    InferenceEffort RequestedEffort,
    WorkUrgency Urgency,
    DateTimeOffset? EligibleAt,
    DateTimeOffset? Deadline,
    ExecutionBoundary ExecutionBoundary);

public sealed record InferenceCapability(
    string CapabilityId,
    string BindingId,
    string BindingVersion,
    ConfiguredFact<ExecutionBoundary> ExecutionBoundary,
    ConfiguredFact<InferenceEffort> SupportedEffort,
    ConfiguredFact<int> OwnerPreference);

public sealed record CapabilityState(
    string CapabilityId,
    CapabilityAvailability Availability,
    DateTimeOffset ObservedAt);

public sealed record AttemptInspection(
    int AttemptNumber,
    string CapabilityId,
    string BindingId,
    string BindingVersion,
    DateTimeOffset StartedAt,
    DateTimeOffset? EndedAt,
    long? LatencyMs,
    PhysicalAttemptOutcome Outcome,
    string? TechnicalFailure);

public sealed record WorkInspection(
    string WorkId,
    WorkState State,
    DateTimeOffset CreatedAt,
    DateTimeOffset EligibleAt,
    DateTimeOffset? Deadline,
    WorkUrgency Urgency,
    string StrategyType,
    string StrategyVersion,
    string? SelectedCapabilityId,
    string? SelectedBindingId,
    string? SelectedBindingVersion,
    bool CancelRequested,
    bool Released,
    string? FailureCode,
    IReadOnlyList<AttemptInspection> Attempts);

public sealed record WorkSubmissionResponse(string WorkId);

public sealed record WorkResultSnapshot(WorkState State, bool Released, string? Result);

public sealed record CapabilitySnapshot(
    InferenceCapability Capability,
    CapabilityState State,
    double? SuccessfulLatencyMs,
    int SuccessfulObservationCount,
    int FailureObservationCount);

public static class KernelContract
{
    public const int MaxPayloadBytes = 1024 * 1024;
    public const string StrategyType = "single-inference";
    public const string StrategyVersion = "v1";
}
