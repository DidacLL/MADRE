namespace Madre.Kernel;

public enum InferenceEffort
{
    Low,
    Standard,
    High
}

public enum WorkUrgency
{
    Background,
    Normal,
    Interactive
}

// Request-side admissibility ceiling. This is not a factual description of where a capability executes.
public enum ExecutionBoundary
{
    LocalOnly,
    ExternalAllowed
}

// Capability-side factual location of the physical execution path.
public enum ExecutionLocation
{
    Local,
    External
}

public enum FactProvenance
{
    Owner,
    ProviderOrRuntime
}

public enum CapabilityAvailability
{
    Unknown,
    Unavailable,
    Available
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

public enum PhysicalFailureKind
{
    NoAdmissibleCapability,
    DeadlineExpired,
    Cancelled,
    LaunchFailed,
    ProcessExited,
    PayloadLimitExceeded,
    IoFailure,
    InvalidBindingResult,
    CompletionUnknown
}

public sealed record PhysicalFailure(PhysicalFailureKind Kind, string? Detail = null);

public sealed record ConfiguredFact<T>(T Value, FactProvenance Provenance);

// Factual configured description of the physical information journey. Optional route/retention text
// remains deliberately opaque to Kernel policy; it is exposed so semantic MADRE/Owner tooling can
// understand the configured journey without Kernel learning SPIRA or provider semantics.
public sealed record CapabilityExecutionPath(
    ConfiguredFact<ExecutionLocation> Location,
    ConfiguredFact<string> Destination,
    ConfiguredFact<string>? Route = null,
    ConfiguredFact<string>? DataRetention = null);

public sealed record PhysicalInferenceRequest(
    string PreparedInput,
    InferenceEffort RequestedEffort,
    WorkUrgency Urgency,
    DateTimeOffset? EligibleAt,
    DateTimeOffset? Deadline,
    ExecutionBoundary ExecutionBoundary,
    IReadOnlyList<string>? EligibleCapabilityIds = null);

public sealed record InferenceExecutionRequest(string PreparedInput);

public sealed record InferenceCapability(
    string CapabilityId,
    string BindingId,
    string BindingVersion,
    CapabilityExecutionPath ExecutionPath,
    ConfiguredFact<InferenceEffort> SupportedEffort,
    int OwnerPreference);

public sealed record CapabilityState(
    string CapabilityId,
    CapabilityAvailability Availability,
    DateTimeOffset? ObservedAt);

public sealed record AttemptInspection(
    int AttemptNumber,
    string CapabilityId,
    string BindingId,
    string BindingVersion,
    DateTimeOffset StartedAt,
    DateTimeOffset? EndedAt,
    long? LatencyMs,
    PhysicalAttemptOutcome Outcome,
    PhysicalFailure? Failure);

public sealed record WorkInspection(
    string WorkId,
    WorkState State,
    DateTimeOffset CreatedAt,
    DateTimeOffset EligibleAt,
    DateTimeOffset? Deadline,
    WorkUrgency Urgency,
    string? SelectedCapabilityId,
    string? SelectedBindingId,
    string? SelectedBindingVersion,
    bool CancelRequested,
    bool Released,
    PhysicalFailure? Failure,
    IReadOnlyList<AttemptInspection> Attempts);

public sealed record WorkSubmissionResponse(string WorkId);

public sealed record WorkResultSnapshot(WorkState State, bool Released, string? Result);

public sealed record CapabilitySnapshot(
    InferenceCapability Capability,
    CapabilityState State,
    double? SuccessfulLatencyMs);

public static class KernelProtocol
{
    public const int Version = 2;
    public const int MaxPayloadBytes = 1024 * 1024;
    public const int MaxFrameBytes = (8 * MaxPayloadBytes) + (64 * 1024);
}

public static class InferenceEffortPolicy
{
    public static bool Supports(InferenceEffort supported, InferenceEffort requested) => requested switch
    {
        InferenceEffort.Low => true,
        InferenceEffort.Standard => supported is InferenceEffort.Standard or InferenceEffort.High,
        InferenceEffort.High => supported == InferenceEffort.High,
        _ => false
    };
}

public static class WorkUrgencyPolicy
{
    public static int Priority(WorkUrgency urgency) => urgency switch
    {
        WorkUrgency.Background => 0,
        WorkUrgency.Normal => 1,
        WorkUrgency.Interactive => 2,
        _ => throw new ArgumentOutOfRangeException(nameof(urgency))
    };
}
