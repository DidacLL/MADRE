namespace Madre.Kernel;

public enum DreDecisionKind
{
    Selected,
    WaitForAvailability,
    NoAdmissibleCapability
}

public sealed record DreDecision(
    DreDecisionKind Kind,
    InferenceCapability? Capability = null);

public sealed class DreSelector
{
    public DreDecision Select(PhysicalInferenceRequest request, IReadOnlyList<CapabilitySnapshot> snapshots) =>
        SelectCore(
            request.RequestedEffort,
            request.Urgency,
            request.ExecutionBoundary,
            request.EligibleCapabilityIds,
            snapshots);

    internal DreDecision Select(SchedulingWork work, IReadOnlyList<CapabilitySnapshot> snapshots) =>
        SelectCore(
            work.RequestedEffort,
            work.Urgency,
            work.ExecutionBoundary,
            work.EligibleCapabilityIds,
            snapshots);

    internal static bool IsAdmissible(SchedulingWork work, InferenceCapability capability) =>
        IsAdmissible(
            work.RequestedEffort,
            work.ExecutionBoundary,
            work.EligibleCapabilityIds,
            capability);

    private static DreDecision SelectCore(
        InferenceEffort requestedEffort,
        WorkUrgency urgency,
        ExecutionBoundary executionBoundary,
        IReadOnlyList<string>? eligibleCapabilityIds,
        IReadOnlyList<CapabilitySnapshot> snapshots)
    {
        List<CapabilitySnapshot> admissible = snapshots
            .Where(snapshot => IsAdmissible(
                requestedEffort,
                executionBoundary,
                eligibleCapabilityIds,
                snapshot.Capability))
            .ToList();

        if (admissible.Count == 0)
        {
            return new DreDecision(DreDecisionKind.NoAdmissibleCapability);
        }

        List<CapabilitySnapshot> available = admissible
            .Where(snapshot => snapshot.State.Availability == CapabilityAvailability.Available)
            .ToList();
        if (available.Count > 0)
        {
            return new DreDecision(DreDecisionKind.Selected, Choose(urgency, available).Capability);
        }

        List<CapabilitySnapshot> unknown = admissible
            .Where(snapshot => snapshot.State.Availability == CapabilityAvailability.Unknown)
            .ToList();
        if (unknown.Count > 0)
        {
            return new DreDecision(DreDecisionKind.Selected, Choose(urgency, unknown).Capability);
        }

        return new DreDecision(DreDecisionKind.WaitForAvailability);
    }

    private static CapabilitySnapshot Choose(
        WorkUrgency urgency,
        IReadOnlyList<CapabilitySnapshot> candidates)
    {
        bool comparableLatency = urgency == WorkUrgency.Interactive
            && candidates.Count > 1
            && candidates.All(snapshot => snapshot.SuccessfulLatencyMs.HasValue);

        return comparableLatency
            ? candidates
                .OrderBy(snapshot => snapshot.SuccessfulLatencyMs!.Value)
                .ThenByDescending(snapshot => snapshot.Capability.OwnerPreference)
                .ThenBy(snapshot => snapshot.Capability.CapabilityId, StringComparer.Ordinal)
                .First()
            : candidates
                .OrderByDescending(snapshot => snapshot.Capability.OwnerPreference)
                .ThenBy(snapshot => snapshot.Capability.CapabilityId, StringComparer.Ordinal)
                .First();
    }

    private static bool IsAdmissible(
        InferenceEffort requestedEffort,
        ExecutionBoundary executionBoundary,
        IReadOnlyList<string>? eligibleCapabilityIds,
        InferenceCapability capability)
    {
        if (eligibleCapabilityIds is not null
            && !eligibleCapabilityIds.Contains(capability.CapabilityId, StringComparer.Ordinal))
        {
            return false;
        }

        if (!InferenceEffortPolicy.Supports(capability.SupportedEffort.Value, requestedEffort))
        {
            return false;
        }

        return executionBoundary != ExecutionBoundary.LocalOnly
            || capability.ExecutionPath.Location.Value == ExecutionLocation.Local;
    }
}
