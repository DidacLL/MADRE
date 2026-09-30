package io.github.didacll.madre.sdk;

import io.github.didacll.madre.sdk.spira.Integrity;

/** Semantic actor supplied by a Module, distinct from the Operations it invokes. */
public interface MADREAgent {
    /** Assurance of this Agent when it actually participates in a semantic construction. */
    Integrity integrity();

    // TODO: Define invocation and explicit delegation from Owner-approved call semantics.
    // Autonomy belongs to the current acting continuation, not permanently to this Agent.
}
