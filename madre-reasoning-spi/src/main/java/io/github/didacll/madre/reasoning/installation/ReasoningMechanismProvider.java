package io.github.didacll.madre.reasoning.installation;

import java.util.List;

/** Java service-provider entrypoint for one independently installed reasoning adapter artifact. */
public interface ReasoningMechanismProvider extends AutoCloseable {
    /** Materializes zero or more configured mechanism instances. */
    List<ReasoningMechanism<?, ?>> materialize(ReasoningProviderConfiguration configuration);

    /** Releases provider-owned startup/runtime resources, when any exist. */
    @Override default void close() { }
}
