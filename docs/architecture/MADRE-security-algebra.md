# MADRE Security Algebra

MADRE security is deterministic admissibility over immutable facts carried by the concrete request/work lifecycle.

It is deliberately **not** an authentication system, ACL system, registry-grant system, token system, role system or installation-acceptance system.

## 1. Normalized dimensions

```text
SYSTEM_RESERVED = 0
LEVEL_1 = 1
LEVEL_2 = 2
LEVEL_3 = 3
LEVEL_4 = 4
LEVEL_5 = 5
```

Ordinary security facts use levels 1..5. The initial independent numeric dimensions are:

```text
sensitivity
trust
risk
```

They share one normalized vocabulary but remain independent dimensions. They are not arithmetically summed or averaged into one score.

Scope/domain facts remain a separate non-numeric dimension.

## 2. Security Envelope

A `SecurityEnvelope` binds security facts to the thing that contributed them:

```text
subject identity or digest
sensitivity
trust
risk
scopes / domain facts
origin
provenance chain
derivation chain
descriptor/version identity
integrity digest
```

For material, the subject is normally the payload digest. A transformed representation receives a new digest and a new envelope; the source envelope is never relabelled in place.

The reference implementation uses canonical SHA-256 envelope integrity to detect mutation. That integrity value is evidence about the envelope's own continuity. It is not an authorization token and does not turn registration or possession into authority.

## 3. Carried Security Context

A request/work lifecycle carries a `SecurityContext` containing the immutable envelopes accumulated so far.

Conceptually:

```text
originating request facts
        |
        + material facts
        |
        + Module / Agent / Operation boundary facts
        |
        + capability / model boundary and risk facts
        |
        + derived representation facts
        v
carried SecurityContext
```

Each governed crossing appends the facts introduced by that concrete crossing. Earlier envelopes remain unchanged.

This is the meaning of additive boundary algebra in MADRE: **composition of independent boundary facts**, not arithmetic addition and not reconstruction from an external authority database.

A registry update cannot rewrite a SecurityContext already carried by accepted work. Delayed execution and retry continue from the carried context stored with the WorkRecord.

## 4. Reference composition

The current minimal reference implementation derives conservative effective facts from all envelopes in the context:

```text
effective sensitivity = highest carried sensitivity
effective trust       = lowest carried trust
effective risk        = highest carried risk
effective scopes      = union of carried scope/domain facts
```

The initial admissibility relation is intentionally small:

```text
effective trust >= effective sensitivity
and
effective trust >= effective risk
```

Invalid envelope integrity is independently inadmissible.

This is a concrete initial relation over the normalized dimensions, not a claim that all future MADRE security semantics reduce to those two comparisons. New relations must be justified by actual MADRE requirements and expressed as algebra over carried facts, never as external grants or mutable authorization records.

Scope/domain semantics are owned by Modules. Kernel carries the resulting scope facts; it does not invent a universal domain ACL vocabulary.

## 5. Registry and descriptors

Registry entries locate and describe published interoperability surfaces. They do not authorize anything.

A descriptor may contain a security envelope because invoking that Agent, Operation or Capability introduces concrete boundary/risk facts. When the descriptor actually participates in discovery or execution, its envelope is appended to the request's carried context and the algebra is evaluated.

Therefore:

```text
registered == known/discoverable
registered != trusted
registered != permitted
registered != authorized
```

Changing a registry entry can change the facts of a **future crossing that has not yet occurred**. It cannot retroactively change security facts already accumulated into existing work.

## 6. Discovery

Boundary-filtered discovery receives the requester's carried `SecurityContext` directly.

For each candidate descriptor:

```text
candidate_context = requester_context + descriptor_envelope
SecurityAlgebra.evaluate(candidate_context)
```

The registry never looks up a requester trust level and never grants visibility because a Module was accepted or registered.

Discovery remains advisory. The actual invocation crossing appends its concrete material and endpoint/capability facts and is evaluated again.

## 7. Work admission and delayed execution

A `WorkSubmission` carries the security context accumulated before submission plus the submitted material envelope.

MADRE appends the material envelope and persists that resulting context in `WorkSpec`.

At capability execution:

```text
stored_work_context
    + selected capability boundary envelope
    -> SecurityAlgebra.evaluate(...)
```

Delayed work does **not** refresh requester trust from a Module registry. Retry does **not** obtain new authority from a previous successful decision. Work IDs, material references, descriptor IDs and idempotency keys are references only.

## 8. Agent / Operation brokering

An explicit broker request carries its current `SecurityContext`.

Input crossing:

```text
carried request context
    + input material envelope
    + selected descriptor envelope
    + concrete Module endpoint boundary envelope
```

Return crossing continues from that same context and appends the returned material envelope.

The result of either evaluation applies only to that concrete crossing. No dispatch, registration, identity reference or previous decision becomes a reusable permission.

## 9. Authentication and credentials

MADRE's current local single-owner installation does not introduce a separate Module authentication or authorization architecture.

The local administrator owns the installation and configuration. HTTP/IPC/SDK transport details are not security authority for the algebra.

Provider credentials, API keys, OAuth flows and provider-specific validation belong to the relevant Capability adapter. They authenticate MADRE to that provider; they do not authorize Module work inside MADRE.

If a future concrete deployment introduces an actually adversarial multi-user or remote trust boundary, that requirement must be designed explicitly rather than pre-installed as generic security boilerplate.

## 10. Evidence

Kernel persists the carried context used for each decision, the resulting effective dimensions/scopes, the concrete target and physical execution boundary, and the deterministic decision/deficits.

It does not persist prompt/context/output content merely to explain security decisions.
