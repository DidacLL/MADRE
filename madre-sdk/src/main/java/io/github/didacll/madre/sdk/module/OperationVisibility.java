package io.github.didacll.madre.sdk.module;

/**
 * Current 0.x installed-composition exposure of one Operation.
 *
 * <p>{@link #PUBLIC} means the current generic installed runtime may expose the Operation through
 * its public Module-composition/host invocation wiring. {@link #PRIVATE} keeps it out of those
 * generic installed invocation paths. This value is not Material confidentiality, external
 * publication, owner visibility, or reasoning locality, and it does not define what an Operation
 * semantically is.</p>
 *
 * <p>The current host reuses PUBLIC Operations for owner-local and external/PUBLIC adapters. That
 * coupling is executable compatibility behavior rather than a claim that every owner-facing
 * Operation must be public to other Modules or that the two-state enum is the final interaction
 * exposure model.</p>
 */
public enum OperationVisibility { PUBLIC, PRIVATE }
