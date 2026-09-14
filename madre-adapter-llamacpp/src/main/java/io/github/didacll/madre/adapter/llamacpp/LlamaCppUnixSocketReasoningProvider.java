package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Service-provider materializer for configured llama.cpp AF_UNIX reasoning mechanisms. */
public final class LlamaCppUnixSocketReasoningProvider implements ReasoningMechanismProvider {
    private static final String PREFIX = "reasoning.llamacpp-unix";

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : LlamaCppProviderConfiguration.instances(configuration, PREFIX)) {
            String prefix = PREFIX + "." + instance;
            if (!LlamaCppProviderConfiguration.enabled(configuration, prefix)) continue;
            LlamaCppUnixSocketConfiguration adapter = new LlamaCppUnixSocketConfiguration(
                    LlamaCppProviderConfiguration.capabilityId(configuration, prefix + ".id"),
                    Path.of(LlamaCppProviderConfiguration.required(configuration,
                            prefix + ".socket")),
                    LlamaCppProviderConfiguration.required(configuration, prefix + ".model"),
                    LlamaCppProviderConfiguration.privacy(configuration, prefix + ".privacy"),
                    LlamaCppProviderConfiguration.duration(configuration,
                            prefix + ".expected-latency-ms"),
                    LlamaCppProviderConfiguration.resources(configuration, prefix));
            mechanisms.add(new ReasoningMechanism<>(
                    new LlamaCppUnixSocketReasoningCapability(adapter),
                    LlamaCppProviderConfiguration.preference(configuration,
                            prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }
}
