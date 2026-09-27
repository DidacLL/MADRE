using Microsoft.Data.Sqlite;

namespace Madre.Kernel;

internal sealed class SqliteDatabase
{
    private readonly string _connectionString;

    public SqliteDatabase(string databasePath)
    {
        DatabasePath = Path.GetFullPath(databasePath);
        Directory.CreateDirectory(Path.GetDirectoryName(DatabasePath)!);
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = DatabasePath,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Cache = SqliteCacheMode.Shared,
            Pooling = true
        }.ToString();
    }

    public string DatabasePath { get; }

    public async Task InitializeSchemaAsync(CancellationToken cancellationToken)
    {
        await using SqliteConnection connection = await OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand schema = connection.CreateCommand();
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
                selected_capability_id TEXT,
                selected_binding_id TEXT,
                selected_binding_version TEXT,
                cancel_requested INTEGER NOT NULL DEFAULT 0,
                result_text TEXT,
                failure_kind TEXT,
                failure_detail TEXT,
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
                failure_kind TEXT,
                failure_detail TEXT,
                PRIMARY KEY(work_id, attempt_number)
            );
            CREATE INDEX IF NOT EXISTS idx_work_ready ON work(state, eligible_at_ms, created_at_ms);
            CREATE INDEX IF NOT EXISTS idx_attempt_capability ON attempts(capability_id, outcome, ended_at_ms);
            """;
        await schema.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
    }

    public async Task<SqliteConnection> OpenAsync(CancellationToken cancellationToken)
    {
        var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand pragmas = connection.CreateCommand();
        pragmas.CommandText = "PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;";
        await pragmas.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        return connection;
    }

    public static DateTimeOffset FromMs(long value) => DateTimeOffset.FromUnixTimeMilliseconds(value);
}
