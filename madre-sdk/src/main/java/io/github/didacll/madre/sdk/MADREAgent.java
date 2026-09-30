package io.github.didacll.madre.sdk;

import io.github.didacll.madre.sdk.spira.Integrity;

/**
 * Semantic actor supplied by a Module, distinct from bounded Operations.
 * An Agent may use Skills and Workflows, invoke its own or another Module's
 * Operations, create semantic ReasoningRequests, and explicitly delegate to
 * another Agent. Invoking an Operation does not itself delegate; the acting
 * Agent keeps its semantic continuation.
 */
public interface MADREAgent {
    /** Assurance contributed when this Agent actually participates. */
    Integrity integrity();

    // TODO: Define invocation, reasoning creation and explicit delegation call shapes.
    // Autonomy belongs to the current acting continuation, not permanently to this Agent.
}
