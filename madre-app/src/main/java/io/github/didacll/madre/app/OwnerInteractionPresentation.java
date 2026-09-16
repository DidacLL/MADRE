package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.material.Material;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Host-owned presentation mechanics for naturally surfacing Module-approved durable follow-up. */
final class OwnerInteractionPresentation implements AutoCloseable {
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(250);

    private final MadreApplication application;
    private final LocalInteractionBinding binding;
    private final Consumer<String> presenter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean polling = new AtomicBoolean();

    OwnerInteractionPresentation(MadreApplication application, LocalInteractionBinding binding,
            Consumer<String> presenter) {
        this(application, binding, presenter, DEFAULT_POLL_INTERVAL);
    }

    OwnerInteractionPresentation(MadreApplication application, LocalInteractionBinding binding,
            Consumer<String> presenter, Duration pollInterval) {
        this.application = Objects.requireNonNull(application, "application");
        this.binding = Objects.requireNonNull(binding, "binding");
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
        if (binding.updates().isPresent()) {
            long millis = Math.max(1L, interval.toMillis());
            scheduler.scheduleWithFixedDelay(this::pollSafely, 0L, millis,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void pollSafely() {
        if (!polling.compareAndSet(false, true)) return;
        LocalInteractionBinding.UpdatesBinding updates = binding.updates().orElse(null);
        if (updates == null) {
            polling.set(false);
            return;
        }
        try {
            application.invokeInteractionText(binding.moduleId(), updates.operation(),
                    updates.materialType(), updates.sensitivity(), updates.payload())
                    .whenComplete((material, failure) -> {
                        try {
                            if (failure == null) {
                                visiblePayload(material).ifPresent(presenter);
                            }
                        } finally {
                            polling.set(false);
                        }
                    });
        } catch (RuntimeException failure) {
            polling.set(false);
        }
    }

    /** The Module decides usefulness; the surface only removes the current collection protocol. */
    static Optional<String> visiblePayload(Material<?> material) {
        if (material == null || !(material.payload() instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        String visible = text.lines().map(line -> {
            int separator = line.indexOf('\t');
            return separator >= 0 ? line.substring(separator + 1).strip() : line.strip();
        }).filter(value -> !value.isBlank())
                .filter(value -> !value.equalsIgnoreCase("NO_FOLLOW_UP"))
                .filter(value -> !value.startsWith("FAILED") && !value.startsWith("CANCELLED"))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        return visible.isBlank() ? Optional.empty() : Optional.of(visible);
    }

    @Override public void close() {
        scheduler.shutdownNow();
    }
}
