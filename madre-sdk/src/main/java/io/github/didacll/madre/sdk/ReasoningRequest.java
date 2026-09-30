package io.github.didacll.madre.sdk;

/**
 * Semantic need for reasoning created by an Agent. Relevant context, Material,
 * provenance, Owner instruction, SPIRA facts and the objective may participate.
 * The responsible Agent/Module context establishes the current valid semantic
 * construction. A ReasoningRequest never enters Kernel as a semantic object.
 */
public interface ReasoningRequest {
    // TODO: Define public construction without inventing mandatory S/P/I/R/A fields.
    // Execution preferences and the Module-owned reasoning executor are separate.
}
