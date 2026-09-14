package fixture;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Independent provider proving ordinary ServiceLoader installation outside MADRE's build graph. */
public final class IndependentTextReasoningProvider implements ReasoningMechanismProvider {
    private static final String PREFIX = "reasoning.independent-text";

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : instances(configuration)) {
            String prefix = PREFIX + "." + instance;
            if (!enabled(configuration, prefix + ".enabled")) continue;
            IndependentTextReasoningCapability capability =
                    new IndependentTextReasoningCapability(
                            new ReasoningCapabilityId(required(configuration, prefix + ".id")),
                            privacy(configuration, prefix + ".privacy"),
                            location(configuration, prefix + ".location"),
                            Duration.ofMillis(positiveLong(configuration,
                                    prefix + ".expected-latency-ms")),
                            availability(configuration, prefix + ".availability"));
            mechanisms.add(new ReasoningMechanism<>(capability,
                    nonnegativeInt(configuration, prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }

    private static List<String> instances(ReasoningProviderConfiguration configuration) {
        String raw = configuration.value(PREFIX + ".instances").orElse(null);
        if (raw == null) return List.of();
        if (raw.isBlank()) throw new IllegalArgumentException(PREFIX + ".instances is blank");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String item : raw.split(",", -1)) {
            String name = item.strip();
            if (name.isEmpty() || !names.add(name)) {
                throw new IllegalArgumentException(PREFIX + ".instances is malformed");
            }
        }
        return List.copyOf(names);
    }

    private static boolean enabled(ReasoningProviderConfiguration configuration, String key) {
        return switch (configuration.value(key).orElse("false").strip()
                .toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }

    private static String required(ReasoningProviderConfiguration configuration, String key) {
        String value = configuration.value(key).orElseThrow(
                () -> new IllegalArgumentException("missing property " + key));
        if (value.isBlank()) throw new IllegalArgumentException("missing property " + key);
        return value.strip();
    }

    private static Privacy privacy(ReasoningProviderConfiguration configuration, String key) {
        return switch (required(configuration, key).toUpperCase(Locale.ROOT)) {
            case "PUBLIC", "P1" -> Privacy.PUBLIC;
            case "UNKNOWN", "P2" -> Privacy.UNKNOWN;
            case "LOCAL", "P3" -> Privacy.LOCAL;
            case "MODULE", "P4" -> Privacy.MODULE;
            case "SECRET", "P5" -> Privacy.SECRET;
            default -> throw new IllegalArgumentException(key + " is not a valid Privacy");
        };
    }

    private static ReasoningLocation location(ReasoningProviderConfiguration configuration,
            String key) {
        try {
            return ReasoningLocation.valueOf(required(configuration, key)
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " is not a valid ReasoningLocation",
                    exception);
        }
    }

    private static ReasoningAvailability availability(ReasoningProviderConfiguration configuration,
            String key) {
        try {
            return ReasoningAvailability.valueOf(configuration.value(key).orElse("AVAILABLE")
                    .strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " is not a valid ReasoningAvailability",
                    exception);
        }
    }

    private static long positiveLong(ReasoningProviderConfiguration configuration, String key) {
        try {
            long value = Long.parseLong(required(configuration, key));
            if (value < 1) throw new IllegalArgumentException(key + " must be positive");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static int nonnegativeInt(ReasoningProviderConfiguration configuration, String key) {
        try {
            int value = Integer.parseInt(required(configuration, key));
            if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
