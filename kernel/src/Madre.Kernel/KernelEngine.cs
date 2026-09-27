using System.Collections.Concurrent;
using System.Diagnostics;
using System.Runtime.ExceptionServices;
using System.Text;

namespace Madre.Kernel;

public sealed class KernelEngine : IAsyncDisposable
{
    private readonly WorkStore _store;
    private readonly DreSelector _dre = new();
    private readonly BindingExecutor _bindingExecutor = new();
    private readonly IReadOnlyList<InferenceCapability> _capabilities;
    private readonly Dictionary<string, InferenceCapability> _capabilitiesById;
    private readonly Dictionary<string, IInferenceBinding> _bindings;
    private readonly SemaphoreSlim _slots;
    private readonly SemaphoreSlim _dispatchGate = new(1, 1);
    private readonly SemaphoreSlim _wake = new(0, 1);
    private readonly CancellationTokenSource _shutdown = new();
    private readonly ConcurrentDictionary<string, CancellationTokenSource> _runningCancellations = new();
    private readonly ConcurrentDictionary<string, Task> _runningTasks = new();
    private readonly ConcurrentDictionary<string, Lazy<Task>> _probeTasks = new();
    private readonly ConcurrentDictionary<string, DateTimeOffset> _nextProbeAt = new();
    private readonly TaskCompletionSource<bool> _fatal = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly IKernelClock _clock;
    private readonly KernelTimingOptions _timing;
    private Task? _scheduler;

    public KernelEngine(
        WorkStore store,
        IReadOnlyList<InferenceCapability> capabilities,
        IReadOnlyList<IInferenceBinding> bindings,
        int maxConcurrent,
        IKernelClock? clock = null,
        KernelTimingOptions? timing = null)
    {
        if (maxConcurrent < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxConcurrent));
        }

        _store = store;
        _capabilities = capabilities;
        _capabilitiesById = capabilities.ToDictionary(capability => capability.CapabilityId, StringComparer.Ordinal);
        _bindings = bindings.ToDictionary(BindingKey, StringComparer.Ordinal);
        _slots = new SemaphoreSlim(maxConcurrent, maxConcurrent);
        _clock = clock ?? SystemKernelClock.Instance;
        _timing = timing ?? KernelTimingOptions.Default;
        if (_timing.ProbeTimeout <= TimeSpan.Zero || _timing.UnavailableReobserveInterval <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(nameof(timing), "Kernel timing values must be positive");
        }

        foreach (InferenceCapability capability in capabilities)
        {
            string key = BindingKey(capability.BindingId, capability.BindingVersion);
            if (!_bindings.ContainsKey(key))
            {
                throw new InvalidOperationException($"Capability {capability.CapabilityId} references missing binding {key}");
            }
        }
    }

    public Task FatalCompletion => _fatal.Task;

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        await _store.InitializeAsync(_capabilities, _clock.UtcNow, cancellationToken).ConfigureAwait(false);
    }

    public void Start()
    {
        if (_scheduler is not null)
        {
            throw new InvalidOperationException("Kernel engine already started");
        }
        _scheduler = SchedulerLoopAsync(_shutdown.Token);
        foreach (InferenceCapability capability in _capabilities)
        {
            _ = ObserveCapabilityAsync(capability);
        }
        Wake();
    }

    public void ThrowIfFaulted()
    {
        if (_fatal.Task.Exception is not { } aggregate)
        {
            return;
        }
        ExceptionDispatchInfo.Capture(aggregate.GetBaseException()).Throw();
    }

    public async Task<string> SubmitAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        if (string.IsNullOrEmpty(request.PreparedInput))
        {
            throw new ArgumentException("preparedInput must not be empty", nameof(request));
        }
        if (!Enum.IsDefined(typeof(InferenceEffort), request.RequestedEffort)
            || !Enum.IsDefined(typeof(WorkUrgency), request.Urgency)
            || !Enum.IsDefined(typeof(ExecutionBoundary), request.ExecutionBoundary))
        {
            throw new ArgumentException("physical request contains an invalid enum value", nameof(request));
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
        Task[] probes = _capabilities.Select(ObserveCapabilityAsync).ToArray();
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
                await _store.ExpirePendingDeadlinesAsync(now, cancellationToken).ConfigureAwait(false);

                IReadOnlyList<SchedulingWork> works = await _store.GetEligibleWorkAsync(now, cancellationToken).ConfigureAwait(false);
                IReadOnlyList<CapabilitySnapshot> snapshots = await _store.Capabilities
                    .GetSnapshotsAsync(cancellationToken)
                    .ConfigureAwait(false);
                var demandedUnavailable = new HashSet<string>(StringComparer.Ordinal);

                foreach (SchedulingWork work in works)
                {
                    if (_runningTasks.ContainsKey(work.WorkId))
                    {
                        continue;
                    }

                    DreDecision decision = _dre.Select(work, snapshots);
                    if (decision.Kind == DreDecisionKind.NoAdmissibleCapability)
                    {
                        await _store.FailNoAdmissibleCapabilityAsync(work.WorkId, cancellationToken).ConfigureAwait(false);
                        continue;
                    }
                    if (decision.Kind == DreDecisionKind.WaitForAvailability)
                    {
                        RegisterUnavailableDemand(work, snapshots, now, demandedUnavailable);
                        continue;
                    }
                    if (_slots.CurrentCount > 0)
                    {
                        await ScheduleAsync(work, decision.Capability!, cancellationToken).ConfigureAwait(false);
                    }
                }

                PruneInactiveProbeDemand(demandedUnavailable);
                LaunchDueDemandObservations(now, demandedUnavailable);

                DateTimeOffset? nextWorkBoundary = await _store
                    .GetNextSchedulingBoundaryAsync(now, cancellationToken)
                    .ConfigureAwait(false);
                DateTimeOffset? nextWake = Earliest(nextWorkBoundary, NextProbeBoundary());
                await WaitForWakeOrBoundaryAsync(nextWake, cancellationToken).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        catch (Exception ex)
        {
            ReportFatal(ex);
            throw;
        }
    }

    private void RegisterUnavailableDemand(
        SchedulingWork work,
        IReadOnlyList<CapabilitySnapshot> snapshots,
        DateTimeOffset now,
        HashSet<string> demanded)
    {
        foreach (CapabilitySnapshot snapshot in snapshots)
        {
            if (snapshot.State.Availability != CapabilityAvailability.Unavailable
                || !DreSelector.IsAdmissible(work, snapshot.Capability))
            {
                continue;
            }
            demanded.Add(snapshot.Capability.CapabilityId);
            DateTimeOffset due = (snapshot.State.ObservedAt ?? now) + _timing.UnavailableReobserveInterval;
            _nextProbeAt.AddOrUpdate(
                snapshot.Capability.CapabilityId,
                due,
                (_, current) => current == DateTimeOffset.MaxValue || current <= due ? current : due);
        }
    }

    private void PruneInactiveProbeDemand(HashSet<string> demanded)
    {
        foreach (string capabilityId in _nextProbeAt.Keys)
        {
            if (!demanded.Contains(capabilityId))
            {
                _nextProbeAt.TryRemove(capabilityId, out _);
            }
        }
    }

    private void LaunchDueDemandObservations(DateTimeOffset now, HashSet<string> demanded)
    {
        foreach (string capabilityId in demanded)
        {
            if (!_nextProbeAt.TryGetValue(capabilityId, out DateTimeOffset due) || due > now)
            {
                continue;
            }
            _nextProbeAt[capabilityId] = DateTimeOffset.MaxValue;
            _ = ObserveCapabilityAsync(_capabilitiesById[capabilityId]);
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
        catch (OperationCanceledException) when (_shutdown.IsCancellationRequested)
        {
        }
        catch (Exception ex)
        {
            ReportFatal(ex);
            throw;
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
        IInferenceBinding binding = _bindings[BindingKey(capability.BindingId, capability.BindingVersion)];
        CapabilityAvailability availability;
        using var probeCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        Task<CapabilityAvailability>? physicalProbe = null;
        try
        {
            physicalProbe = binding.ProbeAsync(probeCancellation.Token);
            availability = await physicalProbe
                .WaitAsync(_timing.ProbeTimeout, cancellationToken)
                .ConfigureAwait(false);
        }
        catch (TimeoutException)
        {
            probeCancellation.Cancel();
            ObserveLateProbeFailure(physicalProbe);
            availability = CapabilityAvailability.Unknown;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            probeCancellation.Cancel();
            ObserveLateProbeFailure(physicalProbe);
            throw;
        }
        catch
        {
            availability = CapabilityAvailability.Unknown;
        }

        cancellationToken.ThrowIfCancellationRequested();
        DateTimeOffset observedAt = _clock.UtcNow;
        await _store.Capabilities.SetStateAsync(
            capability.CapabilityId,
            availability,
            observedAt,
            CancellationToken.None).ConfigureAwait(false);
        _nextProbeAt.TryRemove(capability.CapabilityId, out _);
        Wake();
    }

    private static void ObserveLateProbeFailure(Task<CapabilityAvailability>? probe)
    {
        if (probe is null || probe.IsCompleted)
        {
            return;
        }
        _ = probe.ContinueWith(
            completed => _ = completed.Exception,
            CancellationToken.None,
            TaskContinuationOptions.OnlyOnFaulted | TaskContinuationOptions.ExecuteSynchronously,
            TaskScheduler.Default);
    }

    private async Task ScheduleAsync(
        SchedulingWork work,
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
            ClaimedAttempt? claimed = await _store
                .TryBeginAttemptAsync(work, capability, started, cancellationToken)
                .ConfigureAwait(false);
            if (claimed is null)
            {
                _slots.Release();
                return;
            }

            var attemptCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            _runningCancellations[work.WorkId] = attemptCancellation;
            Task task = ExecuteAttemptAsync(
                work.WorkId,
                claimed.PreparedInput,
                capability,
                claimed.AttemptNumber,
                attemptCancellation);
            _runningTasks[work.WorkId] = task;
        }
        finally
        {
            _dispatchGate.Release();
        }
    }

    private async Task ExecuteAttemptAsync(
        string workId,
        string preparedInput,
        InferenceCapability capability,
        int attemptNumber,
        CancellationTokenSource cancellation)
    {
        var stopwatch = Stopwatch.StartNew();
        try
        {
            IInferenceBinding binding = _bindings[BindingKey(capability.BindingId, capability.BindingVersion)];
            BindingExecutionResult result = await _bindingExecutor.ExecuteAsync(
                binding,
                new InferenceExecutionRequest(preparedInput),
                cancellation.Token).ConfigureAwait(false);
            stopwatch.Stop();

            DateTimeOffset endedAt = _clock.UtcNow;
            await _store.CompleteAttemptAsync(
                workId,
                attemptNumber,
                result,
                endedAt,
                stopwatch.ElapsedMilliseconds,
                CancellationToken.None).ConfigureAwait(false);
            await RecordExecutionAvailabilityAsync(capability, result, endedAt).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (_shutdown.IsCancellationRequested)
        {
        }
        catch (Exception ex)
        {
            ReportFatal(ex);
            throw;
        }
        finally
        {
            stopwatch.Stop();
            CleanupRunning(workId, cancellation);
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
    }

    private void CleanupRunning(string workId, CancellationTokenSource cancellation)
    {
        _runningCancellations.TryRemove(workId, out _);
        _runningTasks.TryRemove(workId, out _);
        cancellation.Dispose();
        _slots.Release();
        Wake();
    }

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

    private void ReportFatal(Exception exception)
    {
        if (_shutdown.IsCancellationRequested)
        {
            return;
        }
        if (_fatal.TrySetException(exception))
        {
            _shutdown.Cancel();
            foreach (CancellationTokenSource attempt in _runningCancellations.Values)
            {
                attempt.Cancel();
            }
            Wake();
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
            catch (Exception)
            {
            }
        }

        Task[] probes = _probeTasks.Values
            .Where(lazy => lazy.IsValueCreated)
            .Select(lazy => lazy.Value)
            .ToArray();
        Task[] running = _runningTasks.Values.ToArray();
        if (probes.Length > 0)
        {
            try
            {
                await Task.WhenAll(probes).ConfigureAwait(false);
            }
            catch (Exception)
            {
            }
        }
        if (running.Length > 0)
        {
            try
            {
                await Task.WhenAll(running).ConfigureAwait(false);
            }
            catch (Exception)
            {
            }
        }

        _shutdown.Dispose();
        _dispatchGate.Dispose();
        _slots.Dispose();
        _wake.Dispose();
    }
}
