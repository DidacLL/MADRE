package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.module.OwnerMessage;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Host-owned presentation mechanics for naturally surfacing CORE Agent-approved follow-up. */
final class OwnerInteractionPresentation implements AutoCloseable {
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(250);

    private final MadreApplication application;
    private final Consumer<String> presenter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean polling = new AtomicBoolean();

    OwnerInteractionPresentation(MadreApplication application, Consumer<String> presenter) {
        this(application, presenter, DEFAULT_POLL_INTERVAL);
    }

    OwnerInteractionPresentation(MadreApplication application, Consumer<String> presenter,
            Duration pollInterval) {
        this.application = Objects.requireNonNull(application, "application");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        Duration interval = Objects.requireNonNull(pollInterval, "pollInterval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "madre-owner-follow-up");
            thread.setDaemon(true);
            return thread;
        });
        if (application.hasOwnerInteraction()) {
            long millis = Math.max(1L, interval.toMillis());
            scheduler.scheduleWithFixedDelay(this::pollSafely, 0L, millis,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void pollSafely() {
        if (!polling.compareAndSet(false, true)) return;
        try {
            application.collectOwnerFollowUps().whenComplete((messages, failure) -> {
                try {
                    if (failure == null) {
                        messages.stream().map(OwnerMessage::text).forEach(presenter);
                    }
                } finally {
                    polling.set(false);
                }
            });
        } catch (RuntimeException failure) {
            polling.set(false);
        }
    }

    @Override public void close() {
        scheduler.shutdownNow();
    }
}
