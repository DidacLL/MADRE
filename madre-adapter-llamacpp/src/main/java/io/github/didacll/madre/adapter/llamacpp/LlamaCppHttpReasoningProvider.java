package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.embedding.EmbeddingSpace;
import io.github.didacll.madre.embedding.TextEmbeddingCodecs;
import io.github.didacll.madre.generation.TextGenerationCodecs;
import io.github.didacll.madre.reasoning.installation.ReasoningConfigurationField;
import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import io.github.didacll.madre.text.TextInferenceCodecs;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Service-provider materializer/configurator for explicit llama.cpp loopback HTTP mechanisms. */
public final class LlamaCppHttpReasoningProvider
        implements ReasoningMechanismProvider, ReasoningProviderConfigurator {
    private static final String PREFIX = "reasoning.llamacpp-http";
    private static final String DEFAULT_COMPUTATION = TextInferenceCodecs.CONTRACT_ID;
    private static final ReasoningProviderDescriptor DESCRIPTOR = new ReasoningProviderDescriptor(
            new ReasoningProviderId("llamacpp-http"),
            "llama.cpp loopback HTTP",
            "Configure a local llama.cpp server reachable through an explicit loopback HTTP endpoint.",
            List.of(
                    ReasoningConfigurationField.text("capability-id", "Capability identity",
                            "Stable identity used by MADRE for this materialized reasoning mechanism.", true, null),
                    ReasoningConfigurationField.text("endpoint", "Endpoint",
                            "Absolute loopback HTTP or HTTPS URI for the llama.cpp server.", true, null),
                    ReasoningConfigurationField.text("model", "Model alias",
                            "Model alias expected by the configured llama.cpp server.", true, null),
                    ReasoningConfigurationField.choice("computation", "Computation contract",
                            "Public reasoning contract implemented by this configured model instance.", true,
                            DEFAULT_COMPUTATION, List.of(TextInferenceCodecs.CONTRACT_ID,
                                    TextGenerationCodecs.CONTRACT_ID,
                                    TextEmbeddingCodecs.CONTRACT_ID)),
                    ReasoningConfigurationField.text("embedding-space-id", "Embedding space identity",
                            "Required only for text-embedding mechanisms; identifies the coordinate space produced by this model/configuration.", false, null),
                    ReasoningConfigurationField.integer("embedding-dimensions", "Embedding dimensions",
                            "Required only for text-embedding mechanisms; exact vector dimensionality produced by this space.", false, null,
                            OptionalLong.of(1), OptionalLong.empty()),
                    ReasoningConfigurationField.choice("privacy", "Privacy",
                            "Explicit receiving Privacy. It is never inferred from loopback transport.", true,
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
            LlamaCppConfiguration adapter = adapterConfiguration(configuration, prefix);
            int preference = LlamaCppProviderConfiguration.preference(
                    configuration, prefix + ".preference");
            switch (computation(configuration, prefix)) {
                case TextInferenceCodecs.CONTRACT_ID -> mechanisms.add(new ReasoningMechanism<>(
                        new LlamaCppReasoningCapability(adapter), preference));
                case TextGenerationCodecs.CONTRACT_ID -> mechanisms.add(new ReasoningMechanism<>(
                        new LlamaCppTextGenerationCapability(adapter), preference));
                case TextEmbeddingCodecs.CONTRACT_ID -> mechanisms.add(new ReasoningMechanism<>(
                        new LlamaCppEmbeddingCapability(adapter,
                                embeddingSpace(configuration, prefix)), preference));
                default -> throw new IllegalArgumentException(
                        prefix + ".computation is not a supported reasoning contract");
            }
        }
        return List.copyOf(mechanisms);
    }

    @Override public List<ReasoningConfiguredInstance> configuredInstances(
            ReasoningProviderConfiguration configuration) {
        return LlamaCppOwnerConfiguration.configuredInstances(configuration, PREFIX,
                "endpoint", "endpoint");
    }

    @Override public ReasoningProviderConfigurationUpdate configure(String instance,
            Map<String, String> values, ReasoningProviderConfiguration configuration) {
        return LlamaCppOwnerConfiguration.configure(instance, values, configuration, PREFIX,
                DESCRIPTOR, "endpoint", "endpoint",
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

    private static LlamaCppConfiguration adapterConfiguration(
            ReasoningProviderConfiguration configuration, String prefix) {
        return new LlamaCppConfiguration(
                LlamaCppProviderConfiguration.capabilityId(configuration, prefix + ".id"),
                URI.create(LlamaCppProviderConfiguration.required(configuration, prefix + ".endpoint")),
                LlamaCppProviderConfiguration.required(configuration, prefix + ".model"),
                LlamaCppProviderConfiguration.privacy(configuration, prefix + ".privacy"),
                LlamaCppProviderConfiguration.duration(configuration, prefix + ".expected-latency-ms"),
                LlamaCppProviderConfiguration.resources(configuration, prefix));
    }

    private static String computation(ReasoningProviderConfiguration configuration, String prefix) {
        return configuration.value(prefix + ".computation").map(String::strip)
                .filter(value -> !value.isEmpty()).orElse(DEFAULT_COMPUTATION);
    }

    private static EmbeddingSpace embeddingSpace(ReasoningProviderConfiguration configuration,
            String prefix) {
        String id = LlamaCppProviderConfiguration.required(configuration,
                prefix + ".embedding-space-id");
        String rawDimensions = LlamaCppProviderConfiguration.required(configuration,
                prefix + ".embedding-dimensions");
        try {
            return new EmbeddingSpace(id, Integer.parseInt(rawDimensions));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    prefix + ".embedding-dimensions must be an integer", exception);
        }
    }

    private static void validateEnabledInstance(ReasoningProviderConfiguration configuration,
            String instance) {
        String prefix = PREFIX + "." + instance.strip();
        adapterConfiguration(configuration, prefix);
        LlamaCppProviderConfiguration.preference(configuration, prefix + ".preference");
        switch (computation(configuration, prefix)) {
            case TextInferenceCodecs.CONTRACT_ID, TextGenerationCodecs.CONTRACT_ID -> { }
            case TextEmbeddingCodecs.CONTRACT_ID -> embeddingSpace(configuration, prefix);
            default -> throw new IllegalArgumentException(
                    prefix + ".computation is not a supported reasoning contract");
        }
    }
}
