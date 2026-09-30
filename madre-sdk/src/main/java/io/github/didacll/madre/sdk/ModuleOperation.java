package io.github.didacll.madre.sdk;

import io.github.didacll.madre.sdk.spira.Risk;

/**
 * Bounded action supplied by a Module; its provider need not have an Agent.
 * Every execution has an acting Agent continuation, including cross-Module
 * calls. An accepted Material boundary contributes receiving Privacy; produced
 * Material may promise a maximum Sensitivity. The concrete selected effect
 * contributes Risk, not the Module or unused Operations.
 */
public interface ModuleOperation {
    /** Consequence of this concrete bounded action when selected. */
    Risk risk();

    // TODO: Define the input/output boundary contract and invocation shape.
    // A later call design must preserve the acting Agent and identify the actual
    // selected effect if one exposed Operation can select effects with different Risks.
}
