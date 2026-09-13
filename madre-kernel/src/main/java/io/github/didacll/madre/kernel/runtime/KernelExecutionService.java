package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.sdk.execution.ExecutionMode;
import io.github.didacll.madre.sdk.execution.ExecutionService;
import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.PhysicalExecutionException;
import io.github.didacll.madre.sdk.execution.PhysicalRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkRequest;
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

/** Shared immediate/durable dispatcher with SQLite-backed durable lifecycle. */
public final class KernelExecutionService implements ExecutionService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(KernelExecutionService.class);
    private final CapabilityRegistry capabilities;
    private final SQLiteWorkStore store;
    private final Duration retention;
    private final ExecutorService executions = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();

    public KernelExecutionService(CapabilityRegistry capabilities, SQLiteWorkStore store, Duration retention) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities"); this.store = Objects.requireNonNull(store, "store");
        this.retention = Objects.requireNonNull(retention, "retention");
        if (retention.isNegative() || retention.isZero()) throw new IllegalArgumentException("retention must be positive");
        scheduler.scheduleWithFixedDelay(this::scheduleSafely, 0, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> store.cleanup(Instant.now().minus(retention)), 1, 1, TimeUnit.MINUTES);
    }

    @Override public <C, R> CompletableFuture<R> execute(WorkRequest<C, R> request) {
        Objects.requireNonNull(request, "request");
        if (request.mode() != ExecutionMode.IMMEDIATE) return CompletableFuture.failedFuture(new IllegalArgumentException("execute requires IMMEDIATE mode"));
        CapabilityRegistry.Selection<C, R> initial = capabilities.select(request).orElse(null);
        if (initial == null) {
            return CompletableFuture.failedFuture(new PhysicalExecutionException(
                    PhysicalFailureCategory.UNAVAILABLE,
                    "no reachable physical Capability is currently available", null));
        }
        return CompletableFuture.supplyAsync(() -> executeImmediate(request, initial), executions);
    }

    private <C, R> R executeImmediate(WorkRequest<C, R> request,
            CapabilityRegistry.Selection<C, R> initial) {
        CapabilityException last = null;
        CapabilityRegistry.Selection<C, R> selected = initial;
        for (int attempt = 1; attempt <= request.retryPolicy().maximumAttempts(); attempt++) {
            CapabilityRegistry.Selection<C, R> current = selected;
            try (current) {
                return invokeSelected(current, request, attempt, () -> false);
            } catch (CapabilityException exception) {
                last = exception;
                if (exception.category() == PhysicalFailureCategory.UNAVAILABLE
                        || attempt == request.retryPolicy().maximumAttempts()) break;
                try { Thread.sleep(request.retryPolicy().delay().toMillis()); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(new PhysicalExecutionException(
                            PhysicalFailureCategory.CANCELLED,
                            "physical retry interrupted", interrupted));
                }
                selected = capabilities.select(request).orElse(null);
                if (selected == null) {
                    last = new CapabilityException(PhysicalFailureCategory.UNAVAILABLE,
                            "no reachable physical Capability is currently available");
                    break;
                }
            }
        }
        CapabilityException failure = Objects.requireNonNull(last);
        throw new CompletionException(new PhysicalExecutionException(
                failure.category(), failure.getMessage(), failure));
    }

    @Override public <C, R> WorkId submit(WorkRequest<C, R> request) {
        Objects.requireNonNull(request, "request");
        if (request.mode() != ExecutionMode.DURABLE) throw new IllegalArgumentException("submit requires DURABLE mode");
        PhysicalContract<C, R> contract = contractFor(request);
        WorkId id = WorkId.create();
        store.insert(new StoredWork(id, request.originatingModule(), contract.id(), contract.commandCodec().encode(request.command()),
                request.carriedSensitivity(), request.physicalRisk(), request.priority(), request.eligibleAt(), request.timeout(),
                request.retryPolicy().maximumAttempts(), request.retryPolicy().delay(), request.cancellationKey(),
                request.preferences().location(), request.preferences().maximumLatency(), WorkState.QUEUED, 0,
                Optional.empty(), Optional.empty(), Optional.empty()));
        scheduler.execute(this::scheduleSafely);
        return id;
    }

    @Override public Optional<WorkStatus> inspect(WorkId id) { return store.status(Objects.requireNonNull(id, "id")); }
    @Override public boolean cancel(WorkId id) { return store.cancel(Objects.requireNonNull(id, "id")); }
    public int cancel(io.github.didacll.madre.sdk.execution.CancellationKey key) { return store.cancel(Objects.requireNonNull(key, "key")); }

    @Override public <R> Optional<R> collect(WorkId id, Class<R> resultType) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(resultType, "resultType");
        StoredWork work = store.find(id).orElse(null);
        if (work == null || work.state() != WorkState.SUCCEEDED || work.result().isEmpty()) return Optional.empty();
        PhysicalContract<?, ?> raw = capabilities.contract(work.contractId()).orElseThrow(() -> new IllegalStateException("physical contract is not registered: " + work.contractId()));
        if (!raw.resultType().equals(resultType)) throw new IllegalArgumentException("result type does not match durable work contract");
        Object decoded = raw.resultCodec().decode(work.result().orElseThrow());
        return Optional.of(resultType.cast(decoded));
    }

    @Override public boolean acknowledge(WorkId id) { return store.acknowledge(Objects.requireNonNull(id, "id")); }

    private void scheduleSafely() {
        if (closed.get()) return;
        try { store.eligible(Instant.now(), 32).forEach(work -> executions.execute(() -> runDurable(work))); }
        catch (RuntimeException exception) { LOG.warn("Durable scheduling pass failed; the next pass will retry", exception); }
    }

    private void runDurable(StoredWork stored) {
        PhysicalContract<?, ?> raw = capabilities.contract(stored.contractId()).orElse(null);
        if (raw == null || stored.command() == null) return;
        runDecoded(stored, raw);
    }

    private <C, R> void runDecoded(StoredWork stored, PhysicalContract<C, R> contract) {
        C command = contract.commandCodec().decode(stored.command());
        WorkRequest<C, R> request = new WorkRequest<>(stored.module(), command, contract.resultType(), stored.sensitivity(), stored.risk(),
                ExecutionMode.DURABLE, stored.priority(), stored.eligibleAt(), stored.timeout(),
                new PhysicalRetryPolicy(stored.maximumAttempts(), stored.retryDelay()), stored.cancellationKey(),
                new PhysicalPreferences(stored.location(), stored.maximumLatency()));
        CapabilityRegistry.Selection<C, R> selection = capabilities.select(request).orElse(null);
        if (selection == null) return;
        try (selection) {
            Instant now = Instant.now();
            if (!store.beginAttempt(stored.id(), stored.attempts(), selection.capability().manifest().id(), now)) return;
            LOG.debug("Physical work {} attempt {} selected Capability {}", stored.id().value(), stored.attempts() + 1,
                    selection.capability().manifest().id().value());
            try {
                R result = invokeSelected(selection, request, stored.attempts() + 1,
                        () -> store.find(stored.id()).map(value -> value.state() == WorkState.CANCELLED).orElse(true));
                store.succeed(stored.id(), stored.attempts() + 1, contract.resultCodec().encode(result), Instant.now());
            } catch (CapabilityException exception) {
                LOG.info("Physical work {} attempt {} failed as {}", stored.id().value(), stored.attempts() + 1,
                        exception.category());
                store.failAttempt(stored, exception.category(), Instant.now());
            }
        }
    }

    private <C, R> R invokeSelected(CapabilityRegistry.Selection<C, R> selection, WorkRequest<C, R> request,
            int attempt, java.util.function.BooleanSupplier cancelled) throws CapabilityException {
        Instant deadline = Instant.now().plus(request.timeout());
        Future<R> future = executions.submit(() -> selection.capability().execute(request.command(), new ExecutionContext(deadline, cancelled, attempt)));
        try { return future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException exception) { future.cancel(true); throw new CapabilityException(PhysicalFailureCategory.TIMEOUT, "physical execution timed out", exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); future.cancel(true); throw new CapabilityException(PhysicalFailureCategory.CANCELLED, "physical execution interrupted", exception); }
        catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof CapabilityException capabilityException) throw capabilityException;
            throw new CapabilityException(PhysicalFailureCategory.INTERNAL, "physical connector failed", cause);
        }
    }

    @SuppressWarnings("unchecked")
    private <C, R> PhysicalContract<C, R> contractFor(WorkRequest<C, R> request) {
        PhysicalContract<?, ?> contract = capabilities.contractForCommand(request.command().getClass(), request.resultType())
                .orElseThrow(() -> new IllegalStateException("no registered physical contract for request"));
        return (PhysicalContract<C, R>) contract;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scheduler.shutdownNow(); executions.shutdownNow();
        try {
            if (!executions.awaitTermination(5, TimeUnit.SECONDS)) LOG.warn("Physical executions did not stop before Kernel store closure");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); LOG.warn("Interrupted while stopping physical executions", exception);
        }
        store.close();
    }
}
