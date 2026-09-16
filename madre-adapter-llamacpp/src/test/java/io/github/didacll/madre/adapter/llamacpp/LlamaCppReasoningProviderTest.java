package io.github.didacll.madre.adapter.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LlamaCppReasoningProviderTest {
    @Test void malformedEnabledValueFailsClearly() {
        ReasoningProviderConfiguration configuration = new ReasoningProviderConfiguration(Map.of(
                "reasoning.llamacpp-http.instances", "primary",
                "reasoning.llamacpp-http.primary.enabled", "perhaps"));
        assertThrows(IllegalArgumentException.class,
                () -> new LlamaCppHttpReasoningProvider().materialize(configuration));
    }

    @Test void disabledInstanceNeedsNoProviderSpecificFields() {
        ReasoningProviderConfiguration configuration = new ReasoningProviderConfiguration(Map.of(
                "reasoning.llamacpp-http.instances", "primary",
                "reasoning.llamacpp-http.primary.enabled", "false"));
        assertEquals(0, new LlamaCppHttpReasoningProvider().materialize(configuration).size());
    }

    @Test void oneProviderMaterializesMultipleExplicitInstances() {
        String socket = Path.of("build", "one.sock").toAbsolutePath().toString();
        String socketTwo = Path.of("build", "two.sock").toAbsolutePath().toString();
        ReasoningProviderConfiguration configuration = new ReasoningProviderConfiguration(Map.ofEntries(
                Map.entry("reasoning.llamacpp-unix.instances", "one,two"),
                Map.entry("reasoning.llamacpp-unix.one.enabled", "true"),
                Map.entry("reasoning.llamacpp-unix.one.id", "one"),
                Map.entry("reasoning.llamacpp-unix.one.socket", socket),
                Map.entry("reasoning.llamacpp-unix.one.model", "model-one"),
                Map.entry("reasoning.llamacpp-unix.one.privacy", "SECRET"),
                Map.entry("reasoning.llamacpp-unix.one.expected-latency-ms", "10"),
                Map.entry("reasoning.llamacpp-unix.one.preference", "2"),
                Map.entry("reasoning.llamacpp-unix.two.enabled", "true"),
                Map.entry("reasoning.llamacpp-unix.two.id", "two"),
                Map.entry("reasoning.llamacpp-unix.two.socket", socketTwo),
                Map.entry("reasoning.llamacpp-unix.two.model", "model-two"),
                Map.entry("reasoning.llamacpp-unix.two.privacy", "P5"),
                Map.entry("reasoning.llamacpp-unix.two.expected-latency-ms", "20"),
                Map.entry("reasoning.llamacpp-unix.two.preference", "1")));
        assertEquals(2, new LlamaCppUnixSocketReasoningProvider()
                .materialize(configuration).size());
    }
}
