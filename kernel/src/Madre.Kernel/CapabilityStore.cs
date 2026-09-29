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
                    execution_location, execution_location_source,
                    destination, destination_source,
                    route, route_source,
                    data_retention, data_retention_source,
                    supported_effort, supported_effort_source,
                    owner_preference)
                VALUES (
                    $id, $binding, $bindingVersion,
                    $location, $locationSource,
                    $destination, $destinationSource,
                    $route, $routeSource,
                    $dataRetention, $dataRetentionSource,
                    $effort, $effortSource,
                    $preference);
                """;
            configured.Parameters.AddWithValue("$id", capability.CapabilityId);
            configured.Parameters.AddWithValue("$binding", capability.BindingId);
            configured.Parameters.AddWithValue("$bindingVersion", capability.BindingVersion);
            configured.Parameters.AddWithValue("$location", capability.ExecutionPath.Location.Value.ToString());
            configured.Parameters.AddWithValue("$locationSource", capability.ExecutionPath.Location.Provenance.ToString());
            configured.Parameters.AddWithValue("$destination", capability.ExecutionPath.Destination.Value);
            configured.Parameters.AddWithValue("$destinationSource", capability.ExecutionPath.Destination.Provenance.ToString());
            configured.Parameters.AddWithValue("$route", capability.ExecutionPath.Route is null ? DBNull.Value : capability.ExecutionPath.Route.Value.Value);
            configured.Parameters.AddWithValue("$routeSource", capability.ExecutionPath.Route is null ? DBNull.Value : capability.ExecutionPath.Route.Value.Provenance.ToString());
            configured.Parameters.AddWithValue("$dataRetention", capability.ExecutionPath.DataRetention is null ? DBNull.Value : capability.ExecutionPath.DataRetention.Value.Value);
            configured.Parameters.AddWithValue("$dataRetentionSource", capability.ExecutionPath.DataRetention is null ? DBNull.Value : capability.ExecutionPath.DataRetention.Value.Provenance.ToString());
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
                   c.execution_location, c.execution_location_source,
                   c.destination, c.destination_source,
                   c.route, c.route_source,
                   c.data_retention, c.data_retention_source,
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
            ConfiguredFact<string>? route = reader.IsDBNull(7)
                ? null
                : new ConfiguredFact<string>(reader.GetString(7), Enum.Parse<FactProvenance>(reader.GetString(8)));
            ConfiguredFact<string>? dataRetention = reader.IsDBNull(9)
                ? null
                : new ConfiguredFact<string>(reader.GetString(9), Enum.Parse<FactProvenance>(reader.GetString(10)));
            var capability = new InferenceCapability(
                reader.GetString(0),
                reader.GetString(1),
                reader.GetString(2),
                new CapabilityExecutionPath(
                    new ConfiguredFact<ExecutionLocation>(
                        Enum.Parse<ExecutionLocation>(reader.GetString(3)),
                        Enum.Parse<FactProvenance>(reader.GetString(4))),
                    new ConfiguredFact<string>(
                        reader.GetString(5),
                        Enum.Parse<FactProvenance>(reader.GetString(6))),
                    route,
                    dataRetention),
                new ConfiguredFact<InferenceEffort>(
                    Enum.Parse<InferenceEffort>(reader.GetString(11)),
                    Enum.Parse<FactProvenance>(reader.GetString(12))),
                reader.GetInt32(13));
            var state = new CapabilityState(
                capability.CapabilityId,
                Enum.Parse<CapabilityAvailability>(reader.GetString(14)),
                reader.IsDBNull(15) ? null : SqliteDatabase.FromMs(reader.GetInt64(15)));
            double? latency = reader.IsDBNull(16) ? null : reader.GetDouble(16);
            result.Add(new CapabilitySnapshot(capability, state, latency));
        }
        return result;
    }
}
