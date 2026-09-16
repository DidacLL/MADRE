package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import java.util.List;

/** ServiceLoader fixture intentionally violating the provider materialization contract. */
public final class NullMaterializationTestProvider implements ReasoningMechanismProvider {
    @Override public ReasoningProviderDescriptor descriptor() {
        return TestReasoningProviderSupport.descriptor("test-null-materialization");
    }

    @Override public ReasoningProviderConfigurator configurator() {
        return TestReasoningProviderSupport.EMPTY_CONFIGURATOR;
    }

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        return null;
    }
}
