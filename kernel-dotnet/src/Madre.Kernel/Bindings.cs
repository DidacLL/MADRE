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
    Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken);
}

public sealed class MeaiInferenceBinding : IInferenceBinding
{
    private readonly IChatClient _chatClient;

    public MeaiInferenceBinding(string bindingId, string bindingVersion, IChatClient chatClient)
    {
        BindingId = bindingId;
        BindingVersion = bindingVersion;
        _chatClient = chatClient;
    }

    public string BindingId { get; }
    public string BindingVersion { get; }

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
    private readonly string _executable;
    private readonly IReadOnlyList<string> _arguments;

    public ProcessInferenceBinding(
        string bindingId,
        string bindingVersion,
        string executable,
        IReadOnlyList<string>? arguments = null)
    {
        BindingId = bindingId;
        BindingVersion = bindingVersion;
        _executable = executable;
        _arguments = arguments ?? Array.Empty<string>();
    }

    public string BindingId { get; }
    public string BindingVersion { get; }

    public async Task<BindingExecutionResult> ExecuteAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = _executable,
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            }
        };
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
            await KillAndWaitAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Cancelled("PROCESS_CANCELLED_CONFIRMED");
        }
        catch (InvalidDataException)
        {
            await KillAndWaitAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Failure("OUTPUT_LIMIT_EXCEEDED");
        }
        catch (IOException)
        {
            await KillAndWaitAsync(process).ConfigureAwait(false);
            return BindingExecutionResult.Failure("PROCESS_IO_FAILURE");
        }
    }

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

    private static async Task KillAndWaitAsync(Process process)
    {
        try
        {
            if (!process.HasExited)
            {
                process.Kill(entireProcessTree: true);
            }
        }
        catch (InvalidOperationException)
        {
        }
        catch (Win32Exception)
        {
        }

        try
        {
            await process.WaitForExitAsync(CancellationToken.None).ConfigureAwait(false);
        }
        catch (InvalidOperationException)
        {
        }
    }
}
