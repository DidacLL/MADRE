package io.github.didacll.madre.kernel;

import java.util.List;

/** Executable physical inference implementation installed into the Kernel. */
public interface InferenceEngine<I, O> {
    EngineId id();
    InferenceType<I, O> type();
    EngineCharacteristics characteristics();
    EngineAvailability availability();

    /** Additional concrete machine resources used by this engine for one work item. */
    default List<ResourceClaim> resourceClaims(InferenceWork<I, O> work) { return List.of(); }

    O execute(I input, EngineExecution execution) throws Exception;
}
