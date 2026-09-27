using Microsoft.Data.Sqlite;

namespace Madre.Kernel;

internal sealed class CapabilityStore
{
    private readonly SqliteDatabase _database;

    public CapabilityStore(SqliteDatabase database)
    {
        _database = database;
    }

    public async Task ReconcileAsync(
        IReadOnlyList<InferenceCapability> capabilities,
        CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();

        await using (SqliteCommand clearState = connection.CreateCommand())
        {
            clearState.Transaction = transaction;
            clearState.CommandText = "DELETE FROM capability_state;";
            await clearState.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
        await using (SqliteCommand clearCatalogue = connection.CreateCommand())
        {
            clearCatalogue.Transaction = transaction;
            clearCatalogue.CommandText = "DELETE FROM capabilities;";
            await clearCatalogue.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }

        foreach (InferenceCapability capability in capabilities)
        {
            await using SqliteCommand configured = connection.CreateCommand();
            configured.Transaction = transaction;
            configured.CommandText = """
                INSERT INTO capabilities (
                    capability_id, binding_id, binding_version,
                    execution_boundary, execution_boundary_source,
                    supported_effort, supported_effort_source,
                    owner_preference)
                VALUES ($id, $binding, $bindingVersion, $boundary, $boundarySource, $effort, $effortSource, $preference);
                """;
            configured.Parameters.AddWithValue("$id", capability.CapabilityId);
            configured.Parameters.AddWithValue("$binding", capability.BindingId);
            configured.Parameters.AddWithValue("$bindingVersion", capability.BindingVersion);
            configured.Parameters.AddWithValue("$boundary", capability.ExecutionBoundary.Value.ToString());
            configured.Parameters.AddWithValue("$boundarySource", capability.ExecutionBoundary.Provenance.ToString());
            configured.Parameters.AddWithValue("$effort", capability.SupportedEffort.Value.ToString());
            configured.Parameters.AddWithValue("$effortSource", capability.SupportedEffort.Provenance.ToString());
            configured.Parameters.AddWithValue("$preference", capability.OwnerPreference);
            await configured.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);

            await using SqliteCommand state = connection.CreateCommand();
            state.Transaction = transaction;
            state.CommandText = """
                INSERT INTO capability_state (capability_id, availability, observed_at_ms)
                VALUES ($id, $availability, NULL);
                """;
            state.Parameters.AddWithValue("$id", capability.CapabilityId);
            state.Parameters.AddWithValue("$availability", CapabilityAvailability.Unknown.ToString());
            await state.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }

        transaction.Commit();
    }

    public async Task SetStateAsync(
        string capabilityId,
        CapabilityAvailability availability,
        DateTimeOffset observedAt,
        CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            UPDATE capability_state
            SET availability = $availability, observed_at_ms = $observedAt
            WHERE capability_id = $id;
            """;
        command.Parameters.AddWithValue("$availability", availability.ToString());
        command.Parameters.AddWithValue("$observedAt", observedAt.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$id", capabilityId);
        if (await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false) != 1)
        {
            throw new KeyNotFoundException($"unknown configured capability {capabilityId}");
        }
    }

    public async Task<IReadOnlyList<CapabilitySnapshot>> GetSnapshotsAsync(CancellationToken cancellationToken)
    {
        var result = new List<CapabilitySnapshot>();
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            SELECT c.capability_id, c.binding_id, c.binding_version,
                   c.execution_boundary, c.execution_boundary_source,
                   c.supported_effort, c.supported_effort_source,
                   c.owner_preference,
                   s.availability, s.observed_at_ms,
                   (
                       SELECT AVG(a.latency_ms)
                       FROM attempts a
                       WHERE a.capability_id = c.capability_id
                         AND a.binding_id = c.binding_id
                         AND a.binding_version = c.binding_version
                         AND a.outcome = $success
                   )
            FROM capabilities c
            JOIN capability_state s ON s.capability_id = c.capability_id
            ORDER BY c.capability_id;
            """;
        command.Parameters.AddWithValue("$success", PhysicalAttemptOutcome.Succeeded.ToString());

        await using SqliteDataReader reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        while (await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            var capability = new InferenceCapability(
                reader.GetString(0),
                reader.GetString(1),
                reader.GetString(2),
                new ConfiguredFact<ExecutionBoundary>(
                    Enum.Parse<ExecutionBoundary>(reader.GetString(3)),
                    Enum.Parse<FactProvenance>(reader.GetString(4))),
                new ConfiguredFact<InferenceEffort>(
                    Enum.Parse<InferenceEffort>(reader.GetString(5)),
                    Enum.Parse<FactProvenance>(reader.GetString(6))),
                reader.GetInt32(7));
            var state = new CapabilityState(
                capability.CapabilityId,
                Enum.Parse<CapabilityAvailability>(reader.GetString(8)),
                reader.IsDBNull(9) ? null : SqliteDatabase.FromMs(reader.GetInt64(9)));
            double? latency = reader.IsDBNull(10) ? null : reader.GetDouble(10);
            result.Add(new CapabilitySnapshot(capability, state, latency));
        }
        return result;
    }
}
