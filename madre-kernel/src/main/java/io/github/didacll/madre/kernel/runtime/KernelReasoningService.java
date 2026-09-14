package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningException;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.CancellationKey;
import io.github.didacll.madre.sdk.execution.ExecutionMode;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningExecutionException;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Immediate and SQLite-backed durable runtime for reasoning computations only. */
public final class KernelReasoningService implements ReasoningService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(KernelReasoningService.class);
    private final ReasoningCapabilityRegistry capabilities;
    private final SQLiteReasoningWorkStore store;
    private final Duration retention;
    private final ExecutorService executions = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();

    public KernelReasoningService(ReasoningCapabilityRegistry capabilities,
            SQLiteReasoningWorkStore store, Duration retention) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.store = Objects.requireNonNull(store, "store");
        this.retention = Objects.requireNonNull(retention, "retention");
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("retention must be positive");
        }
        scheduler.scheduleWithFixedDelay(this::scheduleSafely, 0, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(
                () -> store.cleanup(Instant.now().minus(retention)), 1, 1, TimeUnit.MINUTES);
    }

    @Override public <R, C extends ReasoningComputation<R>> CompletableFuture<R> execute(
            ReasoningRequest<R, C> request) {
        Objects.requireNonNull(request, "request");
        if (request.mode() != ExecutionMode.IMMEDIATE) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("execute requires IMMEDIATE mode"));
        }
        ReasoningCapabilityRegistry.Selection<R, C> initial =
                capabilities.select(request).orElse(null);
        if (initial == null) {
            return CompletableFuture.failedFuture(new ReasoningExecutionException(
                    ReasoningFailureCategory.UNAVAILABLE,
                    "no reachable ReasoningCapability is currently available", null));
        }
        return CompletableFuture.supplyAsync(() -> executeImmediate(request, initial), executions);
    }

    private <R, C extends ReasoningComputation<R>> R executeImmediate(
            ReasoningRequest<R, C> request,
            ReasoningCapabilityRegistry.Selection<R, C> initial) {
        ReasoningException last = null;
        ReasoningCapabilityRegistry.Selection<R, C> selected = initial;
        for (int attempt = 1; attempt <= request.retryPolicy().maximumAttempts(); attempt++) {
            ReasoningCapabilityRegistry.Selection<R, C> current = selected;
            try (current) {
                return invokeSelected(current, request.computation(), request.timeout(), attempt,
                        () -> false);
            } catch (ReasoningException exception) {
                last = exception;
                if (attempt == request.retryPolicy().maximumAttempts()) break;
                try {
                    Thread.sleep(request.retryPolicy().delay().toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(new ReasoningExecutionException(
                            ReasoningFailureCategory.CANCELLED,
                            "reasoning retry interrupted", interrupted));
                }
                selected = capabilities.select(request).orElse(null);
                if (selected == null) {
                    last = new ReasoningException(ReasoningFailureCategory.UNAVAILABLE,
                            "no reachable ReasoningCapability is currently available");
                    break;
                }
            }
        }
        ReasoningException failure = Objects.requireNonNull(last);
        throw new CompletionException(new ReasoningExecutionException(
                failure.category(), failure.getMessage(), failure));
    }

    @Override public <R, C extends ReasoningComputation<R>> WorkId submit(
            ReasoningRequest<R, C> request) {
        Objects.requireNonNull(request, "request");
        if (request.mode() != ExecutionMode.DURABLE) {
            throw new IllegalArgumentException("submit requires DURABLE mode");
        }
        ReasoningContract<R, C> contract = contractFor(request);
        WorkId id = WorkId.create();
        store.insert(new StoredReasoningWork(id, request.originatingModule(), contract.id(),
                contract.computationCodec().encode(request.computation()),
                request.carriedSensitivity(), request.priority(), request.eligibleAt(),
                request.timeout(), request.retryPolicy().maximumAttempts(),
                request.retryPolicy().delay(), request.cancellationKey(),
                request.preferences().location(), request.preferences().maximumLatency(),
                WorkState.QUEUED, 0, Optional.empty(), Optional.empty(), Optional.empty()));
        scheduler.execute(this::scheduleSafely);
        return id;
    }

    @Override public Optional<WorkStatus> inspect(WorkId id) {
        return store.status(Objects.requireNonNull(id, "id"));
    }

    @Override public boolean cancel(WorkId id) {
        return store.cancel(Objects.requireNonNull(id, "id"));
    }

    public int cancel(CancellationKey key) {
        return store.cancel(Objects.requireNonNull(key, "key"));
    }

    @Override public <R> Optional<R> collect(WorkId id, Class<R> resultType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resultType, "resultType");
        StoredReasoningWork work = store.find(id).orElse(null);
        if (work == null || work.state() != WorkState.SUCCEEDED || work.result().isEmpty()) {
            return Optional.empty();
        }
        ReasoningContract<?, ?> raw = capabilities.contract(work.contractId()).orElseThrow(
                () -> new IllegalStateException(
                        "reasoning contract is not registered: " + work.contractId()));
        if (!raw.resultType().equals(resultType)) {
            throw new IllegalArgumentException(
                    "result type does not match durable reasoning contract");
        }
        Object decoded = raw.resultCodec().decode(work.result().orElseThrow());
        return Optional.of(resultType.cast(decoded));
    }

    @Override public boolean acknowledge(WorkId id) {
        return store.acknowledge(Objects.requireNonNull(id, "id"));
    }

    private void scheduleSafely() {
        if (closed.get()) return;
        try {
            store.eligible(Instant.now(), 32)
                    .forEach(work -> executions.execute(() -> runDurable(work)));
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                LOG.warn("Durable reasoning scheduling pass failed; the next pass will retry",
                        exception);
            }
        }
    }

    private void runDurable(StoredReasoningWork stored) {
        ReasoningContract<?, ?> raw = capabilities.contract(stored.contractId()).orElse(null);
        if (raw == null || stored.computation() == null) return;
        runDecoded(stored, raw);
    }

    private <R, C extends ReasoningComputation<R>> void runDecoded(
            StoredReasoningWork stored, ReasoningContract<R, C> contract) {
        C computation = contract.computationCodec().decode(stored.computation());
        ReasoningCapabilityRegistry.Selection<R, C> selection = capabilities.select(computation,
                contract.resultType(), stored.sensitivity(),
                new ReasoningPreferences(stored.location(), stored.maximumLatency())).orElse(null);
        if (selection == null) return;
        try (selection) {
            Instant now = Instant.now();
            if (!store.beginAttempt(stored.id(), stored.attempts(),
                    selection.capability().manifest().id(), now)) return;
            LOG.debug("Reasoning work {} attempt {} selected ReasoningCapability {}",
                    stored.id().value(), stored.attempts() + 1,
                    selection.capability().manifest().id().value());
            try {
                R result = invokeSelected(selection, computation, stored.timeout(),
                        stored.attempts() + 1,
                        () -> store.find(stored.id())
                                .map(value -> value.state() == WorkState.CANCELLED)
                                .orElse(true));
                store.succeed(stored.id(), stored.attempts() + 1,
                        contract.resultCodec().encode(result), Instant.now());
            } catch (ReasoningException exception) {
                LOG.info("Reasoning work {} attempt {} failed as {}", stored.id().value(),
                        stored.attempts() + 1, exception.category());
                store.failAttempt(stored, exception.category(), Instant.now());
            }
        }
    }

    private <R, C extends ReasoningComputation<R>> R invokeSelected(
            ReasoningCapabilityRegistry.Selection<R, C> selection, C computation,
            Duration timeout, int attempt, java.util.function.BooleanSupplier cancelled)
            throws ReasoningException {
        Instant deadline = Instant.now().plus(timeout);
        Future<R> future = executions.submit(() -> selection.capability().execute(computation,
                new ReasoningExecutionContext(deadline, cancelled, attempt)));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "reasoning execution timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ReasoningException(ReasoningFailureCategory.CANCELLED,
                    "reasoning execution interrupted", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReasoningException reasoningException) {
                throw reasoningException;
            }
            throw new ReasoningException(ReasoningFailureCategory.INTERNAL,
                    "reasoning mechanism failed", cause);
        }
    }

    @SuppressWarnings("unchecked")
    private <R, C extends ReasoningComputation<R>> ReasoningContract<R, C> contractFor(
            ReasoningRequest<R, C> request) {
        ReasoningContract<?, ?> contract = capabilities.contractForComputation(
                request.computation().getClass(), request.resultType())
                .orElseThrow(() -> new IllegalStateException(
                        "no registered reasoning contract for request"));
        return (ReasoningContract<R, C>) contract;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scheduler.shutdownNow();
        executions.shutdownNow();
        try {
            if (!executions.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Reasoning executions did not stop before Kernel store closure");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while stopping reasoning executions", exception);
        }
        store.close();
    }
}
