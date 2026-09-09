# Security Algebra Design Memory

Authority: non-normative design memory. Canonical security architecture is `docs/architecture/MADRE-security-algebra.md`.

## Why the algebra exists

**OWNER — CONFIRMED**

MADRE needs a compact deterministic way to evaluate the real consequence of moving material through Agents, Modules, Operations and inference mechanisms without requiring case-by-case user approval for every internal step.

The original idea is to normalize a small number of independent security dimensions to a common scale so a single compositional algorithm can evaluate the actual request/work path.

The common numeric range exists for algebraic simplicity. It is not intended to create a conventional permission matrix.

## Independent dimensions

**OWNER — CONFIRMED**

Different things contribute different security facts.

Examples:

- medical data is highly sensitive;
- known user secrets may carry maximum Sensitivity;
- CORE-owned personal/profile material can be extremely sensitive;
- a strongly isolated local Agent can have strong actor/security characteristics while handling that high-sensitivity material;
- a local inference mechanism can preserve substantially more privacy than a remote/cloud path;
- a destructive, publishing or executable Operation can be very high risk even when its input is public;
- direct user involvement can materially change whether a risky action is acceptable compared with fully autonomous continuation.

Sensitivity, trust/isolation/privacy, risk/consequence, IntendedUse and Autonomy therefore describe different axes. One should not be inferred from another.

## SecurityObject intuition

**OWNER — CONFIRMED**

Every actor/action/artifact/mechanism that matters to the algebra should expose one unique bound security identity describing only its relevant values.

A request from an Agent therefore carries that Agent's SecurityObject and the owning Module's SecurityObject; material contributes Artifact/ContextBundle SecurityObjects; a selected Operation and Capability contribute theirs; later participants add theirs.

The algebra evaluates the carried set repeatedly as new boundaries are proposed.

This should stay structurally simple enough to reason about and test.

## Remediation is part of the model

**OWNER — CONFIRMED**

A failed boundary can return control/evidence to the nearest capable Agent/Module so it can try a valid alternative.

Examples:

```text
minimize
anonymize
omit some context
use another representation
choose another Operation
choose a different/local Capability
require user involvement
stop the route
```

The new representation or participant receives its own SecurityObject and the algebra runs again.

## Concrete examples for formula research

**EXAMPLE**

The eventual formula should be tested against cases such as:

1. maximum-sensitivity secret used only by an isolated local CORE Agent and local inference;
2. the same secret considered for remote inference;
3. public text passed to an Operation that can execute a shell script;
4. a high-consequence Operation requiring active user acknowledgement;
5. the same Operation running fully autonomously;
6. sensitive context transformed into a genuinely minimized/anonymized new Artifact before an external crossing;
7. several Modules/Agents contributing context over a long durable work lifecycle.

These cases are more useful than inventing a large vocabulary before the formula exists.

## Open formula

**OPEN**

The exact algorithm remains a specialized design/research problem.

It should be derived deliberately, represented with small case tables/tests and remain understandable enough that developers can predict why a boundary passes or fails.

## Unknown/third-party valuation

**OPEN**

A third-party/unknown Module or adapter may not arrive with a trustworthy MADRE-native SecurityObject valuation.

Possible future inputs include conservative defaults, explicit installer/user declarations, facts inferred from its concrete adapter/mechanism, and SDK-assisted analysis of MADRE-native source/contracts.

The final rule should be chosen together with the algebra rather than guessed in advance.

## Bounded effects simplify the problem

**OWNER — CONFIRMED**

MADRE does not give its Agents generic unrestricted shell or Internet authority. Effects are represented as concrete Operations/mechanisms.

This is important because the security formula only needs to reason about declared bounded participants and consequences rather than an omnipotent AI process.
