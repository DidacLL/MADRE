/**
 * SPIRA vocabulary for the actual semantic facts participating in a construction.
 * Sensitivity comes from Material/context; Privacy from actual receivers; Integrity
 * from actual Agent/provenance participants; Risk from the selected Operation/effect;
 * and Autonomy from the current acting Agent continuation.
 *
 * <p>Actual participating Sensitivities accumulate by maximum, Privacies by minimum,
 * and Integrities by minimum. Risk and Autonomy are not running aggregates. The
 * relevant relations are information Sensitivity against receiving Privacy,
 * selected Risk and current Autonomy against actual non-user causal Integrity,
 * and selected Risk against actual effect-realizer Integrity. No central evaluator,
 * authorization object, or five-facet label is defined here.</p>
 *
 * <p>The detailed meaning and exact relations are in
 * {@code docs/architecture/security-algebra.md}. The enums name the settled
 * ordered values; they do not decide which constituents are actually involved.</p>
 */
package io.github.didacll.madre.sdk.spira;
