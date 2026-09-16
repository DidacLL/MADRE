package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import java.util.List;
import java.util.Map;

/** Minimal configuration surface used by invalid ServiceLoader provider test fixtures. */
final class TestReasoningProviderSupport {
    private TestReasoningProviderSupport() { }

    static ReasoningProviderDescriptor descriptor(String id) {
        return new ReasoningProviderDescriptor(new ReasoningProviderId(id), id, "test provider",
                List.of());
    }

    static final ReasoningProviderConfigurator EMPTY_CONFIGURATOR = new ReasoningProviderConfigurator() {
        @Override public List<ReasoningConfiguredInstance> configuredInstances(
                ReasoningProviderConfiguration configuration) {
            return List.of();
        }

        @Override public ReasoningProviderConfigurationUpdate configure(String instance,
                Map<String, String> values, ReasoningProviderConfiguration configuration) {
            return ReasoningProviderConfigurationUpdate.empty();
        }

        @Override public ReasoningProviderConfigurationUpdate setEnabled(String instance,
                boolean enabled, ReasoningProviderConfiguration configuration) {
            return ReasoningProviderConfigurationUpdate.empty();
        }

        @Override public ReasoningProviderConfigurationUpdate remove(String instance,
                ReasoningProviderConfiguration configuration) {
            return ReasoningProviderConfigurationUpdate.empty();
        }
    };
}
