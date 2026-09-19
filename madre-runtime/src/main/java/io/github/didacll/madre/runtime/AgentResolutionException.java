package io.github.didacll.madre.runtime;

/** An Operation cannot be assigned one unambiguous semantic actor. */
public final class AgentResolutionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public AgentResolutionException(String message) {
        super(message);
    }
}
