# MADRE Security Algebra

Authority: `MADRE.md` defines product meaning. This document owns the typed security
facts, relation normal forms, composition laws, direct-user crossing, derivation, and
evidence contracts used at MADRE-governed boundaries.

## Purpose and boundary

MADRE preserves the owner's control of sensitive information and bounds consequential
machine execution. It governs crossings made through MADRE contracts. It is not IAM,
RBAC, a permission service, a provider-reputation system, an LLM truth score, a hostile
native-process sandbox, or a generic tool-approval framework.

The algebra is deterministic. There is no policy strategy to inject and no global
security score. Modules establish semantic facts; adapters establish mechanism facts;
Kernel composes those normalized facts without interpreting private payloads.

## Five nominal facets

The public carriers are five distinct ordered types. Their serialized ranks happen to
be `1..5`, but one facet cannot be substituted for another.

| Facet | Meaning | Values |
| --- | --- | --- |
| `Sensitivity` | sensitive state reachable through an exact source scope | `S1..S5` |
| `Privacy` | confidentiality/exposure of an exact observer scope | `PUBLIC`, `UNKNOWN`, `LOCAL_PRIVATE`, `MODULE_PRIVATE`, `SECRET` |
| `Integrity` | warranted causal/effect-realization responsibility of an exact scope | `I1..I5` |
| `Risk` | consequence class of one bounded effect variant | `R1..R5` |
| `Autonomy` | residual machine-controlled execution in that variant | `A1..A5` |

`Privacy.UNKNOWN` is a normalized fact: Privacy applies, but no stronger handling
guarantee is established. `None` means the facet does not apply. Location, provider,
protocol, or `execution_boundary` never computes Privacy. A remote P4 boundary and a
local P1 boundary are both valid declarations.

Integrity is deliberately narrow. It is not truth, intelligence, generic reliability,
model reputation, or moral correctness. Raw inference output gains no Integrity.

## Exact scopes and immutable facts

`SecurityScopeRef(owner_module_id, scope_id, publication_revision, scope_revision)`
identifies one exact, role-neutral projection. `SecurityObject` binds that identity to
only applicable Sensitivity, Privacy, and Integrity facts plus generic content and
contract digests. Its immutable `SecurityID` covers every bound field.

Material construction requires Sensitivity and cannot claim arbitrary Integrity.
Privacy-bearing observer construction requires Privacy. Controller/executor
construction requires Integrity. A scope that actually has more than one role may
carry the corresponding facts, but applicability is determined by the relation, not a
global subject-kind matrix.

Module and adapter layers supply generic digests. The security layer does not know
provider, endpoint, hostname, or location key vocabularies.

## Operation profiles and actual use

An `EffectProfile` is an immutable Operation-owned bounded execution variant. It
contains only exact Operation/profile identity and its `Risk` and `Autonomy`. It has no
Privacy, Integrity, nested SecurityObject, or material-disclosure flag.

`OperationUse` selects a published profile and carries concrete additional observers,
actual non-user controllers, and an optional active direct-user interaction. Callers
cannot override Risk or Autonomy. Protocol-known observers and executors are added by
Broker from the actual route.

## Feasible relation normal forms

Only feasible forms become active state. Composition returns a typed
`CompositionResult`: either an accepted form or relation-local failures. A denied
prospective candidate is evidence only and cannot contaminate the next candidate.

### Disclosure

For exact source scopes and actual observers:

```text
S = max(source Sensitivity)
P = min(observer Privacy)
ordinary feasibility: S <= P
```

`DisclosureNormalForm` carries exact source and observer SecurityIDs plus `S` and `P`.
Incremental composition uses `max` for another source and `min` for another observer.
Independent disclosures remain independent. A mechanism considered and rejected
before receiving bytes is not an observer in any accepted form.

### Control

For one exact EffectProfile and its actual non-user controllers:

```text
demand(R, A) = min(R, A)
controller Integrity = min(actual controller Integrity), or I5 if none
feasibility: demand <= controller Integrity
```

The demand is the greatest monotone value bounded by both Risk and Autonomy: their
meet on the five-rank chain. Different profiles never contribute facets to a fictional
combined profile.

### Effect execution

For one exact EffectProfile and actual effect-realizing executors:

```text
executor Integrity = min(actual executor Integrity)
feasibility: Risk <= executor Integrity
```

An executor set must be nonempty. A forwarding observer or historical participant is
not an executor merely because its SecurityID exists in evidence.

## Structural direct-user crossings

An otherwise inadmissible disclosure may be performed by the owner only as one exact,
live, direct action. This does not change Sensitivity or Privacy.

`DirectUserInteraction` is part of the active `InvocationContext` and binds the
interaction-owning Module, interaction scope/revision, and current execution instance.
Kernel allocates a fresh crossing identity and constructs `DirectUserAction` over:

- the active interaction;
- exact source SecurityID, scope revision, and content digest;
- the exact ordered observer path;
- the exact Operation and EffectProfile identity when an effect exists.

`DisclosureNormalForm` accepts the exception only when every field matches the current
crossing and active invocation. Changed material, revision, digest, observer,
destination, profile, effect, crossing identity, or execution instance fails.

Durable `WorkSubmission`, scheduling, and background continuation have no direct
interaction field. Completed action evidence is audit information, never a reusable
approval, role, credential, clearance, permission, or authority token.

## Derivation

`DerivationEvidence` is provenance, separate from active feasibility.

- Ordinary derivation binds exact sources/producers and cannot lower reachable source
  Sensitivity.
- Structured selection computes exactly the maximum Sensitivity of retained scopes.
- A Module-owned transform binds its exact procedure/revision, inputs, output
  SecurityID, and output digest. Because it creates a genuinely new representation,
  it may lower, preserve, or raise Sensitivity.
- Validation binds an exact procedure and actual Integrity-bearing validators before
  creating a distinct bounded Integrity projection. It is not generic truth scoring.
- Inference output is ordinary generated material and gains no automatic Integrity.

## Active state versus evidence

`SecurityEvidence` contains immutable scope facts, accepted relation forms,
derivations, decisions, and causal/provenance records. Merge is idempotent and detects
identity conflicts. Evidence has no numeric decision semantics.

Ordinary admission receives the current source, profile, observer, controller,
executor, and invocation values directly. It never searches evidence to rediscover
current operands. Registration, ownership, credentials, Work IDs, retries, historical
participants, and rejected candidates are not implicit operands.

One fixed audit/reconstruction function rebuilds recorded forms from their exact
objects and compares them with persisted summaries. It exists for persistence
verification, diagnostics, and tests, not ordinary invocation admission.

## Valuation ownership

Modules classify their own semantic scopes and transformations. Capability adapters
or installation construction establish the Privacy contract of concrete observation
boundaries. Effect-owning Modules establish bounded Risk and Autonomy profiles.
Integrity-bearing control/executor scopes declare their exact warranted contract.

Kernel validates identities and composes facts. It must not infer values from payload
text, provider name, local/remote location, credentials, prior success, or model output.
