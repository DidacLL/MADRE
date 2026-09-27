namespace Madre.Kernel;

public enum DreDecisionKind
{
    Selected,
    WaitForAvailability,
    NoAdmissibleCapability
}

public sealed record DreDecision(DreDecisionKind Kind, InferenceCapability? Capability = null);

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

        if (available.Count == 0)
        {
            return new DreDecision(DreDecisionKind.WaitForAvailability);
        }

        CapabilitySnapshot selected;
        bool comparableLatency = request.Urgency == WorkUrgency.Interactive
            && available.Count > 1
            && available.All(snapshot => snapshot.SuccessfulLatencyMs.HasValue);

        if (comparableLatency)
        {
            selected = available
                .OrderBy(snapshot => snapshot.SuccessfulLatencyMs!.Value)
                .ThenByDescending(snapshot => snapshot.Capability.OwnerPreference)
                .ThenBy(snapshot => snapshot.Capability.CapabilityId, StringComparer.Ordinal)
                .First();
        }
        else
        {
            selected = available
                .OrderByDescending(snapshot => snapshot.Capability.OwnerPreference)
                .ThenBy(snapshot => snapshot.Capability.CapabilityId, StringComparer.Ordinal)
                .First();
        }

        return new DreDecision(DreDecisionKind.Selected, selected.Capability);
    }

    private static bool IsAdmissible(PhysicalInferenceRequest request, InferenceCapability capability)
    {
        if ((int)capability.SupportedEffort.Value < (int)request.RequestedEffort)
        {
            return false;
        }

        if (request.ExecutionBoundary == ExecutionBoundary.LocalOnly
            && capability.ExecutionBoundary.Value != ExecutionBoundary.LocalOnly)
        {
            return false;
        }

        return true;
    }
}
