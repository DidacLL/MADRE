package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.reasoning.installation.ReasoningConfigurationField;
import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Service-provider materializer/configurator for llama.cpp AF_UNIX mechanisms. */
public final class LlamaCppUnixSocketReasoningProvider
        implements ReasoningMechanismProvider, ReasoningProviderConfigurator {
    private static final String PREFIX = "reasoning.llamacpp-unix";
    private static final ReasoningProviderDescriptor DESCRIPTOR = new ReasoningProviderDescriptor(
            new ReasoningProviderId("llamacpp-unix"),
            "llama.cpp Unix socket",
            "Configure a local llama.cpp server exposed through an absolute Unix-domain socket path.",
            List.of(
                    ReasoningConfigurationField.text("capability-id", "Capability identity",
                            "Stable identity used by MADRE for this materialized reasoning mechanism.", true, null),
                    ReasoningConfigurationField.text("socket", "Socket path",
                            "Absolute filesystem path of the llama.cpp Unix-domain socket.", true, null),
                    ReasoningConfigurationField.text("model", "Model alias",
                            "Model alias expected by the configured llama.cpp server.", true, null),
                    ReasoningConfigurationField.choice("privacy", "Privacy",
                            "Explicit receiving Privacy. It is never inferred from local transport.", true,
                            null, List.of("PUBLIC", "UNKNOWN", "LOCAL", "MODULE", "SECRET")),
                    ReasoningConfigurationField.integer("expected-latency-ms", "Expected latency (ms)",
                            "Positive expected mechanism latency used for reasoning selection.", true, "30000",
                            OptionalLong.of(1), OptionalLong.empty()),
                    ReasoningConfigurationField.integer("preference", "Preference",
                            "Non-negative installation preference; higher values are preferred after compatibility.",
                            true, "0", OptionalLong.of(0), OptionalLong.empty()),
                    ReasoningConfigurationField.integer("model-slot-units", "Model-slot units",
                            "Optional units claimed from the conventional model-slot resource.", false, null,
                            OptionalLong.of(1), OptionalLong.empty())));

    @Override public ReasoningProviderDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ReasoningProviderConfigurator configurator() { return this; }

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : LlamaCppProviderConfiguration.instances(configuration, PREFIX)) {
            String prefix = PREFIX + "." + instance;
            if (!LlamaCppProviderConfiguration.enabled(configuration, prefix)) continue;
            mechanisms.add(new ReasoningMechanism<>(
                    new LlamaCppUnixSocketReasoningCapability(adapterConfiguration(configuration, prefix)),
                    LlamaCppProviderConfiguration.preference(configuration, prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }

    @Override public List<ReasoningConfiguredInstance> configuredInstances(
            ReasoningProviderConfiguration configuration) {
        return LlamaCppOwnerConfiguration.configuredInstances(configuration, PREFIX,
                "socket", "socket");
    }

    @Override public ReasoningProviderConfigurationUpdate configure(String instance,
            Map<String, String> values, ReasoningProviderConfiguration configuration) {
        return LlamaCppOwnerConfiguration.configure(instance, values, configuration, PREFIX,
                DESCRIPTOR, "socket", "socket",
                candidate -> validateEnabledInstance(candidate, instance));
    }

    @Override public ReasoningProviderConfigurationUpdate setEnabled(String instance, boolean enabled,
            ReasoningProviderConfiguration configuration) {
        return LlamaCppOwnerConfiguration.setEnabled(instance, enabled, configuration, PREFIX,
                candidate -> validateEnabledInstance(candidate, instance));
    }

    @Override public ReasoningProviderConfigurationUpdate remove(String instance,
            ReasoningProviderConfiguration configuration) {
        return LlamaCppOwnerConfiguration.remove(instance, configuration, PREFIX);
    }

    private static LlamaCppUnixSocketConfiguration adapterConfiguration(
            ReasoningProviderConfiguration configuration, String prefix) {
        return new LlamaCppUnixSocketConfiguration(
                LlamaCppProviderConfiguration.capabilityId(configuration, prefix + ".id"),
                Path.of(LlamaCppProviderConfiguration.required(configuration, prefix + ".socket")),
                LlamaCppProviderConfiguration.required(configuration, prefix + ".model"),
                LlamaCppProviderConfiguration.privacy(configuration, prefix + ".privacy"),
                LlamaCppProviderConfiguration.duration(configuration, prefix + ".expected-latency-ms"),
                LlamaCppProviderConfiguration.resources(configuration, prefix));
    }

    private static void validateEnabledInstance(ReasoningProviderConfiguration configuration,
            String instance) {
        String prefix = PREFIX + "." + instance.strip();
        adapterConfiguration(configuration, prefix);
        LlamaCppProviderConfiguration.preference(configuration, prefix + ".preference");
    }
}
