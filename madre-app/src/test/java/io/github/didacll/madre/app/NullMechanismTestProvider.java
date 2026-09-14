package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.util.Arrays;
import java.util.List;

/** ServiceLoader fixture returning a null mechanism element. */
public final class NullMechanismTestProvider implements ReasoningMechanismProvider {
    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        return Arrays.asList((ReasoningMechanism<?, ?>) null);
    }
}
