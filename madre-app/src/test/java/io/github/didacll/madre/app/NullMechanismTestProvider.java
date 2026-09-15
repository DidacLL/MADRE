package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import java.util.Arrays;
import java.util.List;

/** ServiceLoader fixture returning a null mechanism element. */
public final class NullMechanismTestProvider implements ReasoningMechanismProvider {
    @Override public ReasoningProviderDescriptor descriptor() {
        return TestReasoningProviderSupport.descriptor("test-null-mechanism");
    }

    @Override public ReasoningProviderConfigurator configurator() {
        return TestReasoningProviderSupport.EMPTY_CONFIGURATOR;
    }

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        return Arrays.asList((ReasoningMechanism<?, ?>) null);
    }
}
