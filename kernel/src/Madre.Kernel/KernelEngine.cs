using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text;

namespace Madre.Kernel;

public sealed class KernelEngine : IAsyncDisposable
{
    private readonly WorkStore _store;
    private readonly DreSelector _dre = new();
    private readonly BindingExecutor _bindingExecutor = new();
    private readonly IReadOnlyList<InferenceCapability> _capabilities;
    private readonly Dictionary<string, IInferenceBinding> _bindings;
    private readonly SemaphoreSlim _slots;
    private readonly SemaphoreSlim _dispatchGate = new(1, 1);
    private readonly SemaphoreSlim _wake = new(0, 1);
    private readonly CancellationTokenSource _shutdown = new();
    private readonly ConcurrentDictionary<string, CancellationTokenSource> _runningCancellations = new();
    private readonly ConcurrentDictionary<string, Task> _runningTasks = new();
    private readonly ConcurrentDictionary<string, Lazy<Task>> _probeTasks = new();
    private readonly ConcurrentDictionary<string, DateTimeOffset> _nextProbeAt = new();
    private readonly IKernelClock _clock;
    private readonly KernelTimingOptions _timing;
    private Task? _scheduler;

    public KernelEngine(
        WorkStore store,
        IReadOnlyList<InferenceCapability> capabilities,
        IReadOnlyList<IInferenceBinding> bindings,
        int maxConcurrent = 2,
        IKernelClock? clock = null,
        KernelTimingOptions? timing = null)
    {
        if (maxConcurrent < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxConcurrent));
        }

        _store = store;
        _capabilities = capabilities;
        _bindings = bindings.ToDictionary(BindingKey, StringComparer.Ordinal);
        _slots = new SemaphoreSlim(maxConcurrent, maxConcurrent);
        _clock = clock ?? SystemKernelClock.Instance;
        _timing = timing ?? KernelTimingOptions.Default;

        foreach (InferenceCapability capability in capabilities)
        {
            string key = BindingKey(capability.BindingId, capability.BindingVersion);
            if (!_bindings.ContainsKey(key))
            {
                throw new InvalidOperationException($"Capability {capability.CapabilityId} references missing binding {key}");
            }
        }
    }

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        DateTimeOffset now = _clock.UtcNow;
        await _store.InitializeAsync(_capabilities, now, cancellationToken).ConfigureAwait(false);
        foreach (InferenceCapability capability in _capabilities)
        {
            _nextProbeAt[capability.CapabilityId] = now;
        }
    }

    public void Start()
    {
        if (_scheduler is not null)
        {
            throw new InvalidOperationException("Kernel engine already started");
        }
        _scheduler = SchedulerLoopAsync(_shutdown.Token);
        Wake();
    }

    public async Task<string> SubmitAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        if (string.IsNullOrEmpty(request.PreparedInput))
        {
            throw new ArgumentException("preparedInput must not be empty", nameof(request));
        }
        if (Encoding.UTF8.GetByteCount(request.PreparedInput) > KernelProtocol.MaxPayloadBytes)
        {
            throw new ArgumentException(
                $"preparedInput exceeds the {KernelProtocol.MaxPayloadBytes} UTF-8 byte bound",
                nameof(request));
        }
        if (request.Deadline is { } deadline && request.EligibleAt is { } eligible && deadline <= eligible)
        {
            throw new ArgumentException("deadline must be later than eligibleAt", nameof(request));
        }

        string id = await _store.SubmitAsync(request, _clock.UtcNow, cancellationToken).ConfigureAwait(false);
        Wake();
        return id;
    }

    public Task<WorkInspection?> InspectAsync(string workId, CancellationToken cancellationToken = default) =>
        _store.GetInspectionAsync(workId, cancellationToken);

    public Task<WorkResultSnapshot?> ResultAsync(string workId, CancellationToken cancellationToken = default) =>
        _store.GetResultAsync(workId, cancellationToken);

    public async Task<WorkState?> CancelAsync(string workId, CancellationToken cancellationToken = default)
    {
        await _dispatchGate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            WorkState? state = await _store.CancelAsync(workId, cancellationToken).ConfigureAwait(false);
            if (_runningCancellations.TryGetValue(workId, out CancellationTokenSource? attempt))
            {
                attempt.Cancel();
            }
            Wake();
            return state;
        }
        finally
        {
            _dispatchGate.Release();
        }
    }

    public Task<bool?> ReleaseAsync(string workId, CancellationToken cancellationToken = default) =>
        _store.ReleaseAsync(workId, cancellationToken);

    public Task<IReadOnlyList<CapabilitySnapshot>> CapabilitiesAsync(CancellationToken cancellationToken = default) =>
        _store.Capabilities.GetSnapshotsAsync(cancellationToken);

    public async Task<IReadOnlyList<CapabilitySnapshot>> RefreshCapabilityStatesAsync(
        CancellationToken cancellationToken = default)
    {
        Task[] probes = _capabilities
            .Select(ObserveCapabilityAsync)
            .ToArray();
        await Task.WhenAll(probes).WaitAsync(cancellationToken).ConfigureAwait(false);
        return await CapabilitiesAsync(cancellationToken).ConfigureAwait(false);
    }

    private async Task SchedulerLoopAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                DateTimeOffset now = _clock.UtcNow;
                LaunchDueCapabilityObservations(now);
                await _store.ExpirePendingDeadlinesAsync(now, cancellationToken).ConfigureAwait(false);

                IReadOnlyList<StoredWork> works = await _store.GetEligibleWorkAsync(now, cancellationToken).ConfigureAwait(false);
                IReadOnlyList<CapabilitySnapshot> snapshots = await _store.Capabilities
                    .GetSnapshotsAsync(cancellationToken)
                    .ConfigureAwait(false);

                foreach (StoredWork work in works)
                {
                    if (_slots.CurrentCount == 0)
                    {
                        break;
                    }
                    if (_runningTasks.ContainsKey(work.WorkId))
                    {
                        continue;
                    }

                    DreDecision decision = _dre.Select(work.Request, snapshots);
                    if (decision.Kind == DreDecisionKind.NoAdmissibleCapability)
                    {
                        await _store.FailNoAdmissibleCapabilityAsync(work.WorkId, cancellationToken).ConfigureAwait(false);
                        continue;
                    }
                    if (decision.Kind == DreDecisionKind.WaitForAvailability)
                    {
                        continue;
                    }
                    await ScheduleAsync(work, decision.Capability!, cancellationToken).ConfigureAwait(false);
                }

                DateTimeOffset? nextWorkBoundary = await _store
                    .GetNextSchedulingBoundaryAsync(_clock.UtcNow, cancellationToken)
                    .ConfigureAwait(false);
                DateTimeOffset? nextProbe = NextProbeBoundary();
                DateTimeOffset? nextWake = Earliest(nextWorkBoundary, nextProbe);
                await WaitForWakeOrBoundaryAsync(nextWake, cancellationToken).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
    }

    private void LaunchDueCapabilityObservations(DateTimeOffset now)
    {
        foreach (InferenceCapability capability in _capabilities)
        {
            if (!_nextProbeAt.TryGetValue(capability.CapabilityId, out DateTimeOffset due) || due > now)
            {
                continue;
            }
            _nextProbeAt[capability.CapabilityId] = DateTimeOffset.MaxValue;
            _ = ObserveCapabilityAsync(capability);
        }
    }

    private async Task ObserveCapabilityAsync(InferenceCapability capability)
    {
        Lazy<Task> lazy = _probeTasks.GetOrAdd(
            capability.CapabilityId,
            _ => new Lazy<Task>(
                () => ObserveCapabilityCoreAsync(capability, _shutdown.Token),
                LazyThreadSafetyMode.ExecutionAndPublication));
        try
        {
            await lazy.Value.ConfigureAwait(false);
        }
        finally
        {
            _ = ((ICollection<KeyValuePair<string, Lazy<Task>>>)_probeTasks)
                .Remove(new KeyValuePair<string, Lazy<Task>>(capability.CapabilityId, lazy));
        }
    }

    private async Task ObserveCapabilityCoreAsync(
        InferenceCapability capability,
        CancellationToken cancellationToken)
    {
        CapabilityAvailability availability;
        IInferenceBinding binding = _bindings[BindingKey(capability.BindingId, capability.BindingVersion)];
        try
        {
            availability = await binding.ProbeAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch
        {
            availability = CapabilityAvailability.Unknown;
        }

        DateTimeOffset observedAt = _clock.UtcNow;
        await _store.Capabilities.SetStateAsync(
            capability.CapabilityId,
            availability,
            observedAt,
            CancellationToken.None).ConfigureAwait(false);
        _nextProbeAt[capability.CapabilityId] = observedAt + ReobserveInterval(availability);
        Wake();
    }

    private async Task ScheduleAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationToken cancellationToken)
    {
        if (!_slots.Wait(0))
        {
            return;
        }

        await _dispatchGate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            DateTimeOffset started = _clock.UtcNow;
            int? attemptNumber = await _store
                .TryBeginAttemptAsync(work, capability, started, cancellationToken)
                .ConfigureAwait(false);
            if (attemptNumber is null)
            {
                _slots.Release();
                return;
            }

            var attemptCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            _runningCancellations[work.WorkId] = attemptCancellation;
            Task task = ExecuteAttemptAsync(work, capability, attemptNumber.Value, attemptCancellation);
            _runningTasks[work.WorkId] = task;
        }
        finally
        {
            _dispatchGate.Release();
        }
    }

    private async Task ExecuteAttemptAsync(
        StoredWork work,
        InferenceCapability capability,
        int attemptNumber,
        CancellationTokenSource cancellation)
    {
        var stopwatch = Stopwatch.StartNew();
        BindingExecutionResult result;
        try
        {
            IInferenceBinding binding = _bindings[BindingKey(capability.BindingId, capability.BindingVersion)];
            result = await _bindingExecutor.ExecuteAsync(
                binding,
                new InferenceExecutionRequest(work.Request.PreparedInput),
                cancellation.Token).ConfigureAwait(false);
        }
        finally
        {
            stopwatch.Stop();
        }

        try
        {
            DateTimeOffset endedAt = _clock.UtcNow;
            await _store.CompleteAttemptAsync(
                work.WorkId,
                attemptNumber,
                result,
                endedAt,
                stopwatch.ElapsedMilliseconds,
                CancellationToken.None).ConfigureAwait(false);
            await RecordExecutionAvailabilityAsync(capability, result, endedAt).ConfigureAwait(false);
        }
        finally
        {
            CleanupRunning(work.WorkId, cancellation);
        }
    }

    private async Task RecordExecutionAvailabilityAsync(
        InferenceCapability capability,
        BindingExecutionResult result,
        DateTimeOffset observedAt)
    {
        CapabilityAvailability availability = result.Failure?.Kind switch
        {
            PhysicalFailureKind.LaunchFailed => CapabilityAvailability.Unavailable,
            PhysicalFailureKind.CompletionUnknown => CapabilityAvailability.Unknown,
            _ => CapabilityAvailability.Available
        };
        await _store.Capabilities.SetStateAsync(
            capability.CapabilityId,
            availability,
            observedAt,
            CancellationToken.None).ConfigureAwait(false);
        _nextProbeAt[capability.CapabilityId] = observedAt + ReobserveInterval(availability);
    }

    private void CleanupRunning(string workId, CancellationTokenSource cancellation)
    {
        _runningCancellations.TryRemove(workId, out _);
        _runningTasks.TryRemove(workId, out _);
        cancellation.Dispose();
        _slots.Release();
        Wake();
    }

    private TimeSpan ReobserveInterval(CapabilityAvailability availability) => availability switch
    {
        CapabilityAvailability.Unavailable => _timing.UnavailableReobserveInterval,
        CapabilityAvailability.Unknown => _timing.UnknownReobserveInterval,
        CapabilityAvailability.Available => _timing.AvailableReobserveInterval,
        _ => throw new ArgumentOutOfRangeException(nameof(availability))
    };

    private DateTimeOffset? NextProbeBoundary()
    {
        DateTimeOffset? earliest = null;
        foreach (DateTimeOffset due in _nextProbeAt.Values)
        {
            if (due == DateTimeOffset.MaxValue)
            {
                continue;
            }
            earliest = earliest is null || due < earliest ? due : earliest;
        }
        return earliest;
    }

    private async Task WaitForWakeOrBoundaryAsync(
        DateTimeOffset? boundary,
        CancellationToken cancellationToken)
    {
        if (boundary is null)
        {
            await _wake.WaitAsync(cancellationToken).ConfigureAwait(false);
            return;
        }

        TimeSpan delay = boundary.Value - _clock.UtcNow;
        if (delay <= TimeSpan.Zero)
        {
            return;
        }

        using var linked = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        Task wake = _wake.WaitAsync(linked.Token);
        Task timer = _clock.DelayAsync(delay, linked.Token);
        Task completed = await Task.WhenAny(wake, timer).ConfigureAwait(false);
        linked.Cancel();
        try
        {
            await completed.ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
        }
    }

    private void Wake()
    {
        if (_wake.CurrentCount == 0)
        {
            try
            {
                _wake.Release();
            }
            catch (SemaphoreFullException)
            {
            }
        }
    }

    private static DateTimeOffset? Earliest(DateTimeOffset? left, DateTimeOffset? right) =>
        left is null ? right : right is null ? left : left <= right ? left : right;

    private static string BindingKey(IInferenceBinding binding) => BindingKey(binding.BindingId, binding.BindingVersion);
    private static string BindingKey(string id, string version) => $"{id}@{version}";

    public async ValueTask DisposeAsync()
    {
        _shutdown.Cancel();
        Wake();
        foreach (CancellationTokenSource attempt in _runningCancellations.Values)
        {
            attempt.Cancel();
        }

        if (_scheduler is not null)
        {
            try
            {
                await _scheduler.ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
            }
        }

        Task[] probes = _probeTasks.Values.Select(lazy => lazy.Value).ToArray();
        Task[] running = _runningTasks.Values.ToArray();
        if (probes.Length > 0)
        {
            try
            {
                await Task.WhenAll(probes).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
            }
        }
        if (running.Length > 0)
        {
            await Task.WhenAll(running).ConfigureAwait(false);
        }

        _shutdown.Dispose();
        _dispatchGate.Dispose();
        _slots.Dispose();
        _wake.Dispose();
    }
}
