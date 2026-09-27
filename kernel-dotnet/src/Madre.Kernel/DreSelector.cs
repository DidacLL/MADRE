namespace Madre.Kernel;

public enum DreDecisionKind
{
    Selected,
    WaitForAvailability,
    NoAdmissibleCapability
}

public sealed record DreDecision(
    DreDecisionKind Kind,
    InferenceCapability? Capability = null,
    bool UseCheckpointedTwoStageStrategy = false);

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

        // Slice 2 validates one concrete richer physical strategy without adding a strategy DSL.
        // High-effort background Work is the narrow DRE case used by this validation; existing
        // one-shot High/Normal and High/Interactive behavior remains unchanged.
        bool useCheckpointedTwoStage = request.RequestedEffort == InferenceEffort.High
            && request.Urgency == WorkUrgency.Background;

        return new DreDecision(DreDecisionKind.Selected, selected.Capability, useCheckpointedTwoStage);
    }

    internal DreDecision SelectCheckpointResume(
        PhysicalInferenceRequest request,
        string selectedCapabilityId,
        IReadOnlyList<CapabilitySnapshot> snapshots)
    {
        CapabilitySnapshot? selected = snapshots.FirstOrDefault(
            snapshot => string.Equals(snapshot.Capability.CapabilityId, selectedCapabilityId, StringComparison.Ordinal));
        if (selected is null || !IsAdmissible(request, selected.Capability))
        {
            return new DreDecision(DreDecisionKind.NoAdmissibleCapability);
        }
        if (selected.State.Availability != CapabilityAvailability.Available)
        {
            return new DreDecision(DreDecisionKind.WaitForAvailability);
        }
        return new DreDecision(DreDecisionKind.Selected, selected.Capability, UseCheckpointedTwoStageStrategy: true);
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
