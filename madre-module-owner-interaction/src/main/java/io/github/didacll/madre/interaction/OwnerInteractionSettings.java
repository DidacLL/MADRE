package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reasoning and semantic-state controls chosen by the shipped Module's bounded behavior. */
public record OwnerInteractionSettings(int foregroundMaximumTokens,
        int backgroundMaximumTokens, int conversationHistoryExchanges,
        Duration foregroundTimeout, Duration backgroundTimeout,
        ReasoningRetryPolicy backgroundRetry,
        ReasoningPreferences foregroundPreferences,
        ReasoningPreferences backgroundPreferences) {
    private static final Set<String> INSTALLATION_KEYS = Set.of(
            "foreground-maximum-tokens",
            "background-maximum-tokens",
            "conversation-history-exchanges",
            "foreground-timeout-ms",
            "background-timeout-ms",
            "background-retry-attempts",
            "background-retry-delay-ms",
            "foreground-location",
            "foreground-maximum-latency-ms",
            "background-location",
            "background-maximum-latency-ms");

    public OwnerInteractionSettings {
        if (foregroundMaximumTokens < 1 || backgroundMaximumTokens < 1) {
            throw new IllegalArgumentException("generation limits must be positive");
        }
        if (conversationHistoryExchanges < 1) {
            throw new IllegalArgumentException("conversation history must retain at least one exchange");
        }
        Objects.requireNonNull(foregroundTimeout, "foregroundTimeout");
        Objects.requireNonNull(backgroundTimeout, "backgroundTimeout");
        if (foregroundTimeout.isNegative() || foregroundTimeout.isZero()
                || backgroundTimeout.isNegative() || backgroundTimeout.isZero()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        Objects.requireNonNull(backgroundRetry, "backgroundRetry");
        Objects.requireNonNull(foregroundPreferences, "foregroundPreferences");
        Objects.requireNonNull(backgroundPreferences, "backgroundPreferences");
    }

    public static OwnerInteractionSettings defaults() {
        return new OwnerInteractionSettings(256, 512, 4, Duration.ofSeconds(90),
                Duration.ofMinutes(5), new ReasoningRetryPolicy(3, Duration.ofSeconds(5)),
                ReasoningPreferences.unconstrained(), ReasoningPreferences.unconstrained());
    }

    /** Interprets owner installation settings; no application code knows these Module fields. */
    static OwnerInteractionSettings fromInstallation(
            ModuleProviderConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (!configuration.moduleId().equals(OwnerInteractionModule.ID)) {
            throw new IllegalArgumentException("owner-interaction configuration targets "
                    + configuration.moduleId() + " instead of " + OwnerInteractionModule.ID);
        }
        configuration.keys().stream().filter(key -> !INSTALLATION_KEYS.contains(key)).findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException(
                            "unsupported owner-interaction Module configuration key: " + key);
                });

        OwnerInteractionSettings defaults = defaults();
        int foregroundTokens = positiveInt(configuration, "foreground-maximum-tokens",
                defaults.foregroundMaximumTokens());
        int backgroundTokens = positiveInt(configuration, "background-maximum-tokens",
                defaults.backgroundMaximumTokens());
        int conversationHistory = positiveInt(configuration, "conversation-history-exchanges",
                defaults.conversationHistoryExchanges());
        Duration foregroundTimeout = positiveDuration(configuration, "foreground-timeout-ms",
                defaults.foregroundTimeout());
        Duration backgroundTimeout = positiveDuration(configuration, "background-timeout-ms",
                defaults.backgroundTimeout());
        int retryAttempts = positiveInt(configuration, "background-retry-attempts",
                defaults.backgroundRetry().maximumAttempts());
        Duration retryDelay = nonNegativeDuration(configuration, "background-retry-delay-ms",
                defaults.backgroundRetry().delay());
        ReasoningPreferences foregroundPreferences = preferences(configuration, "foreground",
                defaults.foregroundPreferences());
        ReasoningPreferences backgroundPreferences = preferences(configuration, "background",
                defaults.backgroundPreferences());
        return new OwnerInteractionSettings(foregroundTokens, backgroundTokens,
                conversationHistory, foregroundTimeout, backgroundTimeout,
                new ReasoningRetryPolicy(retryAttempts, retryDelay), foregroundPreferences,
                backgroundPreferences);
    }

    private static ReasoningPreferences preferences(ModuleProviderConfiguration configuration,
            String prefix, ReasoningPreferences defaults) {
        Optional<String> configuredLocation = configuration.value(prefix + "-location");
        Optional<ReasoningLocation> location = configuredLocation.isPresent()
                ? Optional.of(parseLocation(prefix + "-location", configuredLocation.orElseThrow()))
                : defaults.location();
        Optional<String> configuredLatency = configuration.value(prefix + "-maximum-latency-ms");
        Optional<Duration> latency = configuredLatency.isPresent()
                ? Optional.of(Duration.ofMillis(positiveLong(prefix + "-maximum-latency-ms",
                        configuredLatency.orElseThrow())))
                : defaults.maximumLatency();
        return new ReasoningPreferences(location, latency);
    }

    private static ReasoningLocation parseLocation(String key, String value) {
        try {
            return ReasoningLocation.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " must be LOCAL or REMOTE: " + value,
                    exception);
        }
    }

    private static int positiveInt(ModuleProviderConfiguration configuration, String key,
            int defaultValue) {
        return configuration.value(key).map(value -> {
            long parsed = positiveLong(key, value);
            if (parsed > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(key + " exceeds integer range: " + value);
            }
            return (int) parsed;
        }).orElse(defaultValue);
    }

    private static Duration positiveDuration(ModuleProviderConfiguration configuration, String key,
            Duration defaultValue) {
        return configuration.value(key)
                .map(value -> Duration.ofMillis(positiveLong(key, value)))
                .orElse(defaultValue);
    }

    private static Duration nonNegativeDuration(ModuleProviderConfiguration configuration,
            String key, Duration defaultValue) {
        return configuration.value(key)
                .map(value -> Duration.ofMillis(nonNegativeLong(key, value)))
                .orElse(defaultValue);
    }

    private static long positiveLong(String key, String value) {
        long parsed = parseLong(key, value);
        if (parsed < 1) throw new IllegalArgumentException(key + " must be positive: " + value);
        return parsed;
    }

    private static long nonNegativeLong(String key, String value) {
        long parsed = parseLong(key, value);
        if (parsed < 0) {
            throw new IllegalArgumentException(key + " must not be negative: " + value);
        }
        return parsed;
    }

    private static long parseLong(String key, String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + value, exception);
        }
    }
}
