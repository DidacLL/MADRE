internal static partial class Program
{
    private static async Task AuditBlockersAsync()
    {
        await SingleInstanceSafetyAsync();
        await BackgroundFailureTruthAsync();
        await ObservationDemandAndProvenanceAsync();
        await StrictBoundaryAsync();
        await IpcBoundednessAndUtf8Async();
        await PersistenceAndSchedulingShapeAsync();
    }
}
