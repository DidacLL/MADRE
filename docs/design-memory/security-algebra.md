# Security Algebra Design Memory

Authority: non-normative design memory. Canonical security architecture is `docs/architecture/MADRE-security-algebra.md`.

## Original intent: simple normalized algebra, not permission matching

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

The security model is intentionally novel and small. It should not be reconstructed from ACLs, clearances, role systems, policy matrices, zero-trust boilerplate, or paired `requirement`/`capability` property checks.

The Owner's original intuition is to normalize a few security-relevant dimensions to a small common scale (`1..5`, with `0` system-reserved) so heterogeneous facts can be composed deterministically across the concrete lifecycle.

The purpose of normalization is **not** to create dozens of counterpart fields such as:

```text
actor maximum sensitivity
context required trust
operation minimum input trust
target maximum input sensitivity
clearance vs classification
```

Those are list/matrix checks expressed numerically, not the intended algebra.

## Concrete intuition

Provenance: **DIRECT OWNER**
Status: **CONFIRMED examples, not a frozen formula**

Different participants contribute different kinds of consequence.

Examples:

- medical information is highly sensitive;
- known user secrets are among the most sensitive material;
- an encapsulated local UX Agent or local inference mechanism generally preserves more privacy than a broad Internet-facing or remote-cloud path;
- an Operation or capability has its own consequence/risk contribution;
- creating executable material such as a Bash/shell script is high-risk and must not be casually classified as an ordinary moderate file write;
- an action that can publish, destroy, execute or otherwise materially affect the outside world contributes substantially more risk than a bounded local transformation.

The algebra evaluates the actual combination of carried material sensitivity, boundary/privacy/provenance facts, capability/Operation risk, and prior crossings.

Do not invent the final formula from these examples. Derive it from real MADRE cases.

## Carried lifecycle is essential

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

Security facts travel with the concrete request/work lifecycle. Later crossings add facts; mutable registry state does not retroactively rewrite the history of accepted work.

Registration, installation acceptance, identity, possession of references, bearer tokens, roles, ACLs, allowlists and previous successful decisions grant no MADRE permission.

The retrieval claim used for durable material resolution is coordination only, never authority.

## Kernel does not interpret payload semantics

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

Kernel does not read prompts or generated outputs for semantic meaning. It operates on explicit metadata, security/boundary facts, references/digests and physical execution state.

A generated output therefore cannot change Kernel security merely by saying something. The relevant protection is structural: any later use of that material occurs through Module-owned semantics and new bounded crossings.

Inside a Module, generated output may deliberately be used as the next input, persisted as domain state, accepted as evidence, or otherwise given whatever semantic role the Module's Agent architecture requires. MADRE must not impose a universal rule that model-generated material can never become authoritative inside a Module.

## Bounded effects, not broad AI shell/Internet authority

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

MADRE-provided integration surfaces do not give AI actors an unbounded shell or unrestricted Internet environment. External/system effects are exposed through specific bounded Operations or concrete mechanisms with known boundary/risk properties.

This boundedness is part of why the algebra can remain small and meaningful: MADRE evaluates concrete operations/crossings instead of trying to secure an arbitrary omnipotent agent process.

A user may deliberately install custom Modules/adapters with different behavior; that explicit integration must still declare and cross the relevant MADRE boundaries rather than becoming hidden generic authority.

## Rejected generated interpretations

Provenance: **GENERATED INTERPRETATION**
Status: **REJECTED**

Do not resurrect without fresh Owner evidence:

- actor sensitivity ceilings;
- clearance/classification matching;
- minimum-input-trust fields;
- policy/role/grant registries;
- ACL-style scope authorization;
- generic bearer authorization for local Modules;
- large mirrored requirement/property taxonomies;
- semantic prompt/output inspection by Kernel as current security;
- assuming conventional security vocabulary has its conventional industry meaning inside MADRE.

## Current implementation warning

Status: **OPEN / REQUIRES RECONCILIATION**

The current implementation reduces carried envelopes with `max(sensitivity)`, `min(trust)`, `max(risk)` and admits when `trust >= sensitivity` and `trust >= risk`.

That relation is implementation history, not a frozen Owner formula. Preserve useful carried-context/integrity/provenance behavior while re-deriving the minimal contributor structure and algebra from MADRE cases. Do not overreact by deleting the whole security lifecycle because this particular reduction is questionable.

Future deterministic AI-specific signals such as prompt-injection detection or content-quality evidence are separate research directions and must not be silently folded into today's boundary/provenance semantics.
