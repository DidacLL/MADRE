# MADRE Security Algebra

MADRE security is deterministic admissibility over immutable current boundary facts.

## 1. Normalized levels

```text
SYSTEM_RESERVED = 0
LEVEL_1 = 1
LEVEL_2 = 2
LEVEL_3 = 3
LEVEL_4 = 4
LEVEL_5 = 5
```

Ordinary security facts use levels 1..5. The initial independent dimensions are:

```text
sensitivity
trust
risk
```

They share a normalized vocabulary so relations are simple to represent. They are never summed, averaged or collapsed into one score. Scope/domain boundaries are independent non-numeric relations.

## 2. Security Envelope

A Security Envelope binds boundary facts to a descriptor/material subject:

```text
subject identity or digest
sensitivity
trust
risk
scopes
origin
provenance chain
derivation chain
descriptor/version identity
integrity digest
```

The reference implementation serializes the envelope canonically and records a SHA-256 integrity digest. This detects unnoticed mutation inside MADRE's persistence/transport model. It is **not** cryptographic proof that an arbitrary caller was entitled to mint the represented trust/provenance.

Authoritative requester/descriptor boundary facts therefore come from installation-controlled registration/adaptation, not from loose numbers or self-issued requester envelopes in an invocation payload. Future package signatures, OS identities or remote attestation can strengthen provenance without changing the algebraic API.

Durable envelope metadata is identifier-shaped where practical so provenance/reference fields do not become hidden private-content persistence channels.

Envelopes are immutable. A transformation creates a new subject/digest and new envelope with derivation provenance.

## 3. Boundary requirements

A published target can declare execution-relevant relations such as:

```text
minimum requester trust
minimum input trust
maximum input sensitivity
target risk
allowed scopes
allowed execution boundaries
```

Installation policy can relate sensitivity to allowed risk or required destination trust without arithmetic aggregation.

## 4. Evaluation

For capability execution or explicit brokering Kernel evaluates the current crossing:

```text
SecurityAlgebra.evaluate(
    requester envelope,
    material envelope,
    target requirements,
    target descriptor envelope,
    destination boundary envelope,
    actual execution boundary,
    current policy,
)
```

The target descriptor and destination are independent facts. A descriptor may have weak provenance even when its owning Module is strongly identified, and vice versa.

The current algebra checks relationships including requester/material provenance compatibility, requester trust, material trust/sensitivity, target and destination risk/trust, scopes and execution boundary. Policy tables relate sensitivity to maximum risk and minimum destination trust without converting dimensions into one score.

The result is either admissible or inadmissible with deterministic deficits such as:

```text
invalid envelope integrity
insufficient requester trust
material trust inconsistent with requester provenance
insufficient material trust
material too sensitive for target
risk above current policy for sensitivity
insufficient target/destination trust
scope incompatibility
execution-boundary mismatch
```

The current reference algebra is intentionally small and explicit. It provides a stable place to refine exact relations without changing ownership boundaries.

## 5. Discovery

Registry discovery uses a related visibility relation over the currently registered requester boundary plus descriptor and destination facts. Visibility is not invocation authorization; the actual crossing is evaluated again when an Agent/Operation is invoked.

## 6. Provenance and unknown sources

Unknown or weakly described sources remain representable. Installation/adapters assign conservative trust/risk/boundary facts based on available provenance. Missing evidence is never silently equivalent to high trust.

A material envelope cannot legitimately claim greater trust than its current producing/requesting Module boundary. Derived material receives its own envelope and provenance rather than inheriting stronger authority by reference possession.

## 7. References are not authority

Work ids, descriptor ids, material references and correlation ids locate state. They do not grant authorization. Every governed crossing is evaluated from current boundary facts and policy.

A previous accepted security decision remains historical evidence only. Delayed work resolves current requester/Module facts when it executes rather than treating acceptance as a durable grant.

## 8. Context minimization

Semantic context selection, redaction, anonymization and summarization are Module responsibilities. Kernel evaluates only the material actually submitted.

If a Module transforms sensitive material, the new representation receives a new digest, new envelope and derivation chain. Relabeling the original bytes with a lower sensitivity is invalid.

## 9. Evidence

Kernel persists enough structured decision inputs to reconstruct which registered requester/material/target/destination boundary facts were evaluated: identities/fingerprints, target requirements, actual execution boundary, decision and deficits.

This permits audit after transient content disposal without persisting the private content itself. Provider/model failure text is not durable security evidence and is reduced to bounded failure codes by default.
