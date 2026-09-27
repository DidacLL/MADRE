using Microsoft.Data.Sqlite;

namespace Madre.Kernel;

internal sealed record StoredWork(
    string WorkId,
    WorkState State,
    DateTimeOffset CreatedAt,
    PhysicalInferenceRequest Request,
    string StrategyType,
    string StrategyVersion);

public sealed class WorkStore
{
    private readonly string _connectionString;

    public WorkStore(string databasePath)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(databasePath))!);
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = databasePath,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Cache = SqliteCacheMode.Shared,
            Pooling = true
        }.ToString();
    }

    public async Task InitializeAsync(IReadOnlyList<InferenceCapability> capabilities, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using (SqliteCommand schema = connection.CreateCommand())
        {
            schema.CommandText = """
                PRAGMA journal_mode=WAL;
                PRAGMA foreign_keys=ON;
                CREATE TABLE IF NOT EXISTS capabilities (
                    capability_id TEXT PRIMARY KEY,
                    binding_id TEXT NOT NULL,
                    binding_version TEXT NOT NULL,
                    execution_boundary TEXT NOT NULL,
                    execution_boundary_source TEXT NOT NULL,
                    supported_effort TEXT NOT NULL,
                    supported_effort_source TEXT NOT NULL,
                    owner_preference INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS capability_state (
                    capability_id TEXT PRIMARY KEY REFERENCES capabilities(capability_id) ON DELETE CASCADE,
                    availability TEXT NOT NULL,
                    observed_at_ms INTEGER
                );
                CREATE TABLE IF NOT EXISTS work (
                    work_id TEXT PRIMARY KEY,
                    state TEXT NOT NULL,
                    prepared_input TEXT,
                    requested_effort TEXT NOT NULL,
                    urgency TEXT NOT NULL,
                    eligible_at_ms INTEGER NOT NULL,
                    deadline_ms INTEGER,
                    execution_boundary TEXT NOT NULL,
                    created_at_ms INTEGER NOT NULL,
                    strategy_type TEXT NOT NULL,
                    strategy_version TEXT NOT NULL,
                    selected_capability_id TEXT,
                    selected_binding_id TEXT,
                    selected_binding_version TEXT,
                    cancel_requested INTEGER NOT NULL DEFAULT 0,
                    result_text TEXT,
                    failure_code TEXT,
                    released INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE IF NOT EXISTS attempts (
                    work_id TEXT NOT NULL REFERENCES work(work_id) ON DELETE CASCADE,
                    attempt_number INTEGER NOT NULL,
                    capability_id TEXT NOT NULL,
                    binding_id TEXT NOT NULL,
                    binding_version TEXT NOT NULL,
                    started_at_ms INTEGER NOT NULL,
                    ended_at_ms INTEGER,
                    latency_ms INTEGER,
                    outcome TEXT NOT NULL,
                    technical_failure TEXT,
                    PRIMARY KEY(work_id, attempt_number)
                );
                CREATE INDEX IF NOT EXISTS idx_work_ready ON work(state, eligible_at_ms, urgency, created_at_ms);
                CREATE INDEX IF NOT EXISTS idx_attempt_capability ON attempts(capability_id, outcome, ended_at_ms);
                """;
            await schema.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }

        foreach (InferenceCapability capability in capabilities)
        {
            await UpsertCapabilityAsync(connection, capability, cancellationToken).ConfigureAwait(false);
        }

        long now = NowMs();
        await using (SqliteCommand recoverAttempts = connection.CreateCommand())
        {
            recoverAttempts.CommandText = """
                UPDATE attempts
                SET outcome = $unknown,
                    ended_at_ms = $now,
                    latency_ms = MAX(0, $now - started_at_ms),
                    technical_failure = 'KERNEL_RESTART_LOST_CERTAINTY'
                WHERE outcome = $running;
                """;
            recoverAttempts.Parameters.AddWithValue("$unknown", PhysicalAttemptOutcome.UnknownCompletion.ToString());
            recoverAttempts.Parameters.AddWithValue("$running", PhysicalAttemptOutcome.Running.ToString());
            recoverAttempts.Parameters.AddWithValue("$now", now);
            await recoverAttempts.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
        await using (SqliteCommand recoverWork = connection.CreateCommand())
        {
            recoverWork.CommandText = """
                UPDATE work
                SET state = $unknown,
                    failure_code = 'KERNEL_RESTART_LOST_CERTAINTY'
                WHERE state = $running;
                """;
            recoverWork.Parameters.AddWithValue("$unknown", WorkState.UnknownCompletion.ToString());
            recoverWork.Parameters.AddWithValue("$running", WorkState.Running.ToString());
            await recoverWork.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
        await using (SqliteCommand incompatible = connection.CreateCommand())
        {
            incompatible.CommandText = """
                UPDATE work
                SET state = $failed,
                    failure_code = 'INCOMPATIBLE_STRATEGY_VERSION'
                WHERE state = $queued
                  AND (strategy_type <> $strategyType OR strategy_version <> $strategyVersion);
                """;
            incompatible.Parameters.AddWithValue("$failed", WorkState.Failed.ToString());
            incompatible.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
            incompatible.Parameters.AddWithValue("$strategyType", KernelContract.StrategyType);
            incompatible.Parameters.AddWithValue("$strategyVersion", KernelContract.StrategyVersion);
            await incompatible.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }
    }

    public async Task<string> SubmitAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken = default)
    {
        string id = Guid.NewGuid().ToString("N");
        DateTimeOffset created = DateTimeOffset.UtcNow;
        DateTimeOffset eligible = request.EligibleAt ?? created;
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO work (
                work_id, state, prepared_input, requested_effort, urgency, eligible_at_ms, deadline_ms,
                execution_boundary, created_at_ms, strategy_type, strategy_version)
            VALUES ($id, $state, $input, $effort, $urgency, $eligible, $deadline, $boundary, $created, $strategyType, $strategyVersion);
            """;
        command.Parameters.AddWithValue("$id", id);
        command.Parameters.AddWithValue("$state", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$input", request.PreparedInput);
        command.Parameters.AddWithValue("$effort", request.RequestedEffort.ToString());
        command.Parameters.AddWithValue("$urgency", request.Urgency.ToString());
        command.Parameters.AddWithValue("$eligible", eligible.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$deadline", request.Deadline is null ? DBNull.Value : request.Deadline.Value.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$boundary", request.ExecutionBoundary.ToString());
        command.Parameters.AddWithValue("$created", created.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$strategyType", KernelContract.StrategyType);
        command.Parameters.AddWithValue("$strategyVersion", KernelContract.StrategyVersion);
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        return id;
    }

    internal async Task<IReadOnlyList<StoredWork>> GetEligibleWorkAsync(DateTimeOffset now, int limit, CancellationToken cancellationToken)
    {
        var result = new List<StoredWork>();
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            SELECT work_id, state, prepared_input, requested_effort, urgency, eligible_at_ms, deadline_ms,
                   execution_boundary, created_at_ms, strategy_type, strategy_version
            FROM work
            WHERE state = $queued AND eligible_at_ms <= $now AND released = 0
            ORDER BY CASE urgency WHEN 'Interactive' THEN 2 WHEN 'Normal' THEN 1 ELSE 0 END DESC,
                     created_at_ms ASC
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$now", now.ToUnixTimeMilliseconds());
        command.Parameters.AddWithValue("$limit", limit);
        await using SqliteDataReader reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        while (await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            string input = reader.IsDBNull(2) ? throw new InvalidOperationException("queued Work has released input") : reader.GetString(2);
            DateTimeOffset eligible = FromMs(reader.GetInt64(5));
            DateTimeOffset? deadline = reader.IsDBNull(6) ? null : FromMs(reader.GetInt64(6));
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
                FromMs(reader.GetInt64(8)),
                request,
                reader.GetString(9),
                reader.GetString(10)));
        }
        return result;
    }

    internal async Task ExpireQueuedDeadlinesAsync(DateTimeOffset now, CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            UPDATE work
            SET state = $failed, failure_code = 'DEADLINE_EXPIRED'
            WHERE state = $queued AND deadline_ms IS NOT NULL AND deadline_ms <= $now;
            """;
        command.Parameters.AddWithValue("$failed", WorkState.Failed.ToString());
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$now", now.ToUnixTimeMilliseconds());
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    internal async Task FailWithoutAttemptAsync(string workId, string failureCode, CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = "UPDATE work SET state = $failed, failure_code = $failure WHERE work_id = $id AND state = $queued;";
        command.Parameters.AddWithValue("$failed", WorkState.Failed.ToString());
        command.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        command.Parameters.AddWithValue("$failure", failureCode);
        command.Parameters.AddWithValue("$id", workId);
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    internal async Task<int?> TryBeginAttemptAsync(StoredWork work, InferenceCapability capability, DateTimeOffset startedAt, CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();
        await using SqliteCommand claim = connection.CreateCommand();
        claim.Transaction = transaction;
        claim.CommandText = """
            UPDATE work
            SET state = $running,
                selected_capability_id = $capability,
                selected_binding_id = $binding,
                selected_binding_version = $bindingVersion
            WHERE work_id = $id AND state = $queued;
            """;
        claim.Parameters.AddWithValue("$running", WorkState.Running.ToString());
        claim.Parameters.AddWithValue("$queued", WorkState.Queued.ToString());
        claim.Parameters.AddWithValue("$capability", capability.CapabilityId);
        claim.Parameters.AddWithValue("$binding", capability.BindingId);
        claim.Parameters.AddWithValue("$bindingVersion", capability.BindingVersion);
        claim.Parameters.AddWithValue("$id", work.WorkId);
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

        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        using SqliteTransaction transaction = connection.BeginTransaction();
        await using SqliteCommand attempt = connection.CreateCommand();
        attempt.Transaction = transaction;
        attempt.CommandText = """
            UPDATE attempts
            SET ended_at_ms = $ended, latency_ms = $latency, outcome = $outcome, technical_failure = $failure
            WHERE work_id = $id AND attempt_number = $number AND outcome = $running;
            """;
        attempt.Parameters.AddWithValue("$ended", endedAt.ToUnixTimeMilliseconds());
        attempt.Parameters.AddWithValue("$latency", latencyMs);
        attempt.Parameters.AddWithValue("$outcome", result.Outcome.ToString());
        attempt.Parameters.AddWithValue("$failure", result.TechnicalFailure is null ? DBNull.Value : result.TechnicalFailure);
        attempt.Parameters.AddWithValue("$id", workId);
        attempt.Parameters.AddWithValue("$number", attemptNumber);
        attempt.Parameters.AddWithValue("$running", PhysicalAttemptOutcome.Running.ToString());
        await attempt.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);

        await using SqliteCommand work = connection.CreateCommand();
        work.Transaction = transaction;
        work.CommandText = """
            UPDATE work
            SET state = $state, result_text = $result, failure_code = $failure
            WHERE work_id = $id AND state = $running;
            """;
        work.Parameters.AddWithValue("$state", workState.ToString());
        work.Parameters.AddWithValue("$result", result.Result is null ? DBNull.Value : result.Result);
        work.Parameters.AddWithValue("$failure", result.TechnicalFailure is null ? DBNull.Value : result.TechnicalFailure);
        work.Parameters.AddWithValue("$id", workId);
        work.Parameters.AddWithValue("$running", WorkState.Running.ToString());
        await work.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        transaction.Commit();
    }

    public async Task<WorkState?> CancelAsync(string workId, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
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
            update.CommandText = "UPDATE work SET state = $cancelled, cancel_requested = 1, failure_code = 'CANCELLED_BEFORE_DISPATCH' WHERE work_id = $id;";
            update.Parameters.AddWithValue("$cancelled", WorkState.Cancelled.ToString());
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
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
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
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand work = connection.CreateCommand();
        work.CommandText = """
            SELECT state, created_at_ms, eligible_at_ms, deadline_ms, urgency, strategy_type, strategy_version,
                   selected_capability_id, selected_binding_id, selected_binding_version,
                   cancel_requested, released, failure_code
            FROM work WHERE work_id = $id;
            """;
        work.Parameters.AddWithValue("$id", workId);
        await using SqliteDataReader reader = await work.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        if (!await reader.ReadAsync(cancellationToken).ConfigureAwait(false))
        {
            return null;
        }
        WorkState state = Enum.Parse<WorkState>(reader.GetString(0));
        DateTimeOffset created = FromMs(reader.GetInt64(1));
        DateTimeOffset eligible = FromMs(reader.GetInt64(2));
        DateTimeOffset? deadline = reader.IsDBNull(3) ? null : FromMs(reader.GetInt64(3));
        WorkUrgency urgency = Enum.Parse<WorkUrgency>(reader.GetString(4));
        string strategyType = reader.GetString(5);
        string strategyVersion = reader.GetString(6);
        string? selectedCapability = reader.IsDBNull(7) ? null : reader.GetString(7);
        string? selectedBinding = reader.IsDBNull(8) ? null : reader.GetString(8);
        string? selectedBindingVersion = reader.IsDBNull(9) ? null : reader.GetString(9);
        bool cancelRequested = reader.GetInt64(10) != 0;
        bool released = reader.GetInt64(11) != 0;
        string? failure = reader.IsDBNull(12) ? null : reader.GetString(12);
        await reader.DisposeAsync().ConfigureAwait(false);

        var attempts = new List<AttemptInspection>();
        await using SqliteCommand attempt = connection.CreateCommand();
        attempt.CommandText = """
            SELECT attempt_number, capability_id, binding_id, binding_version, started_at_ms,
                   ended_at_ms, latency_ms, outcome, technical_failure
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
                FromMs(attemptReader.GetInt64(4)),
                attemptReader.IsDBNull(5) ? null : FromMs(attemptReader.GetInt64(5)),
                attemptReader.IsDBNull(6) ? null : attemptReader.GetInt64(6),
                Enum.Parse<PhysicalAttemptOutcome>(attemptReader.GetString(7)),
                attemptReader.IsDBNull(8) ? null : attemptReader.GetString(8)));
        }

        return new WorkInspection(
            workId, state, created, eligible, deadline, urgency, strategyType, strategyVersion,
            selectedCapability, selectedBinding, selectedBindingVersion,
            cancelRequested, released, failure, attempts);
    }

    public async Task<WorkResultSnapshot?> GetResultAsync(string workId, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
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

    public async Task SetCapabilityStateAsync(string capabilityId, CapabilityAvailability availability, CancellationToken cancellationToken = default)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = "UPDATE capability_state SET availability = $availability, observed_at_ms = $now WHERE capability_id = $id;";
        command.Parameters.AddWithValue("$availability", availability.ToString());
        command.Parameters.AddWithValue("$now", NowMs());
        command.Parameters.AddWithValue("$id", capabilityId);
        if (await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false) != 1)
        {
            throw new KeyNotFoundException($"unknown capability {capabilityId}");
        }
    }

    public async Task<IReadOnlyList<CapabilitySnapshot>> GetCapabilitiesAsync(CancellationToken cancellationToken = default)
    {
        var result = new List<CapabilitySnapshot>();
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            SELECT c.capability_id, c.binding_id, c.binding_version,
                   c.execution_boundary, c.execution_boundary_source,
                   c.supported_effort, c.supported_effort_source,
                   c.owner_preference,
                   s.availability, s.observed_at_ms,
                   (SELECT AVG(a.latency_ms) FROM attempts a WHERE a.capability_id = c.capability_id AND a.outcome = $success),
                   (SELECT COUNT(*) FROM attempts a WHERE a.capability_id = c.capability_id AND a.outcome = $success),
                   (SELECT COUNT(*) FROM attempts a WHERE a.capability_id = c.capability_id AND a.outcome = $failure)
            FROM capabilities c
            JOIN capability_state s ON s.capability_id = c.capability_id
            ORDER BY c.capability_id;
            """;
        command.Parameters.AddWithValue("$success", PhysicalAttemptOutcome.Succeeded.ToString());
        command.Parameters.AddWithValue("$failure", PhysicalAttemptOutcome.DefiniteFailure.ToString());
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
                reader.IsDBNull(9) ? null : FromMs(reader.GetInt64(9)));
            double? latency = reader.IsDBNull(10) ? null : reader.GetDouble(10);
            result.Add(new CapabilitySnapshot(capability, state, latency, reader.GetInt32(11), reader.GetInt32(12)));
        }
        return result;
    }

    private async Task UpsertCapabilityAsync(SqliteConnection connection, InferenceCapability capability, CancellationToken cancellationToken)
    {
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO capabilities (
                capability_id, binding_id, binding_version,
                execution_boundary, execution_boundary_source,
                supported_effort, supported_effort_source,
                owner_preference)
            VALUES ($id, $binding, $bindingVersion, $boundary, $boundarySource, $effort, $effortSource, $preference)
            ON CONFLICT(capability_id) DO UPDATE SET
                binding_id = excluded.binding_id,
                binding_version = excluded.binding_version,
                execution_boundary = excluded.execution_boundary,
                execution_boundary_source = excluded.execution_boundary_source,
                supported_effort = excluded.supported_effort,
                supported_effort_source = excluded.supported_effort_source,
                owner_preference = excluded.owner_preference;
            """;
        command.Parameters.AddWithValue("$id", capability.CapabilityId);
        command.Parameters.AddWithValue("$binding", capability.BindingId);
        command.Parameters.AddWithValue("$bindingVersion", capability.BindingVersion);
        command.Parameters.AddWithValue("$boundary", capability.ExecutionBoundary.Value.ToString());
        command.Parameters.AddWithValue("$boundarySource", capability.ExecutionBoundary.Provenance.ToString());
        command.Parameters.AddWithValue("$effort", capability.SupportedEffort.Value.ToString());
        command.Parameters.AddWithValue("$effortSource", capability.SupportedEffort.Provenance.ToString());
        command.Parameters.AddWithValue("$preference", capability.OwnerPreference);
        await command.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);

        await using SqliteCommand state = connection.CreateCommand();
        state.CommandText = """
            INSERT INTO capability_state (capability_id, availability, observed_at_ms)
            VALUES ($id, $availability, NULL)
            ON CONFLICT(capability_id) DO NOTHING;
            """;
        state.Parameters.AddWithValue("$id", capability.CapabilityId);
        state.Parameters.AddWithValue("$availability", CapabilityAvailability.Unknown.ToString());
        await state.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    private async Task<SqliteConnection> OpenAsync(CancellationToken cancellationToken)
    {
        var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand pragmas = connection.CreateCommand();
        pragmas.CommandText = "PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;";
        await pragmas.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        return connection;
    }

    private static long NowMs() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    private static DateTimeOffset FromMs(long value) => DateTimeOffset.FromUnixTimeMilliseconds(value);
}
