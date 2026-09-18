package io.github.didacll.madre.kernel;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Physical inference scheduler. It knows engines, resources and technical work, but no MADRE
 * semantic object or Security Algebra value.
 */
public final class InferenceKernel implements AutoCloseable {
    private final TechnicalWorkStore store;
    private final ResourceCoordinator resources;
    private final CopyOnWriteArrayList<InferenceEngine<?, ?>> engines = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<WorkId, KernelTask<?, ?>> active = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers;
    private final ExecutorService engineExecutions = Executors.newCachedThreadPool();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public InferenceKernel(Path database, int parallelism, Map<ResourceId, Long> resourceCapacity) {
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        store = new TechnicalWorkStore(Objects.requireNonNull(database, "database"));
        resources = new ResourceCoordinator(Objects.requireNonNull(resourceCapacity, "resourceCapacity"));
        workers = new ThreadPoolExecutor(parallelism, parallelism, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>());
    }

    public EngineRegistration register(InferenceEngine<?, ?> engine) {
        requireOpen();
        Objects.requireNonNull(engine, "engine");
        if (engines.stream().anyMatch(candidate -> candidate.id().equals(engine.id()))) {
            throw new KernelException("Inference engine already registered: " + engine.id());
        }
        engines.add(engine);
        return new Registration(engine);
    }

    public List<EngineId> installedEngines() {
        return engines.stream().map(InferenceEngine::id).sorted().toList();
    }

    public <I, O> WorkId submit(
            InferenceWork<I, O> work, I input, InferenceResultReceiver<O> receiver) {
        requireOpen();
        Objects.requireNonNull(work, "work");
        I checkedInput = work.type().requireInput(input);
        Objects.requireNonNull(receiver, "receiver");
        store.insert(work);
        enqueue(new KernelTask<>(work, checkedInput, receiver, sequence.getAndIncrement()));
        return work.id();
    }

    /** Reattaches transient input and runtime result ownership after a Kernel restart. */
    public <I, O> WorkSnapshot reattach(
            InferenceWork<I, O> work, I input, InferenceResultReceiver<O> receiver) {
        requireOpen();
        Objects.requireNonNull(work, "work");
        store.requireSame(work);
        WorkSnapshot current = store.snapshot(work.id());
        if (current.status() == WorkStatus.OUTCOME_UNKNOWN) {
            try {
                if (receiver.alreadyAccepted(work.id())) {
                    store.transition(work.id(), WorkStatus.DELIVERED, null);
                }
            } catch (Exception exception) {
                throw new KernelException("Cannot reconcile uncertain result delivery for " + work.id(), exception);
            }
            return store.snapshot(work.id());
        }
        if (current.status() != WorkStatus.NEEDS_INPUT) return current;
        I checkedInput = work.type().requireInput(input);
        Objects.requireNonNull(receiver, "receiver");
        store.transition(work.id(), WorkStatus.QUEUED, null);
        enqueue(new KernelTask<>(work, checkedInput, receiver, sequence.getAndIncrement()));
        return store.snapshot(work.id());
    }

    public WorkSnapshot snapshot(WorkId id) { return store.snapshot(Objects.requireNonNull(id, "id")); }

    public List<WorkId> recoverableWork() { return store.recoverable(); }

    public WorkSnapshot await(WorkId id, Duration timeout) throws InterruptedException, TimeoutException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("timeout must not be negative");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            WorkSnapshot current = store.snapshot(id);
            if (current.status().terminal() || current.status() == WorkStatus.NEEDS_INPUT) return current;
            if (System.nanoTime() >= deadline) throw new TimeoutException("Timed out awaiting inference work " + id);
            Thread.sleep(10);
        }
    }

    public boolean cancel(WorkId id) {
        Objects.requireNonNull(id, "id");
        WorkSnapshot current = store.snapshot(id);
        if (current.status().terminal()) return false;
        KernelTask<?, ?> task = active.remove(id);
        if (task != null) task.cancel();
        store.transition(id, WorkStatus.CANCELLED,
                new TechnicalFailure(TechnicalFailure.Category.CANCELLED, "Inference work was cancelled", false));
        return true;
    }

    private void enqueue(KernelTask<?, ?> task) {
        KernelTask<?, ?> previous = active.putIfAbsent(task.work.id(), task);
        if (previous != null) throw new KernelException("Inference work is already active: " + task.work.id());
        long delay = Math.max(0, Duration.between(Instant.now(), task.work.requirements().eligibleAt()).toMillis());
        if (delay == 0) workers.execute(task);
        else timer.schedule(() -> workers.execute(task), delay, TimeUnit.MILLISECONDS);
    }

    private <I, O> Optional<Selected<I, O>> select(InferenceWork<I, O> work) {
        List<InferenceEngine<I, O>> candidates = new ArrayList<>();
        for (InferenceEngine<?, ?> candidate : engines) {
            if (candidate.type().equals(work.type())) candidates.add(cast(candidate));
        }
        InferenceRequirements requested = work.requirements();
        candidates.removeIf(engine -> requested.exactEngine().isPresent()
                && !requested.exactEngine().orElseThrow().equals(engine.id()));
        candidates.removeIf(engine -> requested.placement() == Placement.LOCAL_ONLY
                && engine.characteristics().location() != EngineLocation.LOCAL);
        candidates.removeIf(engine -> requested.exactProvider().isPresent()
                && !requested.exactProvider().orElseThrow().equals(engine.characteristics().provider()));
        candidates.removeIf(engine -> requested.exactModel().isPresent()
                && !requested.exactModel().orElseThrow().equals(engine.characteristics().model()));
        candidates.removeIf(engine -> !engine.characteristics().satisfies(requested.capability()));
        candidates.removeIf(engine -> requested.maximumExpectedLatency().isPresent()
                && engine.characteristics().expectedLatency().compareTo(
                        requested.maximumExpectedLatency().orElseThrow()) > 0);
        candidates.removeIf(engine -> engine.availability() != EngineAvailability.AVAILABLE);
        candidates.sort(Comparator
                .comparingInt((InferenceEngine<I, O> engine) -> engine.characteristics().preference()).reversed()
                .thenComparing(engine -> engine.characteristics().expectedLatency())
                .thenComparing(InferenceEngine::id));
        for (InferenceEngine<I, O> engine : candidates) {
            List<ResourceClaim> claims = new ArrayList<>(requested.resources());
            claims.addAll(engine.resourceClaims(work));
            claims = combineClaims(claims);
            Optional<ResourceCoordinator.Lease> lease = resources.tryAcquire(claims);
            if (lease.isPresent()) return Optional.of(new Selected<>(engine, lease.orElseThrow(), List.copyOf(claims)));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <I, O> InferenceEngine<I, O> cast(InferenceEngine<?, ?> engine) {
        return (InferenceEngine<I, O>) engine;
    }

    private void requireOpen() {
        if (closed.get()) throw new KernelException("Inference Kernel is closed");
    }

    private static List<ResourceClaim> combineClaims(List<ResourceClaim> claims) {
        TreeMap<ResourceId, Long> combined = new TreeMap<>();
        for (ResourceClaim claim : claims) combined.merge(claim.resource(), claim.units(), Math::addExact);
        return combined.entrySet().stream()
                .map(entry -> new ResourceClaim(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        timer.shutdownNow();
        workers.shutdownNow();
        engineExecutions.shutdownNow();
        store.close();
    }

    public interface EngineRegistration extends AutoCloseable {
        EngineId engineId();
        @Override void close();
    }

    private final class Registration implements EngineRegistration {
        private final InferenceEngine<?, ?> engine;
        private final AtomicBoolean removed = new AtomicBoolean();
        private Registration(InferenceEngine<?, ?> engine) { this.engine = engine; }
        @Override public EngineId engineId() { return engine.id(); }
        @Override public void close() { if (removed.compareAndSet(false, true)) engines.remove(engine); }
    }

    private final class KernelTask<I, O> implements Runnable, Comparable<KernelTask<?, ?>> {
        private final InferenceWork<I, O> work;
        private final I input;
        private final InferenceResultReceiver<O> receiver;
        private final long order;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private KernelTask(InferenceWork<I, O> work, I input, InferenceResultReceiver<O> receiver, long order) {
            this.work = work; this.input = input; this.receiver = receiver; this.order = order;
        }

        @Override public int compareTo(KernelTask<?, ?> other) {
            int urgency = Integer.compare(other.work.requirements().urgency().priority(),
                    work.requirements().urgency().priority());
            return urgency != 0 ? urgency : Long.compare(order, other.order);
        }

        void cancel() { cancelled.set(true); }

        @Override public void run() {
            if (cancelled.get() || store.snapshot(work.id()).status() == WorkStatus.CANCELLED) return;
            Optional<Instant> deadline = work.requirements().deadline();
            if (deadline.isPresent() && !Instant.now().isBefore(deadline.orElseThrow())) {
                fail(new TechnicalFailure(TechnicalFailure.Category.TIMEOUT, "Inference deadline expired", false));
                return;
            }
            Optional<Selected<I, O>> selected = select(work);
            if (selected.isEmpty()) {
                fail(new TechnicalFailure(TechnicalFailure.Category.NO_ENGINE,
                        "No installed inference engine currently satisfies the technical requirements", false));
                return;
            }
            execute(selected.orElseThrow());
        }

        private void execute(Selected<I, O> selected) {
            long started = System.nanoTime();
            int attempt = store.startAttempt(work.id(), selected.engine(), selected.claims());
            Instant executionDeadline = Instant.now().plus(work.requirements().timeout());
            ResourceCoordinator.Lease lease = selected.lease();
            try {
                Future<O> future = engineExecutions.submit(() -> selected.engine().execute(
                        input, new EngineExecution(work.id(), attempt, executionDeadline)));
                O output;
                try {
                    output = work.type().requireOutput(future.get(work.requirements().timeout().toMillis(), TimeUnit.MILLISECONDS));
                } catch (TimeoutException exception) {
                    future.cancel(true);
                    throw exception;
                }
                store.finishAttempt(work.id(), attempt, "SUCCEEDED", elapsedMillis(started));
                store.transition(work.id(), WorkStatus.DELIVERING, null);
                deliver(output);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                executionFailed(attempt, started, TechnicalFailure.Category.CANCELLED, false);
            } catch (TimeoutException exception) {
                executionFailed(attempt, started, TechnicalFailure.Category.TIMEOUT, true);
            } catch (ExecutionException exception) {
                executionFailed(attempt, started, TechnicalFailure.Category.ENGINE_FAILURE, true);
            } catch (Exception exception) {
                executionFailed(attempt, started, TechnicalFailure.Category.ENGINE_FAILURE, true);
            } finally {
                lease.close();
            }
        }

        private void executionFailed(int attempt, long started, TechnicalFailure.Category category, boolean retryable) {
            store.finishAttempt(work.id(), attempt, category.name(), elapsedMillis(started));
            if (retryable && attempt < work.requirements().retryPolicy().maximumAttempts() && !cancelled.get()) {
                store.transition(work.id(), WorkStatus.RETRY_WAIT,
                        new TechnicalFailure(category, "Inference engine attempt failed", true));
                long delay = work.requirements().retryPolicy().delay().toMillis();
                timer.schedule(() -> workers.execute(this), delay, TimeUnit.MILLISECONDS);
            } else {
                fail(new TechnicalFailure(category, "Inference engine execution failed", false));
            }
        }

        private void deliver(O output) {
            try {
                DeliveryAcknowledgement acknowledgement = receiver.accept(work.id(), output);
                if (acknowledgement != DeliveryAcknowledgement.DURABLY_ACCEPTED) {
                    throw new KernelException("Runtime did not durably accept inference result " + work.id());
                }
                store.transition(work.id(), WorkStatus.DELIVERED, null);
            } catch (Exception deliveryFailure) {
                try {
                    if (receiver.alreadyAccepted(work.id())) {
                        store.transition(work.id(), WorkStatus.DELIVERED, null);
                    } else {
                        store.transition(work.id(), WorkStatus.OUTCOME_UNKNOWN,
                                new TechnicalFailure(TechnicalFailure.Category.DELIVERY_FAILURE,
                                        "Inference result delivery outcome is unknown", false));
                    }
                } catch (Exception reconciliationFailure) {
                    store.transition(work.id(), WorkStatus.OUTCOME_UNKNOWN,
                            new TechnicalFailure(TechnicalFailure.Category.DELIVERY_FAILURE,
                                    "Inference result delivery outcome is unknown", false));
                }
            } finally {
                active.remove(work.id(), this);
            }
        }

        private void fail(TechnicalFailure failure) {
            store.transition(work.id(), failure.category() == TechnicalFailure.Category.CANCELLED
                    ? WorkStatus.CANCELLED : WorkStatus.FAILED, failure);
            active.remove(work.id(), this);
        }
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private record Selected<I, O>(
            InferenceEngine<I, O> engine, ResourceCoordinator.Lease lease, List<ResourceClaim> claims) { }
}
