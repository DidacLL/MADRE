using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;

namespace Madre.Kernel.Host;

public sealed class KernelHostConfiguration
{
    public int Port { get; init; } = 5187;
    public string DatabasePath { get; init; } = "./madre-kernel.db";
    public int MaxConcurrent { get; init; } = 2;
    public List<ProcessCapabilityConfiguration> Capabilities { get; init; } = [];
}

public sealed class ProcessCapabilityConfiguration
{
    public required string CapabilityId { get; init; }
    public required string BindingId { get; init; }
    public string BindingVersion { get; init; } = "1";
    public required string Executable { get; init; }
    public List<string> Arguments { get; init; } = [];
    public List<string>? ProbeArguments { get; init; }
    public ExecutionBoundary ExecutionBoundary { get; init; } = ExecutionBoundary.LocalOnly;
    public InferenceEffort SupportedEffort { get; init; } = InferenceEffort.Standard;
    public int OwnerPreference { get; init; }
}

public sealed record LoadedKernelConfiguration(
    string DatabasePath,
    int Port,
    int MaxConcurrent,
    IReadOnlyList<InferenceCapability> Capabilities,
    IReadOnlyList<IInferenceBinding> Bindings);

public static class KernelConfigurationLoader
{
    public static LoadedKernelConfiguration Load(
        string configurationPath,
        string? databaseOverride = null,
        int? portOverride = null,
        int? maxConcurrentOverride = null)
    {
        string fullPath = Path.GetFullPath(configurationPath);
        if (!File.Exists(fullPath))
        {
            throw new FileNotFoundException("Kernel configuration file not found", fullPath);
        }

        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = true
        };
        options.Converters.Add(new JsonStringEnumConverter());
        KernelHostConfiguration configured = JsonSerializer.Deserialize<KernelHostConfiguration>(File.ReadAllText(fullPath), options)
            ?? throw new InvalidDataException("Kernel configuration is empty");

        int port = portOverride ?? configured.Port;
        int maxConcurrent = maxConcurrentOverride ?? configured.MaxConcurrent;
        if (port is < 1 or > 65535)
        {
            throw new InvalidDataException("port must be between 1 and 65535");
        }
        if (maxConcurrent < 1)
        {
            throw new InvalidDataException("maxConcurrent must be at least 1");
        }
        if (configured.Capabilities.Count == 0)
        {
            throw new InvalidDataException("at least one inference capability must be configured");
        }

        string root = Path.GetDirectoryName(fullPath)!;
        string database = databaseOverride ?? configured.DatabasePath;
        database = Path.IsPathRooted(database) ? database : Path.Combine(root, database);

        var capabilities = new List<InferenceCapability>(configured.Capabilities.Count);
        var bindings = new List<IInferenceBinding>(configured.Capabilities.Count);
        var capabilityIds = new HashSet<string>(StringComparer.Ordinal);
        var bindingKeys = new HashSet<string>(StringComparer.Ordinal);

        foreach (ProcessCapabilityConfiguration entry in configured.Capabilities)
        {
            if (string.IsNullOrWhiteSpace(entry.CapabilityId)
                || string.IsNullOrWhiteSpace(entry.BindingId)
                || string.IsNullOrWhiteSpace(entry.BindingVersion)
                || string.IsNullOrWhiteSpace(entry.Executable))
            {
                throw new InvalidDataException("capabilityId, bindingId, bindingVersion and executable are required");
            }
            if (!capabilityIds.Add(entry.CapabilityId))
            {
                throw new InvalidDataException($"duplicate capabilityId: {entry.CapabilityId}");
            }
            string bindingKey = $"{entry.BindingId}@{entry.BindingVersion}";
            if (!bindingKeys.Add(bindingKey))
            {
                throw new InvalidDataException($"duplicate binding identity: {bindingKey}");
            }

            capabilities.Add(new InferenceCapability(
                entry.CapabilityId,
                entry.BindingId,
                entry.BindingVersion,
                new ConfiguredFact<ExecutionBoundary>(entry.ExecutionBoundary, FactProvenance.Owner),
                new ConfiguredFact<InferenceEffort>(entry.SupportedEffort, FactProvenance.Owner),
                entry.OwnerPreference));
            bindings.Add(new ProcessInferenceBinding(
                entry.BindingId,
                entry.BindingVersion,
                entry.Executable,
                entry.Arguments,
                entry.ProbeArguments));
        }

        return new LoadedKernelConfiguration(
            Path.GetFullPath(database),
            port,
            maxConcurrent,
            capabilities,
            bindings);
    }
}
