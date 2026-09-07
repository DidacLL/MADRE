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

The reference implementation serializes the envelope canonically and records a SHA-256 integrity digest. This protects against unnoticed mutation inside MADRE's persistence/transport model; it is not a substitute for future package signatures or OS identity mechanisms.

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
    destination envelope,
    actual execution boundary,
    current policy,
)
```

The result is either admissible or inadmissible with deterministic deficits such as:

```text
invalid envelope integrity
insufficient requester trust
insufficient material trust
material too sensitive for target
risk above current policy for sensitivity
insufficient destination trust
scope incompatibility
execution-boundary mismatch
```

The current reference algebra is intentionally small and explicit. It provides a stable place to refine exact relations without changing ownership boundaries.

## 5. Discovery

Registry discovery uses a related visibility relation over requester trust/scopes and destination integrity. Visibility is not invocation authorization; the actual crossing is evaluated again when an Agent/Operation is invoked.

## 6. Provenance and unknown sources

Unknown or weakly described sources remain representable. Installation/adapters assign conservative trust/risk/boundary facts based on available provenance. Missing evidence is never silently equivalent to high trust.

## 7. References are not authority

Work ids, descriptor ids, material references and correlation ids locate state. They do not grant authorization. Every governed crossing is evaluated from current boundary facts and policy.

A previous accepted security decision remains historical evidence only.

## 8. Context minimization

Semantic context selection, redaction, anonymization and summarization are Module responsibilities. Kernel evaluates only the material actually submitted.

If a Module transforms sensitive material, the new representation receives a new digest, new envelope and derivation chain. Relabeling the original bytes with a lower sensitivity is invalid.

## 9. Evidence

Kernel persists security envelopes and decision deficits because those facts remain useful after transient content disposal. This permits audit of why an execution occurred without turning MADRE into private content storage.
