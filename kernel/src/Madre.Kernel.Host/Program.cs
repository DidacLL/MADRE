using System.Globalization;
using Madre.Kernel.Host;

Dictionary<string, string> options = ParseArguments(args);
string? configPath = Get(options, "--config");
string? database = Get(options, "--db");
string ipcPath = Get(options, "--ipc-path") ?? KernelPaths.DefaultIpcPath;
int? maxConcurrent = ParseOptionalInt(options, "--max-concurrent");

LoadedKernelConfiguration configuration = KernelConfigurationLoader.Load(
    configPath,
    database,
    maxConcurrent);

await KernelIpcServer.RunAsync(
    configuration.DatabasePath,
    Path.GetFullPath(ipcPath),
    configuration.MaxConcurrent,
    configuration.Capabilities,
    configuration.Bindings);

static Dictionary<string, string> ParseArguments(string[] values)
{
    var allowed = new HashSet<string>(StringComparer.Ordinal)
    {
        "--config",
        "--db",
        "--ipc-path",
        "--max-concurrent"
    };
    var parsed = new Dictionary<string, string>(StringComparer.Ordinal);
    for (int i = 0; i < values.Length; i++)
    {
        string key = values[i];
        if (!allowed.Contains(key))
        {
            throw new InvalidDataException($"unknown Kernel argument: {key}");
        }
        if (parsed.ContainsKey(key))
        {
            throw new InvalidDataException($"duplicate Kernel argument: {key}");
        }
        if (i + 1 >= values.Length || values[i + 1].StartsWith("--", StringComparison.Ordinal))
        {
            throw new InvalidDataException($"Kernel argument {key} requires a value");
        }
        parsed.Add(key, values[++i]);
    }
    return parsed;
}

static int? ParseOptionalInt(IReadOnlyDictionary<string, string> values, string key)
{
    string? raw = Get(values, key);
    if (raw is null)
    {
        return null;
    }
    if (!int.TryParse(raw, NumberStyles.None, CultureInfo.InvariantCulture, out int value))
    {
        throw new InvalidDataException($"Kernel argument {key} requires an integer value");
    }
    return value;
}

static string? Get(IReadOnlyDictionary<string, string> values, string key) =>
    values.TryGetValue(key, out string? value) ? value : null;
