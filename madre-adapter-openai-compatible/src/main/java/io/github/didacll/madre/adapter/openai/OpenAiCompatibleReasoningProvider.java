package io.github.didacll.madre.adapter.openai;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Service-provider materializer for configured OpenAI-compatible reasoning mechanisms. */
public final class OpenAiCompatibleReasoningProvider implements ReasoningMechanismProvider {
    private static final String PREFIX = "reasoning.openai-compatible";

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : OpenAiProviderConfiguration.instances(configuration, PREFIX)) {
            String prefix = PREFIX + "." + instance;
            if (!OpenAiProviderConfiguration.enabled(configuration, prefix)) continue;
            OpenAiCompatibleConfiguration adapter = new OpenAiCompatibleConfiguration(
                    OpenAiProviderConfiguration.capabilityId(configuration, prefix + ".id"),
                    URI.create(OpenAiProviderConfiguration.required(configuration,
                            prefix + ".endpoint")),
                    OpenAiProviderConfiguration.required(configuration, prefix + ".model"),
                    OpenAiProviderConfiguration.privacy(configuration, prefix + ".privacy"),
                    OpenAiProviderConfiguration.location(configuration, prefix + ".location"),
                    OpenAiProviderConfiguration.duration(configuration,
                            prefix + ".expected-latency-ms"),
                    OpenAiProviderConfiguration.resources(configuration, prefix));
            mechanisms.add(new ReasoningMechanism<>(
                    new OpenAiCompatibleReasoningCapability(adapter),
                    OpenAiProviderConfiguration.preference(configuration,
                            prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }
}
