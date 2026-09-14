package io.github.didacll.madre.kernel.reasoning;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;

/** Executable Kernel mechanism for one typed reasoning computation contract. */
public interface ReasoningCapability<R, C extends ReasoningComputation<R>> {
    ReasoningCapabilityManifest<R, C> manifest();
    ReasoningAvailability availability();
    R execute(C computation, ReasoningExecutionContext context) throws ReasoningException;
}
