package io.github.didacll.madre.kernel.reasoning;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;

/** Executable Kernel mechanism for one typed reasoning computation contract. */
public interface ReasoningCapability<R, C extends ReasoningComputation<R>> {
    ReasoningCapabilityManifest<R, C> manifest();
    ReasoningAvailability availability();

    /**
     * Returns whether this concrete mechanism can execute this exact computation value.
     *
     * <p>Kernel calls this only after the nominal computation/result contract matches. The
     * predicate is for mechanism-owned value constraints such as an embedding space; it must
     * not perform execution or change the public computation contract.</p>
     */
    default boolean supports(C computation) { return true; }

    R execute(C computation, ReasoningExecutionContext context) throws ReasoningException;
}
