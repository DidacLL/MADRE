package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Service-provider materializer for explicit llama.cpp loopback-HTTP compatibility mechanisms. */
public final class LlamaCppHttpReasoningProvider implements ReasoningMechanismProvider {
    private static final String PREFIX = "reasoning.llamacpp-http";

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : LlamaCppProviderConfiguration.instances(configuration, PREFIX)) {
            String prefix = PREFIX + "." + instance;
            if (!LlamaCppProviderConfiguration.enabled(configuration, prefix)) continue;
            LlamaCppConfiguration adapter = new LlamaCppConfiguration(
                    LlamaCppProviderConfiguration.capabilityId(configuration, prefix + ".id"),
                    URI.create(LlamaCppProviderConfiguration.required(configuration,
                            prefix + ".endpoint")),
                    LlamaCppProviderConfiguration.required(configuration, prefix + ".model"),
                    LlamaCppProviderConfiguration.privacy(configuration, prefix + ".privacy"),
                    LlamaCppProviderConfiguration.duration(configuration,
                            prefix + ".expected-latency-ms"),
                    LlamaCppProviderConfiguration.resources(configuration, prefix));
            mechanisms.add(new ReasoningMechanism<>(new LlamaCppReasoningCapability(adapter),
                    LlamaCppProviderConfiguration.preference(configuration,
                            prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }
}
