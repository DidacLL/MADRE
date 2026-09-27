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
    private static readonly Encoding Utf8 = new UTF8Encoding(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);
    private static readonly TimeSpan TerminationConfirmationTimeout = TimeSpan.FromSeconds(2);
    private readonly string _executable;
    private readonly IReadOnlyList<string> _arguments;
    private readonly IReadOnlyList<string>? _probeArguments;

    private enum ProcessTerminationState
    {
        AlreadyExited,
        Terminated,
        Unknown
    }

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

        try
        {
            await process.WaitForExitAsync(cancellationToken).ConfigureAwait(false);
            return process.ExitCode == 0
                ? CapabilityAvailability.Available
                : CapabilityAvailability.Unavailable;
        }
        catch (OperationCanceledException)
        {
            _ = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
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
            Task input = WriteInputAsync(process.StandardInput, request.PreparedInput, cancellationToken);

            (string output, string error) = await SuperviseAsync(
                process,
                input,
                stdout,
                stderr,
                cancellationToken).ConfigureAwait(false);

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
            ProcessTerminationState termination = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return termination switch
            {
                ProcessTerminationState.Terminated =>
                    BindingExecutionResult.Cancelled("process tree terminated"),
                ProcessTerminationState.AlreadyExited =>
                    BindingExecutionResult.Fail(
                        PhysicalFailureKind.Cancelled,
                        "process completed before cancellation could be confirmed"),
                _ => BindingExecutionResult.Unknown("process cancellation completion could not be confirmed")
            };
        }
        catch (InvalidDataException ex)
        {
            ProcessTerminationState termination = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return termination == ProcessTerminationState.Unknown
                ? BindingExecutionResult.Unknown(
                    $"process output limit exceeded and completion could not be confirmed: {ex.Message}")
                : BindingExecutionResult.Fail(PhysicalFailureKind.PayloadLimitExceeded, ex.Message);
        }
        catch (IOException ex)
        {
            ProcessTerminationState termination = await TryTerminateAndConfirmAsync(process).ConfigureAwait(false);
            return termination == ProcessTerminationState.Unknown
                ? BindingExecutionResult.Unknown(
                    $"process I/O failed and completion could not be confirmed: {ex.GetType().Name}")
                : BindingExecutionResult.Fail(PhysicalFailureKind.IoFailure, ex.GetType().Name);
        }
    }

    private Process NewProcess(bool redirectStreams)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = _executable,
            RedirectStandardInput = redirectStreams,
            RedirectStandardOutput = redirectStreams,
            RedirectStandardError = redirectStreams,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        if (redirectStreams)
        {
            startInfo.StandardInputEncoding = Utf8;
            startInfo.StandardOutputEncoding = Utf8;
            startInfo.StandardErrorEncoding = Utf8;
        }
        return new Process { StartInfo = startInfo };
    }

    private static async Task WriteInputAsync(
        StreamWriter writer,
        string input,
        CancellationToken cancellationToken)
    {
        await writer.WriteAsync(input.AsMemory(), cancellationToken).ConfigureAwait(false);
        await writer.FlushAsync(cancellationToken).ConfigureAwait(false);
        writer.Close();
    }

    private static async Task<(string Output, string Error)> SuperviseAsync(
        Process process,
        Task input,
        Task<string> stdout,
        Task<string> stderr,
        CancellationToken cancellationToken)
    {
        Task? inputTask = input;
        Task? exitTask = process.WaitForExitAsync(cancellationToken);
        Task<string>? stdoutTask = stdout;
        Task<string>? stderrTask = stderr;
        string output = string.Empty;
        string error = string.Empty;

        while (inputTask is not null || exitTask is not null || stdoutTask is not null || stderrTask is not null)
        {
            var pending = new List<Task>(4);
            if (inputTask is not null)
            {
                pending.Add(inputTask);
            }
            if (exitTask is not null)
            {
                pending.Add(exitTask);
            }
            if (stdoutTask is not null)
            {
                pending.Add(stdoutTask);
            }
            if (stderrTask is not null)
            {
                pending.Add(stderrTask);
            }

            Task completed = await Task.WhenAny(pending).ConfigureAwait(false);
            if (ReferenceEquals(completed, inputTask))
            {
                await inputTask!.ConfigureAwait(false);
                inputTask = null;
                continue;
            }
            if (ReferenceEquals(completed, exitTask))
            {
                await exitTask!.ConfigureAwait(false);
                exitTask = null;
                continue;
            }
            if (ReferenceEquals(completed, stdoutTask))
            {
                output = await stdoutTask!.ConfigureAwait(false);
                stdoutTask = null;
                continue;
            }

            error = await stderrTask!.ConfigureAwait(false);
            stderrTask = null;
        }

        return (output, error);
    }

    private static async Task<string> ReadBoundedAsync(StreamReader reader, CancellationToken cancellationToken)
    {
        var builder = new StringBuilder();
        var buffer = new char[4096];
        int bytes = 0;
        while (true)
        {
            int read;
            try
            {
                read = await reader.ReadAsync(buffer.AsMemory(), cancellationToken).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception ex)
            {
                throw new IOException("process output read failed", ex);
            }

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

    private static async Task<ProcessTerminationState> TryTerminateAndConfirmAsync(Process process)
    {
        if (HasExited(process))
        {
            return ProcessTerminationState.AlreadyExited;
        }

        try
        {
            process.Kill(entireProcessTree: true);
        }
        catch (InvalidOperationException)
        {
            return HasExited(process)
                ? ProcessTerminationState.AlreadyExited
                : ProcessTerminationState.Unknown;
        }
        catch (Win32Exception)
        {
            return HasExited(process)
                ? ProcessTerminationState.AlreadyExited
                : ProcessTerminationState.Unknown;
        }

        try
        {
            await process.WaitForExitAsync(CancellationToken.None)
                .WaitAsync(TerminationConfirmationTimeout)
                .ConfigureAwait(false);
            return HasExited(process)
                ? ProcessTerminationState.Terminated
                : ProcessTerminationState.Unknown;
        }
        catch (TimeoutException)
        {
            return HasExited(process)
                ? ProcessTerminationState.Terminated
                : ProcessTerminationState.Unknown;
        }
        catch (InvalidOperationException)
        {
            return HasExited(process)
                ? ProcessTerminationState.Terminated
                : ProcessTerminationState.Unknown;
        }
    }

    private static bool HasExited(Process process)
    {
        try
        {
            return process.HasExited;
        }
        catch (InvalidOperationException)
        {
            return false;
        }
        catch (Win32Exception)
        {
            return false;
        }
    }
}
