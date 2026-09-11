# Security Algebra rationale

Non-normative. Current meaning is in `../../MADRE.md` and
`../architecture/MADRE-security-algebra.md`.

## User-sovereign threat boundary

MADRE protects the owner's governed data and bounded execution crossings, especially
opaque third-party/SaaS exposure. It does not try to protect the owner from software
they deliberately install with unrestricted host access, or paternalistically forbid
an exact action the owner can perform manually. An exceptional crossing therefore
binds live direct action to one exact material/route/effect; it is not reusable
authorization.

This boundary rejects imported enterprise IAM, provider reputation, prompt-injection
ontologies, truth scores, and generic tool-permission workflows. Those models answer
different questions and obscure MADRE's actual disclosure and effect topology.

## Models do not command Kernel

A model result is generated material. A Module owns its interpretation. If the Module
later invokes a bounded Operation, that is a separate typed call. Kernel never parses
model text into shell, network, deletion, or domain commands, so model intent and
truthfulness are not Kernel Integrity.

## Adaptation before policy evaluation

Security is most useful when it exposes the maximum safe subset of mechanisms:
minimize/select/transform material, choose a more private physical route, select a
less consequential or less autonomous profile, or make an exact action directly.
An injectable policy evaluator encourages opaque allow/deny meanings and repeated
whole-history scoring. Fixed relation forms instead make the limiting fact explicit
and composable.

The separation witnesses remain useful:

- an S2 projection can cross where its S5 source cannot;
- a P4 observer differs from P2 under the same route;
- `(R5,A1)` and `(R1,A5)` each demand 1 and must not combine into `(R5,A5)`;
- effect realization needs an actual executor independently of semantic control;
- direct action can cover one exact disclosure without changing S or P.

## Remote is not Privacy

Transport locality does not establish confidentiality. A remote owner-controlled
service may be P4; a local public-publishing surface may be P1; a local SaaS client may
be P2. Adapter/installation construction normalizes the real contract. UNKNOWN means
Privacy applies but no stronger fact is established—not that a parameter is missing.

## Evidence is not current state

Accumulated history is valuable for provenance, persistence verification, audit, and
debugging. It is harmful as an implicit bag from which runtime reconstructs current
callers, destinations, controllers, or executors. Historical possession of a
SecurityID must never turn that participant into a current operand.

Current causal identity is explicitly carried by InvocationContext. Current relation
operands are passed directly. Accepted normal forms are minimal active summaries;
denied candidates remain separate decision evidence.

## Module-owned semantic change

Kernel cannot decide that redaction, aggregation, summarization, or validation changed
meaning by inspecting bytes. Ordinary derivation therefore preserves Sensitivity
closure. A lower-sensitivity representation requires an exact Module-owned transform
procedure and new content-bound SecurityID. A stronger Integrity projection requires
an exact validation procedure and actual bounded validators. This is honest semantic
ownership rather than a central policy engine.

Earlier shared generic level carriers, subject-kind matrices, profile Integrity,
material-only disclosure fields, remote-to-P2 rules, evaluator injection, and
history-conditioned discovery were implementation fossils. Their provenance remains
in Git history; do not reconstruct them as compatibility requirements.
