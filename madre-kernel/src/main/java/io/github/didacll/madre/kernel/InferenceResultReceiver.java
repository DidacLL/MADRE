package io.github.didacll.madre.kernel;

/** Runtime boundary for result handoff. The Kernel never calls Module or Agent code. */
public interface InferenceResultReceiver<O> {
    DeliveryAcknowledgement accept(WorkId workId, O result) throws Exception;

    /** Used after an uncertain delivery to avoid silently executing the inference twice. */
    boolean alreadyAccepted(WorkId workId) throws Exception;
}
