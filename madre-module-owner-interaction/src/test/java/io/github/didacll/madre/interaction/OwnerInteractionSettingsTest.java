package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OwnerInteractionSettingsTest {
    @Test void omittedInstallationConfigurationPreservesCurrentDefaults() {
        OwnerInteractionSettings settings = OwnerInteractionSettings.fromInstallation(
                new ModuleProviderConfiguration(OwnerInteractionModule.ID, Map.of()));

        assertEquals(OwnerInteractionSettings.defaults(), settings);
    }

    @Test void parsesAllModuleOwnedReasoningAndStateControls() {
        OwnerInteractionSettings settings = OwnerInteractionSettings.fromInstallation(
                new ModuleProviderConfiguration(OwnerInteractionModule.ID, Map.of(
                        "foreground-maximum-tokens", "17",
                        "background-maximum-tokens", "29",
                        "conversation-history-exchanges", "6",
                        "foreground-timeout-ms", "1100",
                        "background-timeout-ms", "2200",
                        "background-retry-attempts", "4",
                        "background-retry-delay-ms", "300",
                        "foreground-location", "LOCAL",
                        "foreground-maximum-latency-ms", "400",
                        "background-location", "REMOTE",
                        "background-maximum-latency-ms", "500")));

        assertEquals(17, settings.foregroundMaximumTokens());
        assertEquals(29, settings.backgroundMaximumTokens());
        assertEquals(6, settings.conversationHistoryExchanges());
        assertEquals(Duration.ofMillis(1100), settings.foregroundTimeout());
        assertEquals(Duration.ofMillis(2200), settings.backgroundTimeout());
        assertEquals(4, settings.backgroundRetry().maximumAttempts());
        assertEquals(Duration.ofMillis(300), settings.backgroundRetry().delay());
        assertEquals(ReasoningLocation.LOCAL,
                settings.foregroundPreferences().location().orElseThrow());
        assertEquals(Duration.ofMillis(400),
                settings.foregroundPreferences().maximumLatency().orElseThrow());
        assertEquals(ReasoningLocation.REMOTE,
                settings.backgroundPreferences().location().orElseThrow());
        assertEquals(Duration.ofMillis(500),
                settings.backgroundPreferences().maximumLatency().orElseThrow());
    }

    @Test void malformedExplicitConfigurationFailsInsteadOfFallingBack() {
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> OwnerInteractionSettings.fromInstallation(
                        new ModuleProviderConfiguration(OwnerInteractionModule.ID,
                                Map.of("foreground-maximum-tokens", "zero"))));
        assertTrue(malformed.getMessage().contains("foreground-maximum-tokens"));

        IllegalArgumentException history = assertThrows(IllegalArgumentException.class,
                () -> OwnerInteractionSettings.fromInstallation(
                        new ModuleProviderConfiguration(OwnerInteractionModule.ID,
                                Map.of("conversation-history-exchanges", "0"))));
        assertTrue(history.getMessage().contains("conversation-history-exchanges"));

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> OwnerInteractionSettings.fromInstallation(
                        new ModuleProviderConfiguration(OwnerInteractionModule.ID,
                                Map.of("unknown-setting", "value"))));
        assertTrue(unknown.getMessage().contains("unsupported owner-interaction"));
    }
}
