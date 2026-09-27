using Madre.Kernel;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static async Task PersistenceAndSchedulingShapeAsync()
    {
        using var temp = new TempDir("madre-schema-shape");
        string incompatible = Path.Combine(temp.Path, "pre-release.db");
        await using (var db = new SqliteConnection($"Data Source={incompatible}"))
        {
            await db.OpenAsync();
            await using SqliteCommand command = db.CreateCommand();
            command.CommandText = "CREATE TABLE work (legacy TEXT);";
            await command.ExecuteNonQueryAsync();
        }

        bool rejected = false;
        try
        {
            await new WorkStore(incompatible).InitializeAsync([], DateTimeOffset.UtcNow);
        }
        catch (InvalidDataException ex) when (ex.Message.Contains("incompatible pre-release", StringComparison.OrdinalIgnoreCase))
        {
            rejected = true;
        }
        Check(rejected, "unversioned incompatible pre-release database was treated as current schema");

        string current = Path.Combine(temp.Path, "current.db");
        await new WorkStore(current).InitializeAsync([], DateTimeOffset.UtcNow);
        await using (var db = new SqliteConnection($"Data Source={current}"))
        {
            await db.OpenAsync();
            await using SqliteCommand command = db.CreateCommand();
            command.CommandText = "PRAGMA user_version;";
            Check(Convert.ToInt32(await command.ExecuteScalarAsync()) == 1,
                "current database did not persist explicit schema identity");
        }

        string store = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "WorkStore.cs"));
        int eligibleStart = store.IndexOf("GetEligibleWorkAsync", StringComparison.Ordinal);
        int eligibleEnd = store.IndexOf("GetNextSchedulingBoundaryAsync", eligibleStart, StringComparison.Ordinal);
        Check(eligibleStart >= 0 && eligibleEnd > eligibleStart, "eligible scheduling query not found");
        string eligibleSection = store[eligibleStart..eligibleEnd];
        int claimStart = store.IndexOf("TryBeginAttemptAsync", StringComparison.Ordinal);
        Check(claimStart >= 0
            && !eligibleSection.Contains("prepared_input", StringComparison.Ordinal)
            && store[claimStart..].Contains("SELECT prepared_input", StringComparison.Ordinal),
            "scheduler still materializes physical payloads before claim");

        string sqlite = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "SqliteDatabase.cs"));
        Check(sqlite.Contains("PRAGMA user_version", StringComparison.Ordinal)
            && !sqlite.Contains("CREATE TABLE IF NOT EXISTS", StringComparison.OrdinalIgnoreCase),
            "schema identity is still pretending incompatible layouts are current");
        string engine = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel", "KernelEngine.cs"));
        string hostConfig = File.ReadAllText(Path.Combine(Root, "kernel", "src", "Madre.Kernel.Host", "KernelConfiguration.cs"));
        Check(!engine.Contains("int maxConcurrent =", StringComparison.Ordinal)
            && hostConfig.Split("MaxConcurrent = 2", StringSplitOptions.None).Length - 1 == 1,
            "maxConcurrent default is still owned in more than one place");
        Console.WriteLine("PASS explicit schema identity, metadata-only scheduling, and single default ownership");
    }
}
