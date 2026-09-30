package io.github.didacll.madre.sdk;

import io.github.didacll.madre.sdk.spira.Risk;

/** Bounded action supplied by a Module; its providing Module need not have an Agent. */
public interface ModuleOperation extends Executable {
    String id();

    /** Consequence of this concrete Operation/effect when selected. */
    Risk risk();

    // TODO: Express accepted Material boundaries and their Privacy, and promised output
    // Sensitivity, without imposing one input/output shape on all Operations.
    // Invocation must retain the acting Agent's semantic continuation.
}
