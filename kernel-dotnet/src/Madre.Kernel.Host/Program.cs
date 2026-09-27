using Madre.Kernel;
using Madre.Kernel.Host;

string database = Get(args, "--db") ?? Path.Combine(AppContext.BaseDirectory, "madre-kernel.db");
int port = int.Parse(Get(args, "--port") ?? "5187");
int maxConcurrent = int.Parse(Get(args, "--max-concurrent") ?? "2");

await KernelWebHost.RunAsync(
    database,
    port,
    maxConcurrent,
    Array.Empty<InferenceCapability>(),
    Array.Empty<IInferenceBinding>());

static string? Get(string[] values, string key)
{
    int index = Array.IndexOf(values, key);
    return index >= 0 && index + 1 < values.Length ? values[index + 1] : null;
}
