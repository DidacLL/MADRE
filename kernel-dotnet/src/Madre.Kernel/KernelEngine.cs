using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text;

namespace Madre.Kernel;

public sealed class KernelEngine : IAsyncDisposable
{
    private readonly WorkStore _store;
    private readonly DreSelector _dre;
    private readonly IReadOnlyList<InferenceCapability> _capabilities;
    private readonly Dictionary<string, IInferenceBinding> _bindings;
    private readonly MafTwoStagePhysicalStrategy _twoStageStrategy;
    private readonly bool _validationHoldCheckpointedResume;
    private readonly SemaphoreSlim _slots;
    private readonly CancellationTokenSource _shutdown = new();
    private readonly ConcurrentDictionary<string, CancellationTokenSource> _runningCancellations = new();
    private readonly ConcurrentDictionary<string, Task> _runningTasks = new();
    private Task? _scheduler;

    public KernelEngine(
        WorkStore store,
        IReadOnlyList<InferenceCapability> capabilities,
        IReadOnlyList<IInferenceBinding> bindings,
        int maxConcurrent = 2,
        bool validationHoldCheckpointedResume = false)
    {
        if (maxConcurrent < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxConcurrent));
        }
        _store = store;
        _dre = new DreSelector();
        _capabilities = capabilities;
        _bindings = bindings.ToDictionary(BindingKey, StringComparer.Ordinal);
        _twoStageStrategy = new MafTwoStagePhysicalStrategy(
            store,
            _bindings,
            store.DatabasePath + ".maf-checkpoints");
        _validationHoldCheckpointedResume = validationHoldCheckpointedResume;
        _slots = new SemaphoreSlim(maxConcurrent, maxConcurrent);

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
        await _store.InitializeAsync(_capabilities, cancellationToken).ConfigureAwait(false);
    }

    public void Start()
    {
        if (_scheduler is not null)
        {
            throw new InvalidOperationException("Kernel engine already started");
        }
        _scheduler = SchedulerLoopAsync(_shutdown.Token);
    }

    public async Task<string> SubmitAsync(PhysicalInferenceRequest request, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        if (string.IsNullOrEmpty(request.PreparedInput))
        {
            throw new ArgumentException("preparedInput must not be empty", nameof(request));
        }
        if (Encoding.UTF8.GetByteCount(request.PreparedInput) > KernelContract.MaxPayloadBytes)
        {
            throw new ArgumentException("preparedInput exceeds the 1 MiB validation bound", nameof(request));
        }
        return await _store.SubmitAsync(request, cancellationToken).ConfigureAwait(false);
    }

    public Task<WorkInspection?> InspectAsync(string workId, CancellationToken cancellationToken = default) =>
        _store.GetInspectionAsync(workId, cancellationToken);

    public Task<WorkResultSnapshot?> ResultAsync(string workId, CancellationToken cancellationToken = default) =>
        _store.GetResultAsync(workId, cancellationToken);

    public async Task<WorkState?> CancelAsync(string workId, CancellationToken cancellationToken = default)
    {
        WorkState? state = await _store.CancelAsync(workId, cancellationToken).ConfigureAwait(false);
        if (_runningCancellations.TryGetValue(workId, out CancellationTokenSource? attempt))
        {
            attempt.Cancel();
        }
        return state;
    }

    public Task<bool?> ReleaseAsync(string workId, CancellationToken cancellationToken = default) =>
        _store.ReleaseAsync(workId, cancellationToken);

    public Task SetCapabilityStateAsync(string capabilityId, CapabilityAvailability availability, CancellationToken cancellationToken = default) =>
        _store.SetCapabilityStateAsync(capabilityId, availability, cancellationToken);

    public Task<IReadOnlyList<CapabilitySnapshot>> CapabilitiesAsync(CancellationToken cancellationToken = default) =>
        _store.GetCapabilitiesAsync(cancellationToken);

    private async Task SchedulerLoopAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                DateTimeOffset now = DateTimeOffset.UtcNow;
                await _store.ExpireQueuedDeadlinesAsync(now, cancellationToken).ConfigureAwait(false);
                IReadOnlyList<StoredWork> works = await _store.GetEligibleWorkAsync(now, 64, cancellationToken).ConfigureAwait(false);
                IReadOnlyList<CapabilitySnapshot> snapshots = await _store.GetCapabilitiesAsync(cancellationToken).ConfigureAwait(false);

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

                    if (work.State == WorkState.Checkpointed)
                    {
                        await ScheduleCheckpointResumeAsync(work, snapshots, cancellationToken).ConfigureAwait(false);
                        continue;
                    }

                    DreDecision decision = _dre.Select(work.Request, snapshots);
                    if (decision.Kind == DreDecisionKind.NoAdmissibleCapability)
                    {
                        await _store.FailWithoutAttemptAsync(work.WorkId, "NO_ADMISSIBLE_CAPABILITY", cancellationToken).ConfigureAwait(false);
                        continue;
                    }
                    if (decision.Kind == DreDecisionKind.WaitForAvailability)
                    {
                        continue;
                    }
                    InferenceCapability capability = decision.Capability!;

                    if (decision.UseCheckpointedTwoStageStrategy)
                    {
                        await ScheduleTwoStageStartAsync(work, capability, cancellationToken).ConfigureAwait(false);
                    }
                    else
                    {
                        await ScheduleSingleInferenceAsync(work, capability, cancellationToken).ConfigureAwait(false);
                    }
                }

                await Task.Delay(25, cancellationToken).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
    }

    private async Task ScheduleSingleInferenceAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationToken cancellationToken)
    {
        if (!_slots.Wait(0))
        {
            return;
        }
        DateTimeOffset started = DateTimeOffset.UtcNow;
        int? attemptNumber = await _store.TryBeginAttemptAsync(work, capability, started, cancellationToken).ConfigureAwait(false);
        if (attemptNumber is null)
        {
            _slots.Release();
            return;
        }

        var attemptCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _runningCancellations[work.WorkId] = attemptCancellation;
        Task task = ExecuteAttemptAsync(work, capability, attemptNumber.Value, started, attemptCancellation);
        _runningTasks[work.WorkId] = task;
    }

    private async Task ScheduleTwoStageStartAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationToken cancellationToken)
    {
        if (!_slots.Wait(0))
        {
            return;
        }
        if (!await _store.TryClaimCheckpointedTwoStageAsync(work, capability, cancellationToken).ConfigureAwait(false))
        {
            _slots.Release();
            return;
        }

        var strategyCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _runningCancellations[work.WorkId] = strategyCancellation;
        Task task = ExecuteTwoStageStartAsync(work, capability, strategyCancellation);
        _runningTasks[work.WorkId] = task;
    }

    private async Task ScheduleCheckpointResumeAsync(
        StoredWork work,
        IReadOnlyList<CapabilitySnapshot> snapshots,
        CancellationToken cancellationToken)
    {
        if (_validationHoldCheckpointedResume)
        {
            return;
        }
        if (work.CancelRequested)
        {
            return;
        }
        if (work.StrategyType != KernelContract.CheckpointedTwoStageStrategyType
            || work.StrategyVersion != KernelContract.CheckpointedTwoStageStrategyVersion
            || work.CheckpointSessionId is null
            || work.CheckpointId is null
            || work.SelectedCapabilityId is null)
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "INCOMPATIBLE_STRATEGY_CONTINUATION", cancellationToken).ConfigureAwait(false);
            return;
        }

        DreDecision decision = _dre.SelectCheckpointResume(work.Request, work.SelectedCapabilityId, snapshots);
        if (decision.Kind == DreDecisionKind.NoAdmissibleCapability)
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "CHECKPOINT_CAPABILITY_INCOMPATIBLE", cancellationToken).ConfigureAwait(false);
            return;
        }
        if (decision.Kind == DreDecisionKind.WaitForAvailability)
        {
            return;
        }
        if (!_slots.Wait(0))
        {
            return;
        }

        var strategyCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _runningCancellations[work.WorkId] = strategyCancellation;
        Task task = ExecuteTwoStageResumeAsync(work, decision.Capability!, strategyCancellation);
        _runningTasks[work.WorkId] = task;
    }

    private async Task ExecuteTwoStageStartAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationTokenSource cancellation)
    {
        try
        {
            await _twoStageStrategy.StartAsync(work, capability, cancellation.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            await _store.FailStrategyIfActiveAsync(
                work.WorkId,
                $"MAF_STRATEGY_START_FAILURE:{ex.GetType().Name}",
                CancellationToken.None).ConfigureAwait(false);
        }
        finally
        {
            CleanupRunning(work.WorkId, cancellation);
        }
    }

    private async Task ExecuteTwoStageResumeAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationTokenSource cancellation)
    {
        try
        {
            await _twoStageStrategy.ResumeAsync(work, capability, cancellation.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            await _store.FailStrategyIfActiveAsync(
                work.WorkId,
                $"MAF_STRATEGY_RESUME_FAILURE:{ex.GetType().Name}",
                CancellationToken.None).ConfigureAwait(false);
        }
        finally
        {
            CleanupRunning(work.WorkId, cancellation);
        }
    }

    private async Task ExecuteAttemptAsync(
        StoredWork work,
        InferenceCapability capability,
        int attemptNumber,
        DateTimeOffset started,
        CancellationTokenSource cancellation)
    {
        var stopwatch = Stopwatch.StartNew();
        BindingExecutionResult result;
        try
        {
            IInferenceBinding binding = _bindings[BindingKey(capability.BindingId, capability.BindingVersion)];
            result = await binding.ExecuteAsync(work.Request, cancellation.Token).ConfigureAwait(false);
            result = EnforceResultContract(result);
        }
        catch (OperationCanceledException)
        {
            result = BindingExecutionResult.Unknown("BINDING_CANCELLATION_COMPLETION_UNKNOWN");
        }
        catch (Exception ex)
        {
            result = BindingExecutionResult.Unknown($"BINDING_COMPLETION_UNKNOWN:{ex.GetType().Name}");
        }
        finally
        {
            stopwatch.Stop();
        }

        try
        {
            await _store.CompleteAttemptAsync(
                work.WorkId,
                attemptNumber,
                result,
                DateTimeOffset.UtcNow,
                stopwatch.ElapsedMilliseconds,
                CancellationToken.None).ConfigureAwait(false);
        }
        finally
        {
            CleanupRunning(work.WorkId, cancellation);
        }
    }

    private void CleanupRunning(string workId, CancellationTokenSource cancellation)
    {
        _runningCancellations.TryRemove(workId, out _);
        _runningTasks.TryRemove(workId, out _);
        cancellation.Dispose();
        _slots.Release();
    }

    private static BindingExecutionResult EnforceResultContract(BindingExecutionResult result)
    {
        if (result.Outcome != PhysicalAttemptOutcome.Succeeded)
        {
            return result;
        }
        if (result.Result is null)
        {
            return BindingExecutionResult.Failure("INVALID_BINDING_SUCCESS_RESULT");
        }
        if (Encoding.UTF8.GetByteCount(result.Result) > KernelContract.MaxPayloadBytes)
        {
            return BindingExecutionResult.Failure("OUTPUT_LIMIT_EXCEEDED");
        }
        return result;
    }

    private static string BindingKey(IInferenceBinding binding) => BindingKey(binding.BindingId, binding.BindingVersion);
    private static string BindingKey(string id, string version) => $"{id}@{version}";

    public async ValueTask DisposeAsync()
    {
        _shutdown.Cancel();
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
        Task[] running = _runningTasks.Values.ToArray();
        if (running.Length > 0)
        {
            await Task.WhenAll(running).ConfigureAwait(false);
        }
        _shutdown.Dispose();
        _slots.Dispose();
    }
}
