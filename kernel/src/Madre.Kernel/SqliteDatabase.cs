using Microsoft.Data.Sqlite;

namespace Madre.Kernel;

internal sealed class SqliteDatabase
{
    private const int CurrentSchemaVersion = 1;
    private const int BusyTimeoutMilliseconds = 5_000;
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

        await using (SqliteCommand wal = connection.CreateCommand())
        {
            wal.CommandText = "PRAGMA journal_mode=WAL;";
            await wal.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        }

        int version;
        await using (SqliteCommand identity = connection.CreateCommand())
        {
            identity.CommandText = "PRAGMA user_version;";
            version = Convert.ToInt32(await identity.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false));
        }

        if (version == 0)
        {
            await using SqliteCommand existing = connection.CreateCommand();
            existing.CommandText = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';";
            int tableCount = Convert.ToInt32(await existing.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false));
            if (tableCount != 0)
            {
                throw new InvalidDataException(
                    "Kernel database is an incompatible pre-release schema without current schema identity; no migration is performed");
            }

            using SqliteTransaction transaction = connection.BeginTransaction();
            await using SqliteCommand schema = connection.CreateCommand();
            schema.Transaction = transaction;
            schema.CommandText = """
                CREATE TABLE capabilities (
                    capability_id TEXT PRIMARY KEY,
                    binding_id TEXT NOT NULL,
                    binding_version TEXT NOT NULL,
                    execution_boundary TEXT NOT NULL,
                    execution_boundary_source TEXT NOT NULL,
                    supported_effort TEXT NOT NULL,
                    supported_effort_source TEXT NOT NULL,
                    owner_preference INTEGER NOT NULL
                );
                CREATE TABLE capability_state (
                    capability_id TEXT PRIMARY KEY REFERENCES capabilities(capability_id) ON DELETE CASCADE,
                    availability TEXT NOT NULL,
                    observed_at_ms INTEGER
                );
                CREATE TABLE work (
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
                CREATE TABLE attempts (
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
                CREATE INDEX idx_work_ready ON work(state, eligible_at_ms, created_at_ms);
                CREATE INDEX idx_attempt_capability_binding
                    ON attempts(capability_id, binding_id, binding_version, outcome, ended_at_ms);
                PRAGMA user_version=1;
                """;
            await schema.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
            transaction.Commit();
            return;
        }

        if (version != CurrentSchemaVersion)
        {
            throw new InvalidDataException(
                $"Kernel database schema version {version} is incompatible with current version {CurrentSchemaVersion}; no migration is performed");
        }

        await using SqliteCommand verify = connection.CreateCommand();
        verify.CommandText = """
            SELECT COUNT(*)
            FROM sqlite_master
            WHERE type='table' AND name IN ('capabilities', 'capability_state', 'work', 'attempts');
            """;
        int expectedTables = Convert.ToInt32(await verify.ExecuteScalarAsync(cancellationToken).ConfigureAwait(false));
        if (expectedTables != 4)
        {
            throw new InvalidDataException(
                $"Kernel database claims schema version {CurrentSchemaVersion} but required tables are missing");
        }
    }

    public async Task<SqliteConnection> OpenAsync(CancellationToken cancellationToken)
    {
        var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using SqliteCommand pragmas = connection.CreateCommand();
        pragmas.CommandText = $"PRAGMA foreign_keys=ON; PRAGMA busy_timeout={BusyTimeoutMilliseconds};";
        await pragmas.ExecuteNonQueryAsync(cancellationToken).ConfigureAwait(false);
        return connection;
    }

    public static DateTimeOffset FromMs(long value) => DateTimeOffset.FromUnixTimeMilliseconds(value);
}

public sealed class KernelDatabaseLease : IDisposable
{
    private readonly FileStream _stream;
    private bool _disposed;

    private KernelDatabaseLease(FileStream stream)
    {
        _stream = stream;
    }

    public static KernelDatabaseLease Acquire(string databasePath)
    {
        if (OperatingSystem.IsMacOS())
        {
            throw new PlatformNotSupportedException("MADRE Kernel database ownership is supported on Windows and Linux");
        }

        string database = Path.GetFullPath(databasePath);
        string? directory = Path.GetDirectoryName(database);
        if (directory is null)
        {
            throw new InvalidOperationException("Kernel database path has no parent directory");
        }
        Directory.CreateDirectory(directory);
        string lockPath = database + ".owner.lock";
        var stream = new FileStream(
            lockPath,
            FileMode.OpenOrCreate,
            FileAccess.ReadWrite,
            FileShare.ReadWrite,
            bufferSize: 1,
            FileOptions.None);
        try
        {
            if (stream.Length == 0)
            {
                stream.SetLength(1);
            }
            stream.Lock(0, 1);
            return new KernelDatabaseLease(stream);
        }
        catch (IOException ex)
        {
            stream.Dispose();
            throw new InvalidOperationException(
                $"Kernel database already has an active process owner: {database}",
                ex);
        }
        catch
        {
            stream.Dispose();
            throw;
        }
    }

    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }
        _disposed = true;
        if (!OperatingSystem.IsMacOS())
        {
            try
            {
                _stream.Unlock(0, 1);
            }
            catch (IOException)
            {
            }
        }
        _stream.Dispose();
    }
}
