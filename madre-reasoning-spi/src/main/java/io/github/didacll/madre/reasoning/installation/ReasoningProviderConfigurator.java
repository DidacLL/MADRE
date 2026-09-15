package io.github.didacll.madre.reasoning.installation;

import java.util.List;
import java.util.Map;

/** Provider-owned configuration behavior for repeatable named reasoning instances. */
public interface ReasoningProviderConfigurator {
    /** Returns configured instances without materializing their reasoning mechanisms. */
    List<ReasoningConfiguredInstance> configuredInstances(ReasoningProviderConfiguration configuration);

    /** Creates or updates and enables one named instance after provider-owned validation. */
    ReasoningProviderConfigurationUpdate configure(String instance, Map<String, String> values,
            ReasoningProviderConfiguration configuration);

    /** Enables or disables an existing instance without deleting its settings. */
    ReasoningProviderConfigurationUpdate setEnabled(String instance, boolean enabled,
            ReasoningProviderConfiguration configuration);

    /** Removes only configuration owned by the selected provider instance. */
    ReasoningProviderConfigurationUpdate remove(String instance,
            ReasoningProviderConfiguration configuration);
}
