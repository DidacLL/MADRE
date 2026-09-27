using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Net.Sockets;
using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;

namespace Madre.Kernel.Host;

public enum KernelIpcOperation
{
    Health,
    ProtocolInfo,
    Submit,
    Inspect,
    Result,
    Cancel,
    Release,
    Capabilities,
    RefreshCapabilities
}

public enum KernelIpcErrorCode
{
    InvalidRequest,
    NotFound,
    WorkNotTerminal,
    InternalFailure
}

public sealed record KernelProtocolInfo(int Version, int MaxPayloadBytes, int MaxFrameBytes);

internal sealed record KernelIpcRequest(
    int Version,
    string RequestId,
    KernelIpcOperation Operation,
    JsonElement Payload);

internal sealed record KernelIpcError(KernelIpcErrorCode Code, string? Detail);

internal sealed record KernelIpcResponse(
    int Version,
    string RequestId,
    bool Ok,
    object? Payload,
    KernelIpcError? Error);

internal sealed record WorkIdPayload(string WorkId);
internal sealed record CancelResponse(WorkState State);
internal sealed record ReleaseResponse(bool Released);
internal sealed record HealthResponse(string Status);

public static class KernelIpcDefaults
{
    public const int ListenBacklog = 64;
    public static TimeSpan ClientIdleTimeout { get; } = TimeSpan.FromSeconds(30);
}

public static class KernelIpcServer
{
    private static readonly JsonSerializerOptions Json = CreateJson();

    public static async Task RunAsync(
        string databasePath,
        string ipcPath,
        int maxConcurrent,
        IReadOnlyList<InferenceCapability> capabilities,
        IReadOnlyList<IInferenceBinding> bindings,
        CancellationToken cancellationToken = default)
    {
        PrepareEndpoint(ipcPath);
        await using var engine = new KernelEngine(
            new WorkStore(databasePath),
            capabilities,
            bindings,
            maxConcurrent);
        await engine.InitializeAsync(cancellationToken).ConfigureAwait(false);
        engine.Start();

        using var listener = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
        listener.Bind(new UnixDomainSocketEndPoint(ipcPath));
        listener.Listen(KernelIpcDefaults.ListenBacklog);
        RestrictSocketFile(ipcPath);

        var clients = new ConcurrentDictionary<long, Task>();
        long clientId = 0;
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                Socket client = await listener.AcceptAsync(cancellationToken).ConfigureAwait(false);
                long id = Interlocked.Increment(ref clientId);
                Task task = HandleClientAsync(client, engine, cancellationToken);
                clients[id] = task;
                _ = task.ContinueWith(
                    completed =>
                    {
                        _ = completed.Exception;
                        clients.TryRemove(id, out Task? removed);
                    },
                    CancellationToken.None,
                    TaskContinuationOptions.ExecuteSynchronously,
                    TaskScheduler.Default);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        finally
        {
            listener.Close();
            if (clients.Count > 0)
            {
                try
                {
                    await Task.WhenAll(clients.Values).ConfigureAwait(false);
                }
                catch (OperationCanceledException)
                {
                }
            }
            TryDeleteEndpoint(ipcPath);
        }
    }

    private static async Task HandleClientAsync(
        Socket socket,
        KernelEngine engine,
        CancellationToken serverCancellation)
    {
        using (socket)
        using (var idle = CancellationTokenSource.CreateLinkedTokenSource(serverCancellation))
        {
            idle.CancelAfter(KernelIpcDefaults.ClientIdleTimeout);
            try
            {
                using var stream = new NetworkStream(socket, ownsSocket: false);
                byte[] frame = await ReadFrameAsync(stream, idle.Token).ConfigureAwait(false);
                KernelIpcRequest? request = JsonSerializer.Deserialize<KernelIpcRequest>(frame, Json);
                if (request is null)
                {
                    return;
                }
                KernelIpcResponse response = await DispatchAsync(request, engine, idle.Token).ConfigureAwait(false);
                await WriteFrameAsync(stream, response, idle.Token).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
            }
            catch (IOException)
            {
            }
            catch (SocketException)
            {
            }
            catch (InvalidDataException)
            {
            }
            catch (JsonException)
            {
            }
        }
    }

    private static async Task<KernelIpcResponse> DispatchAsync(
        KernelIpcRequest request,
        KernelEngine engine,
        CancellationToken cancellationToken)
    {
        if (request.Version != KernelProtocol.Version)
        {
            return Error(request, KernelIpcErrorCode.InvalidRequest, $"unsupported protocol version {request.Version}");
        }

        try
        {
            object? payload = request.Operation switch
            {
                KernelIpcOperation.Health => new HealthResponse("ok"),
                KernelIpcOperation.ProtocolInfo => new KernelProtocolInfo(
                    KernelProtocol.Version,
                    KernelProtocol.MaxPayloadBytes,
                    KernelProtocol.MaxFrameBytes),
                KernelIpcOperation.Submit => new WorkSubmissionResponse(
                    await engine.SubmitAsync(ReadPayload<PhysicalInferenceRequest>(request), cancellationToken).ConfigureAwait(false)),
                KernelIpcOperation.Inspect => await RequireInspectionAsync(engine, ReadWorkId(request), cancellationToken).ConfigureAwait(false),
                KernelIpcOperation.Result => await RequireResultAsync(engine, ReadWorkId(request), cancellationToken).ConfigureAwait(false),
                KernelIpcOperation.Cancel => new CancelResponse(
                    await RequireCancelAsync(engine, ReadWorkId(request), cancellationToken).ConfigureAwait(false)),
                KernelIpcOperation.Release => await ReleaseAsync(engine, ReadWorkId(request), cancellationToken).ConfigureAwait(false),
                KernelIpcOperation.Capabilities => await engine.CapabilitiesAsync(cancellationToken).ConfigureAwait(false),
                KernelIpcOperation.RefreshCapabilities => await engine.RefreshCapabilityStatesAsync(cancellationToken).ConfigureAwait(false),
                _ => throw new ArgumentOutOfRangeException(nameof(request.Operation))
            };
            return new KernelIpcResponse(KernelProtocol.Version, request.RequestId, true, payload, null);
        }
        catch (KeyNotFoundException ex)
        {
            return Error(request, KernelIpcErrorCode.NotFound, ex.Message);
        }
        catch (WorkNotTerminalException ex)
        {
            return Error(request, KernelIpcErrorCode.WorkNotTerminal, ex.Message);
        }
        catch (ArgumentException ex)
        {
            return Error(request, KernelIpcErrorCode.InvalidRequest, ex.Message);
        }
        catch (JsonException ex)
        {
            return Error(request, KernelIpcErrorCode.InvalidRequest, ex.Message);
        }
        catch (Exception ex)
        {
            return Error(request, KernelIpcErrorCode.InternalFailure, ex.GetType().Name);
        }
    }

    private static T ReadPayload<T>(KernelIpcRequest request) where T : class =>
        request.Payload.Deserialize<T>(Json)
        ?? throw new ArgumentException($"{request.Operation} payload is required");

    private static string ReadWorkId(KernelIpcRequest request)
    {
        WorkIdPayload payload = ReadPayload<WorkIdPayload>(request);
        if (string.IsNullOrWhiteSpace(payload.WorkId))
        {
            throw new ArgumentException("workId is required");
        }
        return payload.WorkId;
    }

    private static async Task<WorkInspection> RequireInspectionAsync(
        KernelEngine engine,
        string workId,
        CancellationToken cancellationToken) =>
        await engine.InspectAsync(workId, cancellationToken).ConfigureAwait(false)
        ?? throw new KeyNotFoundException($"Work {workId} was not found");

    private static async Task<WorkResultSnapshot> RequireResultAsync(
        KernelEngine engine,
        string workId,
        CancellationToken cancellationToken) =>
        await engine.ResultAsync(workId, cancellationToken).ConfigureAwait(false)
        ?? throw new KeyNotFoundException($"Work {workId} was not found");

    private static async Task<WorkState> RequireCancelAsync(
        KernelEngine engine,
        string workId,
        CancellationToken cancellationToken) =>
        await engine.CancelAsync(workId, cancellationToken).ConfigureAwait(false)
        ?? throw new KeyNotFoundException($"Work {workId} was not found");

    private static async Task<ReleaseResponse> ReleaseAsync(
        KernelEngine engine,
        string workId,
        CancellationToken cancellationToken)
    {
        bool? released = await engine.ReleaseAsync(workId, cancellationToken).ConfigureAwait(false);
        return released switch
        {
            null => throw new KeyNotFoundException($"Work {workId} was not found"),
            false => throw new WorkNotTerminalException($"Work {workId} is not terminal"),
            true => new ReleaseResponse(true)
        };
    }

    private static KernelIpcResponse Error(
        KernelIpcRequest request,
        KernelIpcErrorCode code,
        string? detail) =>
        new(KernelProtocol.Version, request.RequestId, false, null, new KernelIpcError(code, detail));

    private static async Task<byte[]> ReadFrameAsync(Stream stream, CancellationToken cancellationToken)
    {
        byte[] header = new byte[4];
        await stream.ReadExactlyAsync(header, cancellationToken).ConfigureAwait(false);
        int length = BinaryPrimitives.ReadInt32BigEndian(header);
        if (length <= 0 || length > KernelProtocol.MaxFrameBytes)
        {
            throw new InvalidDataException($"IPC frame length {length} is outside the protocol bound");
        }
        byte[] payload = new byte[length];
        await stream.ReadExactlyAsync(payload, cancellationToken).ConfigureAwait(false);
        return payload;
    }

    private static async Task WriteFrameAsync(
        Stream stream,
        KernelIpcResponse response,
        CancellationToken cancellationToken)
    {
        byte[] payload = JsonSerializer.SerializeToUtf8Bytes(response, Json);
        if (payload.Length > KernelProtocol.MaxFrameBytes)
        {
            throw new InvalidDataException("IPC response exceeded the protocol frame bound");
        }
        byte[] header = new byte[4];
        BinaryPrimitives.WriteInt32BigEndian(header, payload.Length);
        await stream.WriteAsync(header, cancellationToken).ConfigureAwait(false);
        await stream.WriteAsync(payload, cancellationToken).ConfigureAwait(false);
        await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
    }

    private static void PrepareEndpoint(string ipcPath)
    {
        string fullPath = Path.GetFullPath(ipcPath);
        string directory = Path.GetDirectoryName(fullPath)
            ?? throw new InvalidOperationException("IPC path has no parent directory");
        Directory.CreateDirectory(directory);
        if (!OperatingSystem.IsWindows())
        {
            Directory.SetUnixFileMode(
                directory,
                UnixFileMode.UserRead | UnixFileMode.UserWrite | UnixFileMode.UserExecute);
        }
        TryDeleteEndpoint(fullPath);
    }

    private static void RestrictSocketFile(string ipcPath)
    {
        if (!OperatingSystem.IsWindows() && File.Exists(ipcPath))
        {
            File.SetUnixFileMode(ipcPath, UnixFileMode.UserRead | UnixFileMode.UserWrite);
        }
    }

    private static void TryDeleteEndpoint(string ipcPath)
    {
        try
        {
            if (File.Exists(ipcPath))
            {
                File.Delete(ipcPath);
            }
        }
        catch (IOException)
        {
        }
    }

    private static JsonSerializerOptions CreateJson()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = true
        };
        options.Converters.Add(new JsonStringEnumConverter());
        return options;
    }

    private sealed class WorkNotTerminalException(string message) : Exception(message);
}
