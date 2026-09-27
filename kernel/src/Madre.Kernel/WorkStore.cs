using Microsoft.Data.Sqlite;

namespace Madre.Kernel;

internal sealed record StoredWork(
    string WorkId,
    WorkState State,
    DateTimeOffset CreatedAt,
    PhysicalInferenceRequest Request,
    bool CancelRequested);

public sealed class WorkStore
{
    private readonly SqliteDatabase _database;

    public WorkStore(string databasePath)
    {
        _database = new SqliteDatabase(databasePath);
        Capabilities = new CapabilityStore(_database);
    }

    internal string DatabasePath => _database.DatabasePath;
    internal CapabilityStore Capabilities { get; }

    public async Task InitializeAsync(
        IReadOnlyList<InferenceCapability> capabilities,
        DateTimeOffset now,
        CancellationToken cancellationToken = default)
    {
        await _database.InitializeSchemaAsync(cancellationToken).ConfigureAwait(false);
        await Capabilities.ReconcileAsync(capabilities, cancellationToken).ConfigureAwait(false);
        await RecoverInterruptedAttemptsAsync(now, cancellationToken).ConfigureAwait(false);
    }

    public async Task<string> SubmitAsync(
        PhysicalInferenceRequest request,
        DateTimeOffset createdAt,
        CancellationToken cancellationToken = default)
    {
        string id = Guid.NewGuid().ToString("N");
        DateTimeOffset eligible = request.EligibleAt ?? createdAt;
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO work (
                work_id, state, prepared_input, requested_effort, urgency, eligible_at_ms, deadline_ms,
                execution_boundary, created_at_ms)
            VALUES ($id, $state, $input, $effort, $urgency, $eligible, $deadline, $boundary, $created);
            """;
        command.Parameters.AddWithValue("$id", id);
        command.Parameters.AddWithValue("$state", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$input", request.PreparedInput);
        command.Parameters.AddWithValue("$effort", request.RequestedEffort.ToString());
        command.Parameters.AddWithValue("$urgency", request.Urgency.ToString());
        command.Parameters.AddWithValue("$eligible", eligible.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$deadline", request.Deadline is null ? DBNull.Value : request.Deadline.Value.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$boundary", request.ExecutionBoundary.ToString());
        command.Parameters.AddWithValue("$created", createdAt.ToUnixTimeMilliseconds());
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        return id;
    }

    internal async Task<IReadOnlyList<StoredWork>> GetEligibleWorkAsync(
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var result = new List<StoredWork>();
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            SELECT work_id, state, prepared_input, requested_effort, urgency, eligible_at_ms, deadline_ms,
                   execution_boundary, created_at_ms, cancel_requested
            FROM work
            WHERE state = $queued AND eligible_at_ms <= $now AND released = 0
            ORDER BY created_at_ms ASC;
            """;
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$now", now.ToUnixTimeMilliseconds());
        await using SqliteDataReader reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        while (await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            string input = reader.IsDBNull(2)
                ? throw new InvalidOperationException("active Work has released input")
                : reader.GetString(2);
            DateTimeOffset eligible = SqliteDatabase.FromMs(reader.GetInt64(5));
            DateTimeOffset? deadline = reader.IsDBNull(6) ? null : SqliteDatabase.FromMs(reader.GetInt64(6));
            var request = new PhysicalInferenceRequest(
                input,
                Enum.Parse<InferenceEffort>(reader.GetString(3)),
                Enum.Parse<WorkUrgency>(reader.GetString(4)),
                eligible,
                deadline,
                Enum.Parse<ExecutionBoundary>(reader.GetString(7)));
            result.Add(new StoredWork(
                reader.GetString(0),
                Enum.Parse<WorkState>(reader.GetString(1)),
                SqliteDatabase.FromMs(reader.GetInt64(8)),
                request,
                reader.GetInt64(9) != 0));
        }

        return result
            .OrderByDescending(work => WorkUrgencyPolicy.Priority(work.Request.Urgency))
            .ThenBy(work => work.CreatedAt)
            .ToList();
    }

    internal async Task<DateTimeOffset?> GetNextSchedulingBoundaryAsync(
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            SELECT MIN(boundary_ms)
            FROM (
                SELECT eligible_at_ms AS boundary_ms
                FROM work
                WHERE state = $queued AND released = 0 AND eligible_at_ms > $now
                UNION ALL
                SELECT deadline_ms AS boundary_ms
                FROM work
                WHERE state = $queued AND released = 0 AND deadline_ms IS NOT NULL AND deadline_ms > $now
            );
            """;
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$now", now.ToUnixTimeMilliseconds());
        object? raw = await command.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false);
        return raw is null || raw is DBNull
            ? null
            : SqliteDatabase.FromMs(Convert.ToInt64(raw));
    }

    internal async Task ExpirePendingDeadlinesAsync(
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            UPDATE work
            SET state = $failed, failure_kind = $kind, failure_detail = $detail
            WHERE state = $queued
              AND deadline_ms IS NOT NULL
              AND deadline_ms <= $now;
            """;
        command.Parameters.AddWithValue("$failed", WorkState.Failed.ToString());
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$kind", PhysicalFailureKind.DeadlineExpired.ToString());
        command.Parameters.AddWithValue("$detail", "deadline elapsed before dispatch");
        command.Parameters.AddWithValue("$now", now.ToUnixTimeMilliseconds());
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    internal Task FailNoAdmissibleCapabilityAsync(string workId, CancellationToken cancellationToken) =>
        FailQueuedAsync(
            workId,
            new PhysicalFailure(PhysicalFailureKind.NoAdmissibleCapability, "no configured capability satisfies the physical requirement"),
            cancellationToken);

    private async Task FailQueuedAsync(
        string workId,
        PhysicalFailure failure,
        CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            UPDATE work
            SET state = $failed, failure_kind = $kind, failure_detail = $detail
            WHERE work_id = $id AND state = $queued;
            """;
        command.Parameters.AddWithValue("$failed", WorkState.Failed.ToString());
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$kind", failure.Kind.ToString());
        command.Parameters.AddWithValue("$detail", failure.Detail is null ? DBNull.Value : failure.Detail);
        command.Parameters.AddWithValue("$id", workId);
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    internal async Task<int?> TryBeginAttemptAsync(
        StoredWork work,
        InferenceCapability capability,
        DateTimeOffset startedAt,
        CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();
        await using SqliteCommand claim = connection.CreateCommand();
        claim.Transaction = transaction;
        claim.CommandText = """
            UPDATE work
            SET state = $running,
                selected_capability_id = $capability,
                selected_binding_id = $binding,
                selected_binding_version = $bindingVersion,
                failure_kind = NULL,
                failure_detail = NULL
            WHERE work_id = $id
              AND state = $queued
              AND cancel_requested = 0
              AND (deadline_ms IS NULL OR deadline_ms > $started);
            """;
        claim.Parameters.AddWithValue("$running", WorkState.Running.ToString());
        claim.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        claim.Parameters.AddWithValue("$capability", capability.CapabilityId);
        claim.Parameters.AddWithValue("$binding", capability.BindingId);
        claim.Parameters.AddWithValue("$bindingVersion", capability.BindingVersion);
        claim.Parameters.AddWithValue("$id", work.WorkId);
        claim.Parameters.AddWithValue("$started", startedAt.ToUnixTimeMilliseconds());
        if (await claim.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false) != 1)
        {
            transaction.Rollback();
            return null;
        }

        await using SqliteCommand number = connection.CreateCommand();
        number.Transaction = transaction;
        number.CommandText = "SELECT COALESCE(MAX(attempt_number), 0) + 1 FROM attempts WHERE work_id = $id;";
        number.Parameters.AddWithValue("$id", work.WorkId);
        int attemptNumber = Convert.ToInt32(await number.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false));

        await using SqliteCommand insert = connection.CreateCommand();
        insert.Transaction = transaction;
        insert.CommandText = """
            INSERT INTO attempts (
                work_id, attempt_number, capability_id, binding_id, binding_version, started_at_ms, outcome)
            VALUES ($id, $number, $capability, $binding, $bindingVersion, $started, $outcome);
            """;
        insert.Parameters.AddWithValue("$id", work.WorkId);
        insert.Parameters.AddWithValue("$number", attemptNumber);
        insert.Parameters.AddWithValue("$capability", capability.CapabilityId);
        insert.Parameters.AddWithValue("$binding", capability.BindingId);
        insert.Parameters.AddWithValue("$bindingVersion", capability.BindingVersion);
        insert.Parameters.AddWithValue("$started", startedAt.ToUnixTimeMilliseconds());
        insert.Parameters.AddWithValue("$outcome", PhysicalAttemptOutcome.Running.ToString());
        await insert.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        transaction.Commit();
        return attemptNumber;
    }

    internal async Task CompleteAttemptAsync(
        string workId,
        int attemptNumber,
        BindingExecutionResult result,
        DateTimeOffset endedAt,
        long latencyMs,
        CancellationToken cancellationToken)
    {
        WorkState workState = result.Outcome switch
        {
            PhysicalAttemptOutcome.Succeeded => WorkState.Succeeded,
            PhysicalAttemptOutcome.DefiniteFailure => WorkState.Failed,
            PhysicalAttemptOutcome.ConfirmedCancelled => WorkState.Cancelled,
            PhysicalAttemptOutcome.UnknownCompletion => WorkState.UnknownCompletion,
            _ => throw new InvalidOperationException("attempt cannot complete as Running")
        };

        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();
        await using SqliteCommand attempt = connection.CreateCommand();
        attempt.Transaction = transaction;
        attempt.CommandText = """
            UPDATE attempts
            SET ended_at_ms = $ended,
                latency_ms = $latency,
                outcome = $outcome,
                failure_kind = $failureKind,
                failure_detail = $failureDetail
            WHERE work_id = $id AND attempt_number = $number AND outcome = $running;
            """;
        attempt.Parameters.AddWithValue("$ended", endedAt.ToUnixTimeMilliseconds());
        attempt.Parameters.AddWithValue("$latency", latencyMs);
        attempt.Parameters.AddWithValue("$outcome", result.Outcome.ToString());
        attempt.Parameters.AddWithValue("$failureKind", result.Failure is null ? DBNull.Value : result.Failure.Kind.ToString());
        attempt.Parameters.AddWithValue("$failureDetail", result.Failure?.Detail is null ? DBNull.Value : result.Failure.Detail);
        attempt.Parameters.AddWithValue("$id", workId);
        attempt.Parameters.AddWithValue("$number", attemptNumber);
        attempt.Parameters.AddWithValue("$running", PhysicalAttemptOutcome.Running.ToString());
        await attempt.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);

        await using SqliteCommand work = connection.CreateCommand();
        work.Transaction = transaction;
        work.CommandText = """
            UPDATE work
            SET state = $state,
                result_text = $result,
                failure_kind = $failureKind,
                failure_detail = $failureDetail
            WHERE work_id = $id AND state = $running;
            """;
        work.Parameters.AddWithValue("$state", workState.ToString());
        work.Parameters.AddWithValue("$result", result.Result is null ? DBNull.Value : result.Result);
        work.Parameters.AddWithValue("$failureKind", result.Failure is null ? DBNull.Value : result.Failure.Kind.ToString());
        work.Parameters.AddWithValue("$failureDetail", result.Failure?.Detail is null ? DBNull.Value : result.Failure.Detail);
        work.Parameters.AddWithValue("$id", workId);
        work.Parameters.AddWithValue("$running", WorkState.Running.ToString());
        await work.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        transaction.Commit();
    }

    public async Task<WorkState?> CancelAsync(string workId, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();
        await using SqliteCommand select = connection.CreateCommand();
        select.Transaction = transaction;
        select.CommandText = "SELECT state FROM work WHERE work_id = $id;";
        select.Parameters.AddWithValue("$id", workId);
        object? raw = await select.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false);
        if (raw is null)
        {
            transaction.Rollback();
            return null;
        }

        WorkState state = Enum.Parse<WorkState>((string)raw);
        await using SqliteCommand update = connection.CreateCommand();
        update.Transaction = transaction;
        if (state == WorkState.Queued)
        {
            update.CommandText = """
                UPDATE work
                SET state = $cancelled,
                    cancel_requested = 1,
                    failure_kind = $kind,
                    failure_detail = $detail
                WHERE work_id = $id;
                """;
            update.Parameters.AddWithValue("$cancelled", WorkState.Cancelled.ToString());
            update.Parameters.AddWithValue("$kind", PhysicalFailureKind.Cancelled.ToString());
            update.Parameters.AddWithValue("$detail", "cancelled before dispatch");
            state = WorkState.Cancelled;
        }
        else if (state == WorkState.Running)
        {
            update.CommandText = "UPDATE work SET cancel_requested = 1 WHERE work_id = $id;";
        }
        else
        {
            transaction.Commit();
            return state;
        }
        update.Parameters.AddWithValue("$id", workId);
        await update.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        transaction.Commit();
        return state;
    }

    public async Task<bool?> ReleaseAsync(string workId, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand exists = connection.CreateCommand();
        exists.CommandText = "SELECT state FROM work WHERE work_id = $id;";
        exists.Parameters.AddWithValue("$id", workId);
        object? raw = await exists.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false);
        if (raw is null)
        {
            return null;
        }
        WorkState state = Enum.Parse<WorkState>((string)raw);
        if (state is WorkState.Queued or WorkState.Running)
        {
            return false;
        }
        await using SqliteCommand release = connection.CreateCommand();
        release.CommandText = "UPDATE work SET released = 1, prepared_input = NULL, result_text = NULL WHERE work_id = $id;";
        release.Parameters.AddWithValue("$id", workId);
        await release.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        return true;
    }

    public async Task<WorkInspection?> GetInspectionAsync(string workId, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand work = connection.CreateCommand();
        work.CommandText = """
            SELECT state, created_at_ms, eligible_at_ms, deadline_ms, urgency,
                   selected_capability_id, selected_binding_id, selected_binding_version,
                   cancel_requested, released, failure_kind, failure_detail
            FROM work WHERE work_id = $id;
            """;
        work.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader reader = await work.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        if (!await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            return null;
        }
        WorkState state = Enum.Parse<WorkState>(reader.GetString(0));
        DateTimeOffset created = SqliteDatabase.FromMs(reader.GetInt64(1));
        DateTimeOffset eligible = SqliteDatabase.FromMs(reader.GetInt64(2));
        DateTimeOffset? deadline = reader.IsDBNull(3) ? null : SqliteDatabase.FromMs(reader.GetInt64(3));
        WorkUrgency urgency = Enum.Parse<WorkUrgency>(reader.GetString(4));
        string? selectedCapability = reader.IsDBNull(5) ? null : reader.GetString(5);
        string? selectedBinding = reader.IsDBNull(6) ? null : reader.GetString(6);
        string? selectedBindingVersion = reader.IsDBNull(7) ? null : reader.GetString(7);
        bool cancelRequested = reader.GetInt64(8) != 0;
        bool released = reader.GetInt64(9) != 0;
        PhysicalFailure? failure = ReadFailure(reader, 10, 11);
        await reader.DisposeAsync().ConfigureAwait(false);

        var attempts = new List<AttemptInspection>();
        await using SqliteCommand attempt = connection.CreateCommand();
        attempt.CommandText = """
            SELECT attempt_number, capability_id, binding_id, binding_version, started_at_ms,
                   ended_at_ms, latency_ms, outcome, failure_kind, failure_detail
            FROM attempts WHERE work_id = $id ORDER BY attempt_number;
            """;
        attempt.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader attemptReader = await attempt.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        while (await attemptReader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            attempts.Add(new AttemptInspection(
                attemptReader.GetInt32(0),
                attemptReader.GetString(1),
                attemptReader.GetString(2),
                attemptReader.GetString(3),
                SqliteDatabase.FromMs(attemptReader.GetInt64(4)),
                attemptReader.IsDBNull(5) ? null : SqliteDatabase.FromMs(attemptReader.GetInt64(5)),
                attemptReader.IsDBNull(6) ? null : attemptReader.GetInt64(6),
                Enum.Parse<PhysicalAttemptOutcome>(attemptReader.GetString(7)),
                ReadFailure(attemptReader, 8, 9)));
        }

        return new WorkInspection(
            workId,
            state,
            created,
            eligible,
            deadline,
            urgency,
            selectedCapability,
            selectedBinding,
            selectedBindingVersion,
            cancelRequested,
            released,
            failure,
            attempts);
    }

    public async Task<WorkResultSnapshot?> GetResultAsync(string workId, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = "SELECT state, released, result_text FROM work WHERE work_id = $id;";
        command.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        if (!await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            return null;
        }
        return new WorkResultSnapshot(
            Enum.Parse<WorkState>(reader.GetString(0)),
            reader.GetInt64(1) != 0,
            reader.IsDBNull(2) ? null : reader.GetString(2));
    }

    private async Task RecoverInterruptedAttemptsAsync(DateTimeOffset now, CancellationToken cancellationToken)
    {
        long nowMs = now.ToUnixTimeMilliseconds();
        await using SqliteConnection connection = await _database.OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();
        await using (SqliteCommand attempts = connection.CreateCommand())
        {
            attempts.Transaction = transaction;
            attempts.CommandText = """
                UPDATE attempts
                SET outcome = $unknown,
                    ended_at_ms = $now,
                    latency_ms = MAX(0, $now - started_at_ms),
                    failure_kind = $kind,
                    failure_detail = $detail
                WHERE outcome = $running;
                """;
            attempts.Parameters.AddWithValue("$unknown", PhysicalAttemptOutcome.UnknownCompletion.ToString());
            attempts.Parameters.AddWithValue("$running", PhysicalAttemptOutcome.Running.ToString());
            attempts.Parameters.AddWithValue("$kind", PhysicalFailureKind.CompletionUnknown.ToString());
            attempts.Parameters.AddWithValue("$detail", "Kernel restarted during an active physical attempt");
            attempts.Parameters.AddWithValue("$now", nowMs);
            await attempts.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
        await using (SqliteCommand work = connection.CreateCommand())
        {
            work.Transaction = transaction;
            work.CommandText = """
                UPDATE work
                SET state = $unknown,
                    failure_kind = $kind,
                    failure_detail = $detail
                WHERE state = $running;
                """;
            work.Parameters.AddWithValue("$unknown", WorkState.UnknownCompletion.ToString());
            work.Parameters.AddWithValue("$running", WorkState.Running.ToString());
            work.Parameters.AddWithValue("$kind", PhysicalFailureKind.CompletionUnknown.ToString());
            work.Parameters.AddWithValue("$detail", "Kernel restarted during an active physical attempt");
            await work.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
        transaction.Commit();
    }

    private static PhysicalFailure? ReadFailure(SqliteDataReader reader, int kindIndex, int detailIndex)
    {
        if (reader.IsDBNull(kindIndex))
        {
            return null;
        }
        return new PhysicalFailure(
            Enum.Parse<PhysicalFailureKind>(reader.GetString(kindIndex)),
            reader.IsDBNull(detailIndex) ? null : reader.GetString(detailIndex));
    }
}
