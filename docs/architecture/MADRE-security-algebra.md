# MADRE Security Algebra

Authority: `MADRE.md` defines product meaning. This document owns detailed security facts, carried composition, provenance, deterministic admissibility and security evidence.

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

Current Kernel `trust` is boundary/provenance trust. It does **not** mean semantic truth, prompt-injection resistance, hallucination probability, model-answer quality or generic AI-content safety.

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

The reference implementation uses canonical SHA-256 envelope integrity to detect mutation. That integrity value is evidence about the envelope's own continuity. It is not authority.

Kernel does not infer semantic redaction/minimization policy from private content. Modules own domain-specific classification, minimization and semantic projection before/while creating the material they expose.

## 3. Carried Security Context

A request/work lifecycle carries a `SecurityContext` containing immutable envelopes accumulated so far.

Conceptually:

```text
originating request facts
        |
        + material facts
        |
        + Module / Agent / Operation boundary facts
        |
        + inference mechanism / model boundary and risk facts
        |
        + derived representation facts
        v
carried SecurityContext
```

Each governed crossing appends facts introduced by that concrete crossing. Earlier envelopes remain unchanged.

This is the meaning of additive boundary algebra in MADRE: **composition of independent boundary facts**, not arithmetic addition and not reconstruction from an external authority database.

A registry update cannot rewrite a `SecurityContext` already carried by accepted work. Delayed execution and retry continue from the context stored with the WorkRecord.

## 4. Reference composition

The current minimal reference implementation derives conservative effective facts from all envelopes in the context:

```text
effective sensitivity = highest carried sensitivity
effective trust       = lowest carried trust
effective risk        = highest carried risk
effective scopes      = union of carried scope/domain facts
```

The initial admissibility relation is:

```text
effective trust >= effective sensitivity
and
effective trust >= effective risk
```

Invalid envelope integrity is independently inadmissible.

This is a concrete initial relation, not a claim that all future MADRE security semantics reduce to those comparisons. New relations must be justified by actual MADRE requirements and expressed as algebra over carried facts, never as external grants or mutable authorization records.

Scope/domain semantics are owned by Modules. Kernel carries resulting scope facts; it does not invent a universal domain ACL vocabulary.

## 5. Non-authority references and registries

Registry entries locate and describe published interoperability surfaces. They do not authorize anything.

A descriptor may contain a security envelope because invoking that Agent, Operation or inference mechanism introduces concrete boundary/risk facts. When that descriptor participates in an actual crossing, its envelope is composed into the carried context and evaluated.

Unknown or weakly described integrations may contribute conservative provenance/risk facts. That is a property of the prospective crossing; registration itself is never semantic certification or trust.

Therefore:

```text
registered == known/discoverable
registered != trusted
registered != permitted
registered != authorized
```

Changing a registry entry can change facts of a **future crossing that has not occurred**. It cannot retroactively change security facts already accumulated into existing work.

Work IDs, material references, retrieval claims/coordination values, descriptor IDs, idempotency keys and correlation identifiers are references/coordination data only. Possession of any of them grants no MADRE authority.

## 6. Discovery

Boundary-filtered discovery receives the requester's carried `SecurityContext` directly.

For each candidate descriptor:

```text
candidate_context = requester_context + descriptor_envelope
SecurityAlgebra.evaluate(candidate_context)
```

The registry never looks up a requester trust level and never grants visibility merely because a Module was accepted or registered.

Discovery is advisory. Actual invocation is evaluated again with concrete material and endpoint/mechanism facts.

## 7. Durable work

A durable `WorkSubmission` carries the security context accumulated before submission plus the immutable envelope describing the Module-owned material referenced by the work.

MADRE composes that material envelope into the carried context and persists the resulting security history in `WorkSpec`. The actual private material remains with the Module and is resolved just-in-time according to `MADRE-execution-contract.md`.

At inference execution:

```text
stored_work_context
    + selected mechanism boundary envelope
    -> SecurityAlgebra.evaluate(...)
```

Delayed work does not refresh requester trust from a Module registry. Retry does not obtain authority from a previous successful decision.

Material returned during just-in-time resolution must match the previously carried material reference/digest/envelope continuity; the retrieval claim itself is not security authority.

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

The result of either evaluation applies only to that concrete crossing. Dispatch, registration, identity references or previous decisions never become reusable permission.

## 9. Authentication, credentials and future deployment boundaries

MADRE's current local single-owner installation does not introduce separate Module authentication/authorization infrastructure.

HTTP/IPC/SDK transport mechanics are not security authority for the algebra.

Provider API keys, OAuth/login/session flows and other mechanism-specific credentials belong to the relevant adapter/external software. They access that provider/mechanism; they do not authorize Module work inside MADRE. Detailed provider integration belongs to `MADRE-execution-contract.md`.

If a future deployment introduces a concrete adversarial multi-user/remote trust boundary, that requirement must be designed explicitly rather than pre-installed as generic security boilerplate.

Future deterministic AI-specific security signals may be added only when MADRE has a concrete model for them. They must not silently redefine today's boundary/provenance `trust` dimension.

## 10. Evidence

Kernel persists the carried context used for each decision, resulting effective dimensions/scopes, the concrete target/execution boundary and deterministic decision/deficits.

It does not persist prompt/context/output content merely to explain security decisions.
