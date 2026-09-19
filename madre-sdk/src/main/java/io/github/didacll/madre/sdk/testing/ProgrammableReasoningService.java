package io.github.didacll.madre.sdk.testing;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
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

/** Lightweight deterministic reasoning double for Module and Agent semantic tests. */
public final class ProgrammableReasoningService implements ReasoningService {
    private final List<ResponseRule<?, ?>> rules = new ArrayList<>();
    private final Map<WorkId, PendingWork> work = new LinkedHashMap<>();
    private long nextId = 1;

    public synchronized <R, C extends ReasoningComputation<R>> ProgrammableReasoningService respond(
            Class<C> type, Function<? super C, ? extends R> responder) {
        rules.add(new ResponseRule<>(Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(responder, "responder")));
        return this;
    }

    @Override public synchronized <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
            ReasoningRequest<R, C> request) {
        try { return CompletableFuture.completedFuture(request.resultType().cast(evaluate(request.computation()))); }
        catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    @Override public synchronized <R, C extends ReasoningComputation<R>> WorkId submit(
            ReasoningRequest<R, C> request) {
        WorkId id = new WorkId("test-work-" + nextId++);
        work.put(id, new PendingWork(Objects.requireNonNull(request, "request")));
        return id;
    }

    public synchronized boolean complete(WorkId id) {
        PendingWork pending = work.get(id);
        if (pending == null || pending.state != WorkState.QUEUED) return false;
        pending.attempts++;
        pending.result = evaluate(pending.request.computation());
        pending.state = WorkState.SUCCEEDED;
        pending.completedAt = Optional.of(Instant.now());
        return true;
    }

    @Override public synchronized Optional<WorkStatus> inspect(WorkId id) {
        PendingWork pending = work.get(id);
        return pending == null ? Optional.empty() : Optional.of(new WorkStatus(id, pending.state,
                pending.attempts, pending.request.eligibleAt(), Optional.empty(), pending.completedAt));
    }

    @Override public synchronized boolean cancel(WorkId id) {
        PendingWork pending = work.get(id);
        if (pending == null || pending.state != WorkState.QUEUED) return false;
        pending.state = WorkState.CANCELLED;
        pending.completedAt = Optional.of(Instant.now());
        return true;
    }

    @Override public synchronized <R> Optional<R> collect(WorkId id, Class<R> resultType) {
        PendingWork pending = work.get(id);
        if (pending == null || pending.state != WorkState.SUCCEEDED) return Optional.empty();
        return Optional.of(resultType.cast(pending.result));
    }

    @Override public synchronized boolean acknowledge(WorkId id) {
        PendingWork pending = work.get(id);
        return pending != null && pending.state == WorkState.SUCCEEDED && work.remove(id) != null;
    }

    private Object evaluate(ReasoningComputation<?> computation) {
        for (ResponseRule<?, ?> rule : rules) {
            if (rule.type.isInstance(computation)) return rule.apply(computation);
        }
        throw new IllegalStateException("no response configured for " + computation.getClass().getName());
    }

    private record ResponseRule<C, R>(Class<C> type, Function<? super C, ? extends R> responder) {
        private Object apply(Object value) { return Objects.requireNonNull(responder.apply(type.cast(value)), "responder result"); }
    }

    private static final class PendingWork {
        private final ReasoningRequest<?, ?> request;
        private WorkState state = WorkState.QUEUED;
        private int attempts;
        private Object result;
        private Optional<Instant> completedAt = Optional.empty();
        private PendingWork(ReasoningRequest<?, ?> request) { this.request = request; }
    }
}
