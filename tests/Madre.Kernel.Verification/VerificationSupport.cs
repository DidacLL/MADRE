using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net.Sockets;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using Madre.Kernel;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.AI;

internal static partial class Program
{
    private static readonly WorkState[] TerminalStates =
    [
        WorkState.Succeeded,
        WorkState.Failed,
        WorkState.Cancelled,
        WorkState.UnknownCompletion
    ];

    private sealed class ManualKernelClock(DateTimeOffset initial) : IKernelClock
    {
        private readonly object _gate = new();
        private readonly List<DelayWaiter> _waiters = [];
        private DateTimeOffset _now = initial;

        public DateTimeOffset UtcNow
        {
            get
            {
                lock (_gate)
                {
                    return _now;
                }
            }
        }

        public Task DelayAsync(TimeSpan delay, CancellationToken cancellationToken)
        {
            if (delay <= TimeSpan.Zero)
            {
                return Task.CompletedTask;
            }

            lock (_gate)
            {
                var completion = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                var waiter = new DelayWaiter(_now + delay, completion);
                _waiters.Add(waiter);
                if (cancellationToken.CanBeCanceled)
                {
                    waiter.Registration = cancellationToken.Register(
                        static state => ((TaskCompletionSource<bool>)state!).TrySetCanceled(),
                        completion);
                }
                return completion.Task;
            }
        }

        public void Advance(TimeSpan amount)
        {
            List<DelayWaiter> due;
            lock (_gate)
            {
                _now += amount;
                due = _waiters.Where(waiter => waiter.Due <= _now).ToList();
                foreach (DelayWaiter waiter in due)
                {
                    _waiters.Remove(waiter);
                }
            }
            foreach (DelayWaiter waiter in due)
            {
                waiter.Registration.Dispose();
                waiter.Completion.TrySetResult(true);
            }
        }

        private sealed class DelayWaiter(DateTimeOffset due, TaskCompletionSource<bool> completion)
        {
            public DateTimeOffset Due { get; } = due;
            public TaskCompletionSource<bool> Completion { get; } = completion;
            public CancellationTokenRegistration Registration { get; set; }
        }
    }

    private sealed class ControlledBinding(
        string bindingId,
        string bindingVersion,
        CapabilityAvailability availability = CapabilityAvailability.Available) : IInferenceBinding
    {
        private int _active;
        private int _maxActive;
        private int _probeCount;
        private int _executionCount;
        private volatile CapabilityAvailability _availability = availability;

        public string BindingId => bindingId;
        public string BindingVersion => bindingVersion;
        public CapabilityAvailability Availability
        {
            get => _availability;
            set => _availability = value;
        }
        public int Active => Volatile.Read(ref _active);
        public int MaxActive => Volatile.Read(ref _maxActive);
        public int ProbeCount => Volatile.Read(ref _probeCount);
        public int ExecutionCount => Volatile.Read(ref _executionCount);
        public ConcurrentQueue<string> Invocations { get; } = new();
        public Func<CancellationToken, Task<CapabilityAvailability>>? ProbeHandler { get; set; }
        public Func<InferenceExecutionRequest, CancellationToken, Task<BindingExecutionResult>>? ExecuteHandler { get; set; }

        public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken)
        {
            Interlocked.Increment(ref _probeCount);
            return ProbeHandler?.Invoke(cancellationToken) ?? Task.FromResult(_availability);
        }

        public async Task<BindingExecutionResult> ExecuteAsync(
            InferenceExecutionRequest request,
            CancellationToken cancellationToken)
        {
            Interlocked.Increment(ref _executionCount);
            Invocations.Enqueue(request.PreparedInput);
            int active = Interlocked.Increment(ref _active);
            while (true)
            {
                int current = Volatile.Read(ref _maxActive);
                if (active <= current || Interlocked.CompareExchange(ref _maxActive, active, current) == current)
                {
                    break;
                }
            }

            try
            {
                if (ExecuteHandler is not null)
                {
                    return await ExecuteHandler(request, cancellationToken).ConfigureAwait(false);
                }
                return BindingExecutionResult.Success(request.PreparedInput);
            }
            finally
            {
                Interlocked.Decrement(ref _active);
            }
        }
    }

    private sealed class LocalChatClient(
        Func<IEnumerable<ChatMessage>, ChatOptions?, CancellationToken, Task<ChatResponse>> callback) : IChatClient
    {
        public Task<ChatResponse> GetResponseAsync(
            IEnumerable<ChatMessage> messages,
            ChatOptions? options = null,
            CancellationToken cancellationToken = default) =>
            callback(messages, options, cancellationToken);

        public async IAsyncEnumerable<ChatResponseUpdate> GetStreamingResponseAsync(
            IEnumerable<ChatMessage> messages,
            ChatOptions? options = null,
            [EnumeratorCancellation] CancellationToken cancellationToken = default)
        {
            ChatResponse response = await callback(messages, options, cancellationToken).ConfigureAwait(false);
            yield return new ChatResponseUpdate(ChatRole.Assistant, response.Text);
        }

        public object? GetService(Type serviceType, object? serviceKey = null) =>
            serviceKey is null && serviceType.IsInstanceOfType(this) ? this : null;

        public void Dispose()
        {
        }
    }

    private static InferenceCapability Capability(
        string id,
        string bindingId,
        string bindingVersion,
        InferenceEffort effort,
        ExecutionBoundary boundary,
        int preference) =>
        new(
            id,
            bindingId,
            bindingVersion,
            new ConfiguredFact<ExecutionBoundary>(boundary, FactProvenance.Owner),
            new ConfiguredFact<InferenceEffort>(effort, FactProvenance.Owner),
            preference);

    private static CapabilitySnapshot Snapshot(
        InferenceCapability capability,
        CapabilityAvailability availability,
        double? latency = null,
        DateTimeOffset? observedAt = null) =>
        new(
            capability,
            new CapabilityState(capability.CapabilityId, availability, observedAt),
            latency);

    private static bool IsTerminal(WorkState state) => TerminalStates.Contains(state);

    private static async Task<WorkInspection> WaitTerminalAsync(
        KernelEngine engine,
        string workId,
        int timeoutMs = 10_000)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        WorkInspection? latest = null;
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            latest = await engine.InspectAsync(workId).ConfigureAwait(false);
            if (latest is not null && IsTerminal(latest.State))
            {
                return latest;
            }
            await Task.Delay(10).ConfigureAwait(false);
        }
        throw new InvalidOperationException($"Work {workId} did not become terminal; latest={latest?.State}");
    }

    private static async Task WaitAllTerminalAsync(
        KernelEngine engine,
        IEnumerable<string> workIds,
        int timeoutMs = 30_000)
    {
        string[] ids = workIds.ToArray();
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            bool all = true;
            foreach (string id in ids)
            {
                WorkInspection? inspection = await engine.InspectAsync(id).ConfigureAwait(false);
                if (inspection is null || !IsTerminal(inspection.State))
                {
                    all = false;
                    break;
                }
            }
            if (all)
            {
                return;
            }
            await Task.Delay(15).ConfigureAwait(false);
        }
        throw new InvalidOperationException($"{ids.Length} Work items did not all become terminal");
    }

    private static void AssertGlobalPhysicalInvariants(
        IReadOnlyList<WorkInspection> works,
        int observedActive,
        int maxConcurrent)
    {
        Check(observedActive <= maxConcurrent,
            $"observed physical concurrency {observedActive} exceeded maxConcurrent={maxConcurrent}");
        foreach (WorkInspection work in works)
        {
            Check(work.Attempts.Select(attempt => attempt.AttemptNumber).Distinct().Count() == work.Attempts.Count,
                $"Work {work.WorkId} has duplicate attempt identity");
            if (work.State == WorkState.Queued)
            {
                Check(work.Attempts.Count == 0,
                    $"queued Work {work.WorkId} unexpectedly has an attempt");
            }
            if (IsTerminal(work.State))
            {
                Check(work.Attempts.All(attempt => attempt.Outcome != PhysicalAttemptOutcome.Running),
                    $"terminal Work {work.WorkId} retains a Running attempt");
            }
            if (work.State == WorkState.UnknownCompletion)
            {
                Check(work.Attempts.LastOrDefault()?.Outcome == PhysicalAttemptOutcome.UnknownCompletion,
                    $"UnknownCompletion Work {work.WorkId} lacks uncertain attempt truth");
            }
        }
    }

    private static async Task<IReadOnlyList<WorkInspection>> InspectAllAsync(
        KernelEngine engine,
        IEnumerable<string> ids)
    {
        var result = new List<WorkInspection>();
        foreach (string id in ids)
        {
            WorkInspection? inspection = await engine.InspectAsync(id).ConfigureAwait(false);
            Check(inspection is not null, $"missing durable Work {id}");
            result.Add(inspection!);
        }
        return result;
    }

    private static IEnumerable<IReadOnlyList<T>> Permutations<T>(IReadOnlyList<T> values)
    {
        T[] buffer = values.ToArray();
        return Permute(buffer, 0);

        static IEnumerable<IReadOnlyList<T>> Permute(T[] buffer, int index)
        {
            if (index == buffer.Length)
            {
                yield return buffer.ToArray();
                yield break;
            }
            for (int i = index; i < buffer.Length; i++)
            {
                (buffer[index], buffer[i]) = (buffer[i], buffer[index]);
                foreach (IReadOnlyList<T> value in Permute(buffer, index + 1))
                {
                    yield return value;
                }
                (buffer[index], buffer[i]) = (buffer[i], buffer[index]);
            }
        }
    }

    private static async Task<byte[]?> SendRawFrameAsync(
        string socketPath,
        int declaredLength,
        ReadOnlyMemory<byte> body,
        bool readResponse = true,
        bool sendHeader = true,
        int bodyChunkBytes = int.MaxValue,
        CancellationToken cancellationToken = default)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(3));
        try
        {
            using var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
            await socket.ConnectAsync(new UnixDomainSocketEndPoint(socketPath), timeout.Token).ConfigureAwait(false);
            using var stream = new NetworkStream(socket, ownsSocket: false);
            if (sendHeader)
            {
                byte[] header = new byte[4];
                BinaryPrimitives.WriteInt32BigEndian(header, declaredLength);
                await stream.WriteAsync(header, timeout.Token).ConfigureAwait(false);
            }
            for (int offset = 0; offset < body.Length; offset += bodyChunkBytes)
            {
                int count = Math.Min(bodyChunkBytes, body.Length - offset);
                await stream.WriteAsync(body.Slice(offset, count), timeout.Token).ConfigureAwait(false);
            }
            await stream.FlushAsync(timeout.Token).ConfigureAwait(false);
            if (!readResponse)
            {
                return null;
            }

            byte[] responseHeader = new byte[4];
            await stream.ReadExactlyAsync(responseHeader, timeout.Token).ConfigureAwait(false);
            int length = BinaryPrimitives.ReadInt32BigEndian(responseHeader);
            Check(length > 0 && length <= KernelProtocol.MaxFrameBytes, "raw response length outside protocol bound");
            byte[] response = new byte[length];
            await stream.ReadExactlyAsync(response, timeout.Token).ConfigureAwait(false);
            return response;
        }
        catch (Exception ex) when (ex is IOException or SocketException or OperationCanceledException)
        {
            return null;
        }
    }

    private static byte[] RawRequestJson(string requestId, string operation, string payloadJson = "null") =>
        Encoding.UTF8.GetBytes($"{{\"version\":{KernelProtocol.Version},\"requestId\":{JsonSerializer.Serialize(requestId)},\"operation\":{JsonSerializer.Serialize(operation)},\"payload\":{payloadJson}}}");

    private static async Task AssertCleanIpcWorkAsync(IpcClient client, string input = "verification-clean")
    {
        Check((await client.CallAsync<Health>("Health", null).ConfigureAwait(false)).Status == "ok",
            "Health did not recover after hostile IPC batch");
        string id = await SubmitAsync(client, Req(input, InferenceEffort.Low, WorkUrgency.Normal, ExecutionBoundary.LocalOnly)).ConfigureAwait(false);
        WorkInspection done = await WaitIpcTerminalAsync(client, id).ConfigureAwait(false);
        Check(done.State == WorkState.Succeeded, $"clean IPC Work failed after hostile batch: {done.State}/{done.Failure}");
    }

    private static async Task<WorkInspection> WaitIpcTerminalAsync(IpcClient client, string id, int timeoutMs = 10_000)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        WorkInspection? latest = null;
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            latest = await InspectAsync(client, id).ConfigureAwait(false);
            if (IsTerminal(latest.State))
            {
                return latest;
            }
            await Task.Delay(15).ConfigureAwait(false);
        }
        throw new InvalidOperationException($"IPC Work {id} did not become terminal; latest={latest?.State}");
    }

    private static async Task<int> CountRowsAsync(string database, string table, string? where = null)
    {
        await using var connection = new SqliteConnection($"Data Source={database}");
        await connection.OpenAsync().ConfigureAwait(false);
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = $"SELECT COUNT(*) FROM {table}" + (where is null ? string.Empty : $" WHERE {where}");
        return Convert.ToInt32(await command.ExecuteScalarAsync().ConfigureAwait(false));
    }
}
