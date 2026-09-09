# MADRE Security Algebra

Authority: `MADRE.md` defines product meaning. This document owns the security object model, carried composition and deterministic admissibility contract.

MADRE security is a small normalized algebra over the concrete actors, material, Operations and execution mechanisms participating in a request/work lifecycle.

The exact formula is intentionally not frozen yet. The object model and responsibility boundaries are.

## 1. Normalized values

Security-relevant dimensions use a small common scale:

```text
SYSTEM_RESERVED = 0
LEVEL_1 = 1
LEVEL_2 = 2
LEVEL_3 = 3
LEVEL_4 = 4
LEVEL_5 = 5
```

The dimensions are independent. Sharing a numeric range exists to make composition simple; it does not make the dimensions interchangeable.

Examples:

- an Artifact may have very high Sensitivity;
- the local Agent handling it may have very strong trust/isolation/privacy characteristics;
- an Operation over unrelated public data may still have very high action risk;
- a highly autonomous Operation may require a different decision from the same effect performed with direct user intervention.

High trust never means low sensitivity. Sensitivity describes material; actor/boundary security describes a different concern.

## 2. SecurityID and SecurityObject

Every MADRE object that contributes security facts owns/references a stable `SecurityID`.

That identifier resolves to an immutable/bound **SecurityObject** describing the subject and only the normalized values meaningful for that subject kind.

Conceptually:

```text
SecurityObject
    securityId
    subjectId
    subjectKind
    applicable normalized security values
    binding / integrity evidence
```

The SecurityObject must be structurally bound to the subject it describes so a downstream Agent cannot freely rewrite its own valuation or replace another participant's values by assertion.

The current local single-owner implementation may satisfy this binding with ordinary immutable runtime/storage structures and integrity checks. Stronger cryptographic/identity machinery is not implied unless a future deployment actually requires it.

## 3. Values are subject-kind specific

MADRE does not require every SecurityObject to carry every possible dimension.

Security values are attached only where they have meaning.

The current conceptual mapping includes:

### Artifact / ContextBundle

Material-oriented facts may include:

```text
Sensitivity
IntendedUse
other material-specific facts required by the final algebra
```

A `ContextBundle` is artifact-like material and receives its SecurityObject when the owning Module constructs the representation for a concrete purpose.

### Module / Agent

Actor/boundary facts may include the trust, containment/isolation/privacy and risk characteristics that the final algebra proves necessary.

CORE-capable Modules are expected to provide the strongest applicable actor/containment characteristics because they may handle highly sensitive personal/system material.

### Operation

Operation security facts include effect/consequence risk and **Autonomy**.

Autonomy expresses the conditions under which the Operation can continue after being triggered, including whether user intervention/acknowledgement is required.

The same high-risk effect can therefore receive a different admissibility result when it requires active user involvement versus when it is fully autonomous.

### Capability / inference mechanism

Mechanism facts describe the relevant execution-boundary/privacy/exposure and risk characteristics of the concrete physical route.

A local isolated mechanism and a remote/cloud mechanism can therefore contribute materially different security consequences even when both perform the same inference API operation.

The final formula may refine this mapping, but it must not turn into a universal bag of irrelevant fields on every object.

## 4. IntendedUse and transformation

`IntendedUse` belongs to the security description of Artifact/ContextBundle material used for a concrete purpose.

It identifies how the receiving reasoning/action context intends that material to participate. It does not magically rewrite base material facts.

A Module may transform, minimize, anonymize, summarize, omit or otherwise derive a new representation according to its own domain semantics. The derived Artifact/ContextBundle receives its own identity/security definition.

Kernel does not perform semantic minimization by reading private content.

## 5. Carried composition

A request/work lifecycle carries the security identities introduced by the objects that have actually participated.

Conceptually:

```text
originating Agent SecurityObject
    + owning Module SecurityObject
    + ContextBundle / Artifact SecurityObjects
    + selected Operation SecurityObject
    + selected Capability SecurityObject
    + later participating objects/crossings
    -> carried security set
```

`+` means compositional accumulation, not arithmetic addition.

At every governed boundary MADRE evaluates the carried objects plus the prospective participant through the Security Algebra.

```text
SecurityAlgebra.evaluate(carried objects, prospective object/boundary)
    -> admissible
    OR
    -> inadmissible + failure point/evidence
```

Earlier security identities remain part of the lifecycle. Mutable registry metadata does not retroactively rewrite accepted work.

Restart/retry restores the durable carried security identities and adds the facts introduced by the new concrete attempt.

## 6. Algebraic decision

The final algorithm must answer the concrete question:

> Given the material, actors, Operations, mechanisms and accumulated boundary history participating in this request, is this next crossing/execution acceptable?

The normalized dimensions exist so this can be one understandable deterministic calculation rather than a collection of case-by-case permission lists.

The formula must be derived from representative MADRE cases, including at least:

```text
highly sensitive personal/medical/secret material on a strongly isolated local path
highly sensitive material considered for a less-private remote path
low-sensitivity material used by a destructive/high-consequence Operation
high-risk Operation with explicit user involvement versus full autonomy
context minimized/anonymized into a new lower-exposure representation
multiple Modules/Agents/Capabilities contributing different security facts over one lifecycle
```

The formula is a dedicated design/research task and should be verified with explicit case tables/tests before becoming implementation authority.

## 7. Failure and remediation

An inadmissible boundary is not always the end of the semantic task.

MADRE should return enough deterministic failure information to the nearest capable Module/Agent so it can choose a different valid strategy when one exists.

Possible Module/Agent responses include:

```text
minimize or anonymize material
omit unnecessary material
construct a different ContextBundle
select another Operation
select another Capability/mechanism
defer or require user involvement
abandon the requested path
```

Any transformed material or changed participant introduces the corresponding new SecurityObject(s), after which the algebra is evaluated again.

## 8. Registration and credentials

The registry stores existence, descriptors and routing information.

Registration, installation acceptance, ordinary identity, possession of a Work/material reference, roles, ACLs, allowlists or previous successful decisions are not separate MADRE authorization sources.

Provider API keys, OAuth/account sessions and similar credentials belong to a concrete Capability adapter/external system. They allow access to that mechanism; they do not grant MADRE work authority.

## 9. Bounded effects

MADRE-provided AI surfaces do not expose a generic unrestricted shell or unrestricted Internet environment.

System/network/external effects are concrete bounded Operations or mechanisms, each with its own SecurityObject and known effect/boundary facts.

Executable artifacts such as shell/Bash code participate in high-risk effect scenarios when they can become executable behavior. Publishing, destructive and irreversible Operations likewise carry strong consequence/risk facts regardless of whether their input material is sensitive.

## 10. Payload opacity

Kernel does not read prompts or generated outputs to infer semantic permission, truth or policy.

Generated output cannot modify Kernel security through semantic text. Once delivered, however, it is ordinary Module-owned Artifact material and may be reused according to Module semantics; any later crossing receives its own SecurityObject evaluation.

## 11. Unknown security values

How an unknown/third-party object receives its initial SecurityObject remains an explicit open design problem.

Potential inputs may include conservative defaults, installer/user declarations, concrete adapter facts and future SDK-assisted code inspection/analysis for MADRE-native Modules.

No one strategy is canonical until the final algebra and validation process are designed.

## 12. SDK support

The SDK should provide the safe construction/binding/propagation of `SecurityID` and `SecurityObject` data so ordinary Module developers do not manually recreate the protocol.

Future SDK tooling may help analyze a MADRE-native Module implementation and compare observed code/integration characteristics with its declared security valuation. Such analysis is a validation aid, not an independent permission source.

## 13. Evidence

Kernel may persist the normalized security identities/values and deterministic decision evidence needed to reproduce/explain an admission result, without persisting private material content.

The evidence schema should follow the final algebra and remain smaller than the payload it protects.
