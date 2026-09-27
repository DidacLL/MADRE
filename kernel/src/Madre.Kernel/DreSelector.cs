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
    public DreDecision Select(PhysicalInferenceRequest request, IReadOnlyList<CapabilitySnapshot> snapshots)
    {
        List<CapabilitySnapshot> admissible = snapshots
            .Where(snapshot => IsAdmissible(request, snapshot.Capability))
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
            return new DreDecision(DreDecisionKind.Selected, Choose(request, available).Capability);
        }

        // Unknown means that Kernel lacks current availability evidence. It is not evidence
        // that the configured capability is unavailable. If no known-available option exists,
        // DRE may try an otherwise admissible Unknown capability and let execution produce
        // physical evidence. Known-unavailable capabilities remain waiting candidates only.
        List<CapabilitySnapshot> unknown = admissible
            .Where(snapshot => snapshot.State.Availability == CapabilityAvailability.Unknown)
            .ToList();
        if (unknown.Count > 0)
        {
            return new DreDecision(DreDecisionKind.Selected, Choose(request, unknown).Capability);
        }

        return new DreDecision(DreDecisionKind.WaitForAvailability);
    }

    private static CapabilitySnapshot Choose(
        PhysicalInferenceRequest request,
        IReadOnlyList<CapabilitySnapshot> candidates)
    {
        bool comparableLatency = request.Urgency == WorkUrgency.Interactive
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

    private static bool IsAdmissible(PhysicalInferenceRequest request, InferenceCapability capability)
    {
        if (!InferenceEffortPolicy.Supports(capability.SupportedEffort.Value, request.RequestedEffort))
        {
            return false;
        }

        return request.ExecutionBoundary != ExecutionBoundary.LocalOnly
            || capability.ExecutionBoundary.Value == ExecutionBoundary.LocalOnly;
    }
}
