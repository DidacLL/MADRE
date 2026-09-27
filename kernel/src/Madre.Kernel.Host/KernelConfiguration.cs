using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;

namespace Madre.Kernel.Host;

public static class KernelHostDefaults
{
    public const int MaxConcurrent = 2;
}

public sealed class KernelHostConfiguration
{
    public string? DatabasePath { get; init; }
    public int? MaxConcurrent { get; init; }
    public List<ProcessCapabilityConfiguration>? Capabilities { get; init; } = [];
}

public sealed class ProcessCapabilityConfiguration
{
    public string? CapabilityId { get; init; }
    public string? BindingId { get; init; }
    public string? BindingVersion { get; init; }
    public string? Executable { get; init; }
    public List<string>? Arguments { get; init; } = [];
    public List<string>? ProbeArguments { get; init; }
    public ExecutionBoundary? ExecutionBoundary { get; init; }
    public InferenceEffort? SupportedEffort { get; init; }
    public int? OwnerPreference { get; init; }
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
    private static readonly JsonSerializerOptions Json = CreateJson();

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
            try
            {
                configured = JsonSerializer.Deserialize<KernelHostConfiguration>(File.ReadAllText(fullPath), Json)
                    ?? throw new InvalidDataException("Kernel configuration is empty");
            }
            catch (JsonException ex)
            {
                throw new InvalidDataException($"Kernel configuration is invalid: {ex.Message}", ex);
            }
        }

        if (configured.Capabilities is null)
        {
            throw new InvalidDataException("capabilities must be an array when present");
        }

        int maxConcurrent = maxConcurrentOverride ?? configured.MaxConcurrent ?? KernelHostDefaults.MaxConcurrent;
        if (maxConcurrent < 1)
        {
            throw new InvalidDataException("maxConcurrent must be at least 1");
        }

        if (configured.DatabasePath is not null && string.IsNullOrWhiteSpace(configured.DatabasePath))
        {
            throw new InvalidDataException("databasePath must not be empty when present");
        }
        if (databaseOverride is not null && string.IsNullOrWhiteSpace(databaseOverride))
        {
            throw new InvalidDataException("database path override must not be empty");
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
            if (entry.ExecutionBoundary is null
                || entry.SupportedEffort is null
                || entry.OwnerPreference is null)
            {
                throw new InvalidDataException(
                    $"capability {entry.CapabilityId} requires executionBoundary, supportedEffort and ownerPreference");
            }
            if (entry.Arguments is null || entry.Arguments.Any(argument => argument is null))
            {
                throw new InvalidDataException($"capability {entry.CapabilityId} arguments must be a string array");
            }
            if (entry.ProbeArguments is not null && entry.ProbeArguments.Any(argument => argument is null))
            {
                throw new InvalidDataException($"capability {entry.CapabilityId} probeArguments must be a string array");
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
                new ConfiguredFact<ExecutionBoundary>(entry.ExecutionBoundary.Value, FactProvenance.Owner),
                new ConfiguredFact<InferenceEffort>(entry.SupportedEffort.Value, FactProvenance.Owner),
                entry.OwnerPreference.Value));
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

    private static JsonSerializerOptions CreateJson()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = false,
            UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow
        };
        options.Converters.Add(new JsonStringEnumConverter(namingPolicy: null, allowIntegerValues: false));
        return options;
    }
}
