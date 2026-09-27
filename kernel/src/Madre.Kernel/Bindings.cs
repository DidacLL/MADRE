using System.ComponentModel;
using System.Diagnostics;
using System.Text;
using Microsoft.Extensions.AI;

namespace Madre.Kernel;

public sealed record BindingExecutionResult(
    PhysicalAttemptOutcome Outcome,
    string? Result = null,
    string? TechnicalFailure = null)
{
    public static BindingExecutionResult Success(string result) => new(PhysicalAttemptOutcome.Succeeded, result);
    public static BindingExecutionResult Failure(string failure) => new(PhysicalAttemptOutcome.DefiniteFailure, null, failure);
    public static BindingExecutionResult Cancelled(string failure) => new(PhysicalAttemptOutcome.ConfirmedCancelled, null, failure);
    public static BindingExecutionResult Unknown(string failure) => new(PhysicalAttemptOutcome.UnknownCompletion, null, failure);
}

public interface IInferenceBinding
{
    string BindingId { get; }
    string BindingVersion { get; }
    Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken);
    Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken);
}

public sealed class MeaiInferenceBinding : IInferenceBinding
{
    private readonly IChatClient _chatClient;
    private readonly Func<CancellationToken, Task<CapabilityAvailability>>? _probe;

    public MeaiInferenceBinding(
        string bindingId,
        string bindingVersion,
        IChatClient chatClient,
        Func<CancellationToken, Task<CapabilityAvailability>>? probe = null)
    {
        BindingId = bindingId;
        BindingVersion = bindingVersion;
        _chatClient = chatClient;
        _probe = probe;
    }

    public string BindingId { get; }
    public string BindingVersion { get; }

    public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken) =>
        _probe is null
            ? Task.FromResult(CapabilityAvailability.Unknown)
            : _probe(cancellationToken);

    public async Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken)
    {
        try
        {
            ChatResponse response = await _chatClient.GetResponseAsync(request.PreparedInput, cancellationToken: cancellationToken)
                .ConfigureAwait(false);
            if (Encoding.UTF8.GetByteCount(response.Text) > KernelContract.MaxPayloadBytes)
            {
                return BindingExecutionResult.Failure("OUTPUT_LIMIT_EXCEEDED");
            }
            return BindingExecutionResult.Success(response.Text);
        }
        catch (OperationCanceledException)
        {
            return BindingExecutionResult.Unknown("MEAI_CANCELLATION_COMPLETION_UNKNOWN");
        }
        catch (Exception ex)
        {
            return BindingExecutionResult.Unknown($"MEAI_COMPLETION_UNKNOWN:{ex.GetType().Name}");
        }
    }
}

public sealed class ProcessInferenceBinding : IInferenceBinding
{
    private static readonly TimeSpan ProbeTimeout = TimeSpan.FromSeconds(3);
    private readonly string _executable;
    private readonly IReadOnlyList<string> _arguments;
    private readonly IReadOnlyList<string>? _probeArguments;

    public ProcessInferenceBinding(
        string bindingId,
        string bindingVersion,
        string executable,
        IReadOnlyList<string>? arguments = null,
        IReadOnlyList<string>? probeArguments = null)
    {
        BindingId = bindingId;
        BindingVersion = bindingVersion;
        _executable = executable;
        _arguments = arguments ?? Array.Empty<string>();
        _probeArguments = probeArguments;
    }

    public string BindingId { get; }
    public string BindingVersion { get; }

    public async Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken)
    {
        if (_probeArguments is null)
        {
            return CapabilityAvailability.Unknown;
        }

        using var process = NewProcess(redirectStreams: false);
        foreach (string argument in _probeArguments)
        {
            process.StartInfo.ArgumentList.Add(argument);
        }

        try
        {
            if (!process.Start())
            {
                return CapabilityAvailability.Unavailable;
            }
        }
        catch (Win32Exception)
        {
            return CapabilityAvailability.Unavailable;
        }
        catch (InvalidOperationException)
        {
            return CapabilityAvailability.Unavailable;
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(ProbeTimeout);
        try
        {
            await process.WaitForExitAsync(timeout.Token).ConfigureAwait(false);
            return process.ExitCode == 0
                ? CapabilityAvailability.Available
                : CapabilityAvailability.Unavailable;
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return CapabilityAvailability.Unavailable;
        }
        catch (OperationCanceledException)
        {
            return CapabilityAvailability.Unknown;
        }
    }

    public async Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken)
    {
        using var process = NewProcess(redirectStreams: true);
        foreach (string argument in _arguments)
        {
            process.StartInfo.ArgumentList.Add(argument);
        }

        try
        {
            if (!process.Start())
            {
                return BindingExecutionResult.Failure("PROCESS_LAUNCH_FAILED");
            }
        }
        catch (Win32Exception)
        {
            return BindingExecutionResult.Failure("PROCESS_LAUNCH_FAILED");
        }
        catch (InvalidOperationException)
        {
            return BindingExecutionResult.Failure("PROCESS_LAUNCH_FAILED");
        }

        try
        {
            Task<string> stdout = ReadBoundedAsync(process.StandardOutput, KernelContract.MaxPayloadBytes, cancellationToken);
            Task<string> stderr = ReadBoundedAsync(process.StandardError, KernelContract.MaxPayloadBytes, cancellationToken);

            await process.StandardInput.WriteAsync(request.PreparedInput.AsMemory(), cancellationToken).ConfigureAwait(false);
            await process.StandardInput.FlushAsync(cancellationToken).ConfigureAwait(false);
            process.StandardInput.Close();

            await process.WaitForExitAsync(cancellationToken).ConfigureAwait(false);
            string output = await stdout.ConfigureAwait(false);
            _ = await stderr.ConfigureAwait(false);

            if (process.ExitCode != 0)
            {
                return BindingExecutionResult.Failure($"PROCESS_EXIT_{process.ExitCode}");
            }
            return BindingExecutionResult.Success(output);
        }
        catch (OperationCanceledException)
        {
            bool confirmed = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return confirmed
                ? BindingExecutionResult.Cancelled("PROCESS_CANCELLED_CONFIRMED")
                : BindingExecutionResult.Unknown("PROCESS_CANCELLATION_COMPLETION_UNKNOWN");
        }
        catch (InvalidDataException)
        {
            _ = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Failure("OUTPUT_LIMIT_EXCEEDED");
        }
        catch (IOException)
        {
            _ = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Failure("PROCESS_IO_FAILURE");
        }
    }

    private Process NewProcess(bool redirectStreams) => new()
    {
        StartInfo = new ProcessStartInfo
        {
            FileName = _executable,
            RedirectStandardInput = redirectStreams,
            RedirectStandardOutput = redirectStreams,
            RedirectStandardError = redirectStreams,
            UseShellExecute = false,
            CreateNoWindow = true
        }
    };

    private static async Task<string> ReadBoundedAsync(StreamReader reader, int maxBytes, CancellationToken cancellationToken)
    {
        var builder = new StringBuilder();
        var buffer = new char[4096];
        int bytes = 0;
        while (true)
        {
            int read = await reader.ReadAsync(buffer.AsMemory(), cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                return builder.ToString();
            }
            bytes += Encoding.UTF8.GetByteCount(buffer, 0, read);
            if (bytes > maxBytes)
            {
                throw new InvalidDataException("process output exceeded bound");
            }
            builder.Append(buffer, 0, read);
        }
    }

    private static async Task<bool> TryTerminateAndConfirmAsync(Process process)
    {
        try
        {
            if (process.HasExited)
            {
                return false;
            }
            process.Kill(entireProcessTree: true);
        }
        catch (InvalidOperationException)
        {
            return false;
        }
        catch (Win32Exception)
        {
            return false;
        }

        try
        {
            await process.WaitForExitAsync(CancellationToken.None).ConfigureAwait(false);
            return process.HasExited;
        }
        catch (InvalidOperationException)
        {
            return false;
        }
    }
}
