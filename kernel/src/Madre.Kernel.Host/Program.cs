using Madre.Kernel.Host;

string? configPath = Get(args, "--config");
string? database = Get(args, "--db");
string ipcPath = Get(args, "--ipc-path") ?? KernelPaths.DefaultIpcPath;
int? maxConcurrent = ParseOptionalInt(args, "--max-concurrent");

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

static int? ParseOptionalInt(string[] values, string key)
{
    string? raw = Get(values, key);
    return raw is null ? null : int.Parse(raw);
}

static string? Get(string[] values, string key)
{
    int index = Array.IndexOf(values, key);
    return index >= 0 && index + 1 < values.Length ? values[index + 1] : null;
}
