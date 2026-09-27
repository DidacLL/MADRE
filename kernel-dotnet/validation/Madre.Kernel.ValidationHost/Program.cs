using System.Runtime.CompilerServices;
using Madre.Kernel;
using Madre.Kernel.Host;
using Microsoft.Extensions.AI;

string database = Required(args, "--db");
string fixture = Required(args, "--fixture");
int port = int.Parse(Required(args, "--port"));
int maxConcurrent = int.Parse(Get(args, "--max-concurrent") ?? "1");
string dotnet = Environment.GetEnvironmentVariable("DOTNET_HOST_PATH") ?? "dotnet";

var capabilities = new[]
{
    new InferenceCapability(
        "meai-fast",
        "meai/test",
        "1",
        new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.ExternalAllowed, FactProvenance.Owner),
        new ConfiguredFact<InferenceEffort>(InferenceEffort.High, FactProvenance.ProviderOrRuntime),
        10),
    new InferenceCapability(
        "process-local",
        "process/test",
        "1",
        new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.LocalOnly, FactProvenance.Owner),
        new ConfiguredFact<InferenceEffort>(InferenceEffort.Standard, FactProvenance.ProviderOrRuntime),
        100),
    new InferenceCapability(
        "owner-custom",
        "owner/custom",
        "1",
        new ConfiguredFact<ExecutionBoundary>(ExecutionBoundary.LocalOnly, FactProvenance.Owner),
        new ConfiguredFact<InferenceEffort>(InferenceEffort.Low, FactProvenance.Owner),
        50)
};

IInferenceBinding[] bindings =
[
    new MeaiInferenceBinding("meai/test", "1", new DeterministicChatClient()),
    new ProcessInferenceBinding("process/test", "1", dotnet, [fixture]),
    new OwnerCustomBinding()
];

await KernelWebHost.RunAsync(database, port, maxConcurrent, capabilities, bindings);

static string Required(string[] values, string key) => Get(values, key) ?? throw new ArgumentException($"missing {key}");
static string? Get(string[] values, string key)
{
    int index = Array.IndexOf(values, key);
    return index >= 0 && index + 1 < values.Length ? values[index + 1] : null;
}

sealed class DeterministicChatClient : IChatClient
{
    public ChatClientMetadata Metadata { get; } = new(nameof(DeterministicChatClient), new Uri("http://127.0.0.1"), "deterministic-test");

    public async Task<ChatResponse> GetResponseAsync(
        IEnumerable<ChatMessage> messages,
        ChatOptions? options = null,
        CancellationToken cancellationToken = default)
    {
        string input = string.Concat(messages.Select(message => message.Text));
        int delay = input.StartsWith("MEAI_SLOW", StringComparison.Ordinal) ? 2000 : 40;
        await Task.Delay(delay, cancellationToken).ConfigureAwait(false);
        return new ChatResponse(new ChatMessage(ChatRole.Assistant, "meai:" + input));
    }

    public async IAsyncEnumerable<ChatResponseUpdate> GetStreamingResponseAsync(
        IEnumerable<ChatMessage> messages,
        ChatOptions? options = null,
        [EnumeratorCancellation] CancellationToken cancellationToken = default)
    {
        ChatResponse response = await GetResponseAsync(messages, options, cancellationToken).ConfigureAwait(false);
        yield return new ChatResponseUpdate(ChatRole.Assistant, response.Text);
    }

    public object? GetService(Type serviceType, object? serviceKey = null) => serviceType.IsInstanceOfType(this) ? this : null;
    public void Dispose() { }
}

sealed class OwnerCustomBinding : IInferenceBinding
{
    public string BindingId => "owner/custom";
    public string BindingVersion => "1";

    public async Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken)
    {
        try
        {
            await Task.Delay(90, cancellationToken).ConfigureAwait(false);
            string result = request.PreparedInput == "NONSENSE"
                ? "nonsense-but-physically-valid"
                : "owner:" + request.PreparedInput;
            return BindingExecutionResult.Success(result);
        }
        catch (OperationCanceledException)
        {
            return BindingExecutionResult.Unknown("OWNER_CUSTOM_CANCELLATION_COMPLETION_UNKNOWN");
        }
    }
}
