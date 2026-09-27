using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;

namespace Madre.Kernel.Host;

public sealed class KernelHostConfiguration
{
    public string? DatabasePath { get; init; }
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
    int MaxConcurrent,
    IReadOnlyList<InferenceCapability> Capabilities,
    IReadOnlyList<IInferenceBinding> Bindings);

public static class KernelPaths
{
    public static string UserDataDirectory
    {
        get
        {
            string root = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            if (string.IsNullOrWhiteSpace(root))
            {
                root = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            }
            return Path.Combine(root, "MADRE", "kernel");
        }
    }

    public static string DefaultDatabasePath => Path.Combine(UserDataDirectory, "kernel.db");
    public static string DefaultIpcPath => Path.Combine(UserDataDirectory, $"kernel-v{KernelProtocol.Version}.sock");
}

public static class KernelConfigurationLoader
{
    public static LoadedKernelConfiguration Load(
        string? configurationPath,
        string? databaseOverride = null,
        int? maxConcurrentOverride = null)
    {
        KernelHostConfiguration configured;
        string? configurationDirectory = null;
        if (string.IsNullOrWhiteSpace(configurationPath))
        {
            configured = new KernelHostConfiguration();
        }
        else
        {
            string fullPath = Path.GetFullPath(configurationPath);
            if (!File.Exists(fullPath))
            {
                throw new FileNotFoundException("Kernel configuration file not found", fullPath);
            }
            configurationDirectory = Path.GetDirectoryName(fullPath)!;
            var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
            {
                PropertyNameCaseInsensitive = true
            };
            options.Converters.Add(new JsonStringEnumConverter());
            configured = JsonSerializer.Deserialize<KernelHostConfiguration>(File.ReadAllText(fullPath), options)
                ?? throw new InvalidDataException("Kernel configuration is empty");
        }

        int maxConcurrent = maxConcurrentOverride ?? configured.MaxConcurrent;
        if (maxConcurrent < 1)
        {
            throw new InvalidDataException("maxConcurrent must be at least 1");
        }

        string database = databaseOverride ?? configured.DatabasePath ?? KernelPaths.DefaultDatabasePath;
        if (!Path.IsPathRooted(database) && configurationDirectory is not null)
        {
            database = Path.Combine(configurationDirectory, database);
        }
        database = Path.GetFullPath(database);

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
            database,
            maxConcurrent,
            capabilities,
            bindings);
    }
}
