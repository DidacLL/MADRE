using System.ComponentModel;
using System.Diagnostics;
using System.Text;
using Microsoft.Extensions.AI;

namespace Madre.Kernel;

public sealed record BindingExecutionResult(
    PhysicalAttemptOutcome Outcome,
    string? Result = null,
    PhysicalFailure? Failure = null)
{
    public static BindingExecutionResult Success(string result) =>
        new(PhysicalAttemptOutcome.Succeeded, result);

    public static BindingExecutionResult Fail(PhysicalFailureKind kind, string? detail = null) =>
        new(PhysicalAttemptOutcome.DefiniteFailure, null, new PhysicalFailure(kind, detail));

    public static BindingExecutionResult Cancelled(string? detail = null) =>
        new(PhysicalAttemptOutcome.ConfirmedCancelled, null, new PhysicalFailure(PhysicalFailureKind.Cancelled, detail));

    public static BindingExecutionResult Unknown(string? detail = null) =>
        new(PhysicalAttemptOutcome.UnknownCompletion, null, new PhysicalFailure(PhysicalFailureKind.CompletionUnknown, detail));
}

public interface IInferenceBinding
{
    string BindingId { get; }
    string BindingVersion { get; }
    Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken);
    Task<BindingExecutionResult> ExecuteAsync(InferenceExecutionRequest request, CancellationToken cancellationToken);
}

internal sealed class BindingExecutor
{
    public async Task<BindingExecutionResult> ExecuteAsync(
        IInferenceBinding binding,
        InferenceExecutionRequest request,
        CancellationToken cancellationToken)
    {
        BindingExecutionResult result;
        try
        {
            result = await binding.ExecuteAsync(request, cancellationToken).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            return BindingExecutionResult.Unknown("binding execution cancelled without confirmed physical completion");
        }
        catch (Exception ex)
        {
            return BindingExecutionResult.Unknown($"binding threw {ex.GetType().Name}");
        }

        if (result.Outcome != PhysicalAttemptOutcome.Succeeded)
        {
            return result;
        }
        if (result.Result is null)
        {
            return BindingExecutionResult.Fail(
                PhysicalFailureKind.InvalidBindingResult,
                "binding reported success without a result");
        }
        if (Encoding.UTF8.GetByteCount(result.Result) > KernelProtocol.MaxPayloadBytes)
        {
            return BindingExecutionResult.Fail(
                PhysicalFailureKind.PayloadLimitExceeded,
                $"result exceeded {KernelProtocol.MaxPayloadBytes} UTF-8 bytes");
        }
        return result;
    }
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

    public async Task<BindingExecutionResult> ExecuteAsync(
        InferenceExecutionRequest request,
        CancellationToken cancellationToken)
    {
        ChatResponse response = await _chatClient
            .GetResponseAsync(request.PreparedInput, cancellationToken: cancellationToken)
            .ConfigureAwait(false);
        return BindingExecutionResult.Success(response.Text);
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

        using Process process = NewProcess(redirectStreams: false);
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

    public async Task<BindingExecutionResult> ExecuteAsync(
        InferenceExecutionRequest request,
        CancellationToken cancellationToken)
    {
        using Process process = NewProcess(redirectStreams: true);
        foreach (string argument in _arguments)
        {
            process.StartInfo.ArgumentList.Add(argument);
        }

        try
        {
            if (!process.Start())
            {
                return BindingExecutionResult.Fail(PhysicalFailureKind.LaunchFailed, "Process.Start returned false");
            }
        }
        catch (Win32Exception ex)
        {
            return BindingExecutionResult.Fail(PhysicalFailureKind.LaunchFailed, ex.NativeErrorCode.ToString());
        }
        catch (InvalidOperationException ex)
        {
            return BindingExecutionResult.Fail(PhysicalFailureKind.LaunchFailed, ex.GetType().Name);
        }

        try
        {
            Task<string> stdout = ReadBoundedAsync(process.StandardOutput, cancellationToken);
            Task<string> stderr = ReadBoundedAsync(process.StandardError, cancellationToken);

            await process.StandardInput.WriteAsync(request.PreparedInput.AsMemory(), cancellationToken).ConfigureAwait(false);
            await process.StandardInput.FlushAsync(cancellationToken).ConfigureAwait(false);
            process.StandardInput.Close();

            await process.WaitForExitAsync(cancellationToken).ConfigureAwait(false);
            string output = await stdout.ConfigureAwait(false);
            string error = await stderr.ConfigureAwait(false);

            if (process.ExitCode != 0)
            {
                string detail = string.IsNullOrWhiteSpace(error)
                    ? $"exitCode={process.ExitCode}"
                    : $"exitCode={process.ExitCode}; stderr={error}";
                return BindingExecutionResult.Fail(PhysicalFailureKind.ProcessExited, detail);
            }
            return BindingExecutionResult.Success(output);
        }
        catch (OperationCanceledException)
        {
            bool confirmed = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return confirmed
                ? BindingExecutionResult.Cancelled("process tree terminated")
                : BindingExecutionResult.Unknown("process cancellation completion could not be confirmed");
        }
        catch (InvalidDataException ex)
        {
            _ = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Fail(PhysicalFailureKind.PayloadLimitExceeded, ex.Message);
        }
        catch (IOException ex)
        {
            _ = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Fail(PhysicalFailureKind.IoFailure, ex.GetType().Name);
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

    private static async Task<string> ReadBoundedAsync(StreamReader reader, CancellationToken cancellationToken)
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
            if (bytes > KernelProtocol.MaxPayloadBytes)
            {
                throw new InvalidDataException($"process output exceeded {KernelProtocol.MaxPayloadBytes} UTF-8 bytes");
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
