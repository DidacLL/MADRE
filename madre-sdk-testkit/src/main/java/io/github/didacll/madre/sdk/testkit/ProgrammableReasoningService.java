package io.github.didacll.madre.sdk.testkit;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Deterministic public {@link ReasoningService} test double for Module semantic tests.
 *
 * <p>This class deliberately models only the public service lifecycle. It does not simulate
 * Kernel scheduling, mechanism selection, Security Algebra receiver enforcement, retry timing,
 * resource coordination or SQLite durability.</p>
 */
public final class ProgrammableReasoningService implements ReasoningService {
    private final List<ResponseRule<?, ?>> rules = new ArrayList<>();
    private final Map<WorkId, MutableWork> work = new LinkedHashMap<>();
    private long nextWorkId = 1L;

    /** Registers deterministic behavior for one nominal computation implementation. */
    public synchronized <R, C extends ReasoningComputation<R>> ProgrammableReasoningService respond(
            Class<C> computationType, Function<? super C, ? extends R> responder) {
        rules.add(new ResponseRule<>(Objects.requireNonNull(computationType, "computationType"),
                Objects.requireNonNull(responder, "responder")));
        return this;
    }

    @Override
    public synchronized <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
            ReasoningRequest<R, C> request) {
        ReasoningRequest<R, C> exactRequest = Objects.requireNonNull(request, "request");
        try {
            return CompletableFuture.completedFuture(
                    exactRequest.resultType().cast(evaluate(exactRequest.computation())));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public synchronized <R, C extends ReasoningComputation<R>> WorkId submit(
            ReasoningRequest<R, C> request) {
        ReasoningRequest<R, C> exactRequest = Objects.requireNonNull(request, "request");
        WorkId id = new WorkId("test-work-" + nextWorkId++);
        work.put(id, new MutableWork(exactRequest));
        return id;
    }

    /** Moves queued deterministic work to RUNNING without executing the responder. */
    public synchronized boolean start(WorkId id) {
        MutableWork entry = work.get(Objects.requireNonNull(id, "id"));
        if (entry == null || entry.state != WorkState.QUEUED) return false;
        entry.state = WorkState.RUNNING;
        entry.attempts++;
        return true;
    }

    /** Executes the configured responder and marks queued/running work SUCCEEDED. */
    public synchronized boolean complete(WorkId id) {
        MutableWork entry = work.get(Objects.requireNonNull(id, "id"));
        if (entry == null || isTerminal(entry.state)) return false;
        if (entry.state == WorkState.QUEUED) start(id);
        Object result = evaluate(entry.request.computation());
        entry.result = Objects.requireNonNull(result, "reasoning responder returned null");
        entry.lastFailureCategory = Optional.empty();
        entry.state = WorkState.SUCCEEDED;
        entry.completedAt = Optional.of(Instant.now());
        return true;
    }

    /** Marks queued/running work FAILED with one public reasoning failure category. */
    public synchronized boolean fail(WorkId id, ReasoningFailureCategory category) {
        MutableWork entry = work.get(Objects.requireNonNull(id, "id"));
        if (entry == null || isTerminal(entry.state)) return false;
        if (entry.state == WorkState.QUEUED) entry.attempts++;
        entry.state = WorkState.FAILED;
        entry.lastFailureCategory = Optional.of(Objects.requireNonNull(category, "category"));
        entry.completedAt = Optional.of(Instant.now());
        return true;
    }

    @Override
    public synchronized Optional<WorkStatus> inspect(WorkId id) {
        MutableWork entry = work.get(Objects.requireNonNull(id, "id"));
        if (entry == null) return Optional.empty();
        return Optional.of(new WorkStatus(id, entry.state, entry.attempts,
                entry.request.eligibleAt(), entry.lastFailureCategory, entry.completedAt));
    }

    @Override
    public synchronized boolean cancel(WorkId id) {
        MutableWork entry = work.get(Objects.requireNonNull(id, "id"));
        if (entry == null || isTerminal(entry.state)) return false;
        entry.state = WorkState.CANCELLED;
        entry.lastFailureCategory = Optional.of(ReasoningFailureCategory.CANCELLED);
        entry.completedAt = Optional.of(Instant.now());
        return true;
    }

    @Override
    public synchronized <R> Optional<R> collect(WorkId id, Class<R> resultType) {
        MutableWork entry = work.get(Objects.requireNonNull(id, "id"));
        Class<R> exactResultType = Objects.requireNonNull(resultType, "resultType");
        if (entry == null || entry.state != WorkState.SUCCEEDED) return Optional.empty();
        return Optional.of(exactResultType.cast(entry.result));
    }

    @Override
    public synchronized boolean acknowledge(WorkId id) {
        WorkId exactId = Objects.requireNonNull(id, "id");
        MutableWork entry = work.get(exactId);
        if (entry == null || !isTerminal(entry.state)) return false;
        work.remove(exactId);
        return true;
    }

    private Object evaluate(ReasoningComputation<?> computation) {
        ReasoningComputation<?> exactComputation = Objects.requireNonNull(computation, "computation");
        for (ResponseRule<?, ?> rule : rules) {
            if (rule.supports(exactComputation)) {
                return rule.respond(exactComputation);
            }
        }
        throw new IllegalStateException(
                "no deterministic reasoning responder registered for "
                        + exactComputation.getClass().getName());
    }

    private static boolean isTerminal(WorkState state) {
        return state == WorkState.SUCCEEDED || state == WorkState.FAILED
                || state == WorkState.CANCELLED;
    }

    private record ResponseRule<R, C extends ReasoningComputation<R>>(
            Class<C> computationType, Function<? super C, ? extends R> responder) {
        boolean supports(ReasoningComputation<?> computation) {
            return computationType.isInstance(computation);
        }

        Object respond(ReasoningComputation<?> computation) {
            return Objects.requireNonNull(responder.apply(computationType.cast(computation)),
                    "reasoning responder returned null");
        }
    }

    private static final class MutableWork {
        private final ReasoningRequest<?, ?> request;
        private WorkState state = WorkState.QUEUED;
        private int attempts;
        private Optional<ReasoningFailureCategory> lastFailureCategory = Optional.empty();
        private Optional<Instant> completedAt = Optional.empty();
        private Object result;

        private MutableWork(ReasoningRequest<?, ?> request) {
            this.request = Objects.requireNonNull(request, "request");
        }
    }
}
