package io.github.didacll.madre.sdk;

/** Semantic actor supplied by a Module, distinct from the Operations it invokes. */
public interface MADREAgent {
    String id();

    // TODO: Define invocation and explicit delegation from Owner-approved call semantics.
    // Autonomy belongs to the current acting continuation, not permanently to this Agent.
}
