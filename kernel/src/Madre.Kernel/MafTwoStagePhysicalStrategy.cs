using System.Diagnostics;
using System.Text;
using Microsoft.Agents.AI.Workflows;
using Microsoft.Agents.AI.Workflows.Checkpointing;

namespace Madre.Kernel;

// One concrete checkpointable physical strategy. This is deliberately not a strategy framework.
internal sealed class MafTwoStagePhysicalStrategy
{
    private readonly WorkStore _store;
    private readonly IReadOnlyDictionary<string, IInferenceBinding> _bindings;
    private readonly string _checkpointRoot;

    public MafTwoStagePhysicalStrategy(
        WorkStore store,
        IReadOnlyDictionary<string, IInferenceBinding> bindings,
        string checkpointRoot)
    {
        _store = store;
        _bindings = bindings;
        _checkpointRoot = checkpointRoot;
    }

    public async Task StartAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationToken cancellationToken)
    {
        string directory = CheckpointDirectory(work.WorkId);
        using var checkpointStore = new FileSystemJsonCheckpointStore(new DirectoryInfo(directory));
        CheckpointManager checkpointManager = CheckpointManager.CreateJson(checkpointStore);
        Workflow workflow = BuildWorkflow(work, capability);

        await using StreamingRun run = await InProcessExecution.RunStreamingAsync(
            workflow,
            work.Request.PreparedInput,
            checkpointManager,
            sessionId: work.WorkId,
            cancellationToken: cancellationToken).ConfigureAwait(false);

        ExternalRequest? pending = null;
        CheckpointInfo? checkpoint = null;
        bool workflowError = false;

        await foreach (WorkflowEvent evt in run.WatchStreamAsync(blockOnPendingRequest: false, cancellationToken).ConfigureAwait(false))
        {
            if (evt is RequestInfoEvent requestInfo)
            {
                pending = requestInfo.Request;
            }
            else if (evt is SuperStepCompletedEvent completed && completed.CompletionInfo?.Checkpoint is { } current)
            {
                checkpoint = current;
            }
            else if (evt is WorkflowErrorEvent or ExecutorFailedEvent)
            {
                workflowError = true;
            }
        }

        if (workflowError || pending is null || checkpoint is null)
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "MAF_STAGE_A_CHECKPOINT_FAILED", CancellationToken.None).ConfigureAwait(false);
            return;
        }

        if (!pending.TryGetDataAs<string>(out _))
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "MAF_STAGE_A_CHECKPOINT_PAYLOAD_INVALID", CancellationToken.None).ConfigureAwait(false);
            return;
        }

        if (!await _store.MarkCheckpointedAsync(
            work.WorkId,
            checkpoint.SessionId,
            checkpoint.CheckpointId,
            CancellationToken.None).ConfigureAwait(false))
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "MAF_CHECKPOINT_LINK_FAILED", CancellationToken.None).ConfigureAwait(false);
        }
    }

    public async Task ResumeAsync(
        StoredWork work,
        InferenceCapability capability,
        CancellationToken cancellationToken)
    {
        if (work.CheckpointSessionId is null || work.CheckpointId is null)
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "MISSING_MAF_CHECKPOINT_REFERENCE", CancellationToken.None).ConfigureAwait(false);
            return;
        }

        string directory = CheckpointDirectory(work.WorkId);
        using var checkpointStore = new FileSystemJsonCheckpointStore(new DirectoryInfo(directory));
        CheckpointManager checkpointManager = CheckpointManager.CreateJson(checkpointStore);
        Workflow workflow = BuildWorkflow(work, capability);
        var checkpoint = new CheckpointInfo(work.CheckpointSessionId, work.CheckpointId);

        await using StreamingRun run = await InProcessExecution.ResumeStreamingAsync(
            workflow,
            checkpoint,
            checkpointManager,
            cancellationToken).ConfigureAwait(false);

        ExternalRequest? pending = null;
        bool workflowError = false;
        await foreach (WorkflowEvent evt in run.WatchStreamAsync(blockOnPendingRequest: false, cancellationToken).ConfigureAwait(false))
        {
            if (evt is RequestInfoEvent requestInfo)
            {
                pending = requestInfo.Request;
            }
            else if (evt is WorkflowErrorEvent or ExecutorFailedEvent)
            {
                workflowError = true;
            }
        }

        if (workflowError || pending is null || !pending.TryGetDataAs<string>(out string? stageAOutput) || stageAOutput is null)
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "MAF_CHECKPOINT_RESUME_FAILED", CancellationToken.None).ConfigureAwait(false);
            return;
        }

        await run.SendResponseAsync(pending.CreateResponse(stageAOutput)).ConfigureAwait(false);

        await foreach (WorkflowEvent evt in run.WatchStreamAsync(blockOnPendingRequest: false, cancellationToken).ConfigureAwait(false))
        {
            if (evt is WorkflowErrorEvent or ExecutorFailedEvent)
            {
                workflowError = true;
            }
        }

        if (workflowError)
        {
            await _store.FailStrategyIfActiveAsync(work.WorkId, "MAF_STAGE_B_WORKFLOW_FAILED", CancellationToken.None).ConfigureAwait(false);
        }
    }

    public void DeleteCheckpointState(string workId)
    {
        string directory = CheckpointDirectory(workId);
        if (!Directory.Exists(directory))
        {
            return;
        }

        try
        {
            Directory.Delete(directory, recursive: true);
        }
        catch (DirectoryNotFoundException)
        {
            // A concurrent idempotent release already removed the same subordinate state.
        }
    }

    private Workflow BuildWorkflow(StoredWork work, InferenceCapability capability)
    {
        var stageA = new PhysicalStageAExecutor(
            "madre-physical-stage-a",
            (input, ct) => ExecuteStageAsync(work, capability, input, resumeFromCheckpoint: false, finalStage: false, ct));
        RequestPort<string, string> checkpointGate = RequestPort.Create<string, string>("madre-stage-a-durable-boundary");
        var stageB = new PhysicalStageBExecutor(
            "madre-physical-stage-b",
            (input, ct) => ExecuteStageAsync(work, capability, input, resumeFromCheckpoint: true, finalStage: true, ct));

        return new WorkflowBuilder(stageA)
            .AddEdge(stageA, checkpointGate)
            .AddEdge(checkpointGate, stageB)
            .WithOutputFrom(stageB)
            .Build();
    }

    private async Task<string> ExecuteStageAsync(
        StoredWork work,
        InferenceCapability capability,
        string input,
        bool resumeFromCheckpoint,
        bool finalStage,
        CancellationToken cancellationToken)
    {
        DateTimeOffset started = DateTimeOffset.UtcNow;
        int? attemptNumber = await _store.TryBeginCheckpointedStrategyAttemptAsync(
            work.WorkId,
            capability,
            started,
            resumeFromCheckpoint,
            cancellationToken).ConfigureAwait(false);
        if (attemptNumber is null)
        {
            throw new InvalidOperationException("MADRE Work did not authorize this physical strategy stage");
        }

        var stopwatch = Stopwatch.StartNew();
        BindingExecutionResult result;
        try
        {
            IInferenceBinding binding = _bindings[BindingKey(capability.BindingId, capability.BindingVersion)];
            PhysicalInferenceRequest stageRequest = work.Request with { PreparedInput = input };
            result = await binding.ExecuteAsync(stageRequest, cancellationToken).ConfigureAwait(false);
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

        await _store.CompleteCheckpointedStrategyAttemptAsync(
            work.WorkId,
            attemptNumber.Value,
            result,
            DateTimeOffset.UtcNow,
            stopwatch.ElapsedMilliseconds,
            finalStage,
            CancellationToken.None).ConfigureAwait(false);

        if (result.Outcome != PhysicalAttemptOutcome.Succeeded || result.Result is null)
        {
            throw new InvalidOperationException(result.TechnicalFailure ?? "physical inference stage did not succeed");
        }
        return result.Result;
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

    private string CheckpointDirectory(string workId) => Path.Combine(_checkpointRoot, workId);
    private static string BindingKey(string id, string version) => $"{id}@{version}";

    [SendsMessage(typeof(string))]
    private sealed class PhysicalStageAExecutor(
        string id,
        Func<string, CancellationToken, Task<string>> execute) : Executor<string>(id)
    {
        public override async ValueTask HandleAsync(
            string message,
            IWorkflowContext context,
            CancellationToken cancellationToken = default)
        {
            string output = await execute(message, cancellationToken).ConfigureAwait(false);
            await context.SendMessageAsync(output, cancellationToken: cancellationToken).ConfigureAwait(false);
        }
    }

    [YieldsOutput(typeof(string))]
    private sealed class PhysicalStageBExecutor(
        string id,
        Func<string, CancellationToken, Task<string>> execute) : Executor<string>(id)
    {
        public override async ValueTask HandleAsync(
            string message,
            IWorkflowContext context,
            CancellationToken cancellationToken = default)
        {
            string output = await execute(message, cancellationToken).ConfigureAwait(false);
            await context.YieldOutputAsync(output, cancellationToken).ConfigureAwait(false);
        }
    }
}
