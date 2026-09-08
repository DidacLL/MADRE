# MADRE Security Algebra

Authority: `MADRE.md` defines product meaning. This document owns detailed security facts, carried composition, deterministic admissibility and security evidence.

MADRE security is deterministic evaluation over a small set of normalized facts carried by the concrete request/work lifecycle.

It is deliberately **not** an authentication system, ACL system, registry-grant system, role system, clearance model, policy-matrix system or installation-acceptance system.

## 1. Normalized vocabulary

```text
SYSTEM_RESERVED = 0
LEVEL_1 = 1
LEVEL_2 = 2
LEVEL_3 = 3
LEVEL_4 = 4
LEVEL_5 = 5
```

Ordinary security-relevant facts use levels `1..5` so materially different concerns can participate in one simple deterministic algebra without creating a large bespoke taxonomy.

The current vocabulary uses concepts such as:

```text
sensitivity
trust / privacy-provenance quality
risk / consequence
```

These words must be interpreted only through MADRE's own boundary model. Do not import conventional clearance, IAM or policy-engine meanings merely because the vocabulary overlaps with security engineering terminology.

Current `trust` terminology is about boundary/provenance/security quality. It does **not** mean semantic truth, prompt-injection resistance, hallucination probability, answer quality or generic AI-content safety.

## 2. Contributors add relevant boundary facts, not mirrored requirements

A request/work lifecycle accumulates security-relevant facts from the concrete things and boundaries it actually uses.

Typical contributors include:

```text
originating request / Module boundary
material or context sensitivity
Agent / Module boundary
Operation consequence/risk
inference-mechanism privacy/exposure/risk
new derived material or representation
actual transfer/execution boundary
```

The purpose is not to define counterpart fields such as:

```text
actor maximum sensitivity
context minimum trust
operation required clearance
target maximum input sensitivity
```

Those would be pairwise permission checks expressed with normalized numbers rather than the intended algebra.

A sensitive context can be perfectly appropriate inside a highly private local path. Conversely, low-sensitivity material may still be involved in a high-risk destructive or externally publishing Operation. The algebra evaluates the whole concrete crossing/lifecycle rather than asking whether one actor has a static clearance for one classification.

Examples used to reason about the model include:

- medical information: high sensitivity;
- known user secrets: among the highest sensitivity;
- encapsulated local UX/local inference: stronger privacy/lower exposure than broad remote or Internet-facing paths;
- executable artifacts such as Bash/shell scripts: high risk because they can become directly effectful;
- publishing, destructive or irreversible Operations: high consequence/risk even when their input is not very sensitive.

These examples are specification probes, not a frozen lookup table.

## 3. Carried security state

A request/work lifecycle carries immutable security/boundary contributions accumulated so far.

Conceptually:

```text
originating facts
    + material sensitivity/privacy facts
    + Module/Agent/Operation boundary facts
    + mechanism boundary/exposure/risk facts
    + derived-representation facts
    -> carried security state
```

Every governed crossing adds the facts introduced by that concrete crossing. Earlier facts remain part of the lifecycle history.

This is the meaning of additive/compositional boundary algebra in MADRE: **accumulation of relevant boundary consequences**, not arithmetic addition and not reconstruction from an external authority database.

A registry update cannot rewrite the carried security history of already accepted work. Restart/retry continue from that history and add only facts from newly occurring crossings.

## 4. Security envelope / integrity representation

The current implementation uses immutable `SecurityEnvelope` objects plus a carried `SecurityContext` and integrity digests. That representation is useful implementation evidence, especially for continuity/provenance and restart-safe carried state.

However, the exact universal field layout of `SecurityEnvelope` is not itself a frozen product requirement. A future schema may simplify which contributor kinds carry which normalized facts, provided it preserves the small compositional model and deterministic lifecycle evaluation.

For material, digest/integrity continuity allows Kernel to verify that later-resolved material is the material whose security facts were already carried.

Kernel does not infer semantic redaction/minimization policy from private content. Modules own domain-specific classification, minimization and semantic projection when constructing material and boundary metadata.

## 5. Admissibility relation: open architecture, current implementation not authority

The canonical architecture intentionally does **not** freeze a final formula yet.

The required property is:

```text
carried material/context sensitivity
    + concrete privacy/provenance/boundary facts
    + concrete capability/Operation consequence/risk
    + previous lifecycle crossings
    -> simple deterministic admissibility result
```

The exact relation must be derived from real MADRE cases and remain understandable enough that normalized values reduce complexity rather than create another policy language.

The current runtime implementation reduces envelopes with:

```text
effective sensitivity = max(...)
effective trust       = min(...)
effective risk        = max(...)
```

and admits with:

```text
trust >= sensitivity
and
trust >= risk
```

That is **current implementation behavior only** and is now an explicit Kernel convergence gap. It must not be treated as frozen Owner semantics or used as the template for future schema design. Preserve useful carried-state/integrity behavior while re-deriving the minimum correct algebra rather than replacing it with conventional clearance matching.

## 6. Registry, identity and references grant nothing

Registration means existence/discovery/routing only.

```text
registered == known/discoverable
registered != trusted
registered != permitted
registered != authorized
```

Module identity, descriptor identity, Work IDs, material references, retrieval claims, correlation values, idempotency keys, installation acceptance, bearer tokens, roles, allowlists and prior decisions are not permission sources.

Changing descriptor/registry facts can affect the boundary facts of a **future crossing that has not happened yet**. It cannot retroactively change the carried history of an existing request/work lifecycle.

The durable-material retrieval claim is opaque coordination only; possession of it grants no MADRE authority.

## 7. Bounded Operations and mechanisms

MADRE-provided AI surfaces do not give Agents unrestricted shell or Internet authority.

System/network/external effects are exposed through specific bounded Module Operations or concrete mechanisms with explicit boundary/risk properties. This boundedness is a deliberate part of the security design: the algebra evaluates concrete effects rather than trying to secure an arbitrary omnipotent AI process.

A user may install custom Modules/adapters with different behavior, but those integrations still cross explicit MADRE boundaries and contribute their declared risk/privacy facts.

Unknown external Operation effect after an uncertain dispatch must not be blindly retried.

## 8. Kernel payload opacity and generated material

Kernel does not read prompts or outputs to derive semantic meaning, policy or permission. It handles explicit metadata/security facts, digests/references, physical mechanism state and transient bytes needed for execution/transfer.

Generated output therefore cannot rewrite Kernel security by making a semantic assertion.

Once delivered to a Module, however, generated output is Module-owned material. A Module/Agent may intentionally use it as later input, evidence, WorkPlan state, persisted domain material, or the basis for a bounded Operation. MADRE security constrains the actual subsequent crossings/effects; it does not forbid agentic chaining by declaring generated material universally non-authoritative.

## 9. Provider credentials and deployment boundaries

MADRE's current local single-owner installation does not add separate Module authentication/authorization infrastructure.

HTTP/IPC/SDK transport mechanics are not security authority for the algebra.

Provider API keys, OAuth/login/session flows and similar mechanism-specific credentials belong to the concrete adapter/external software. They access that provider/mechanism; they do not authorize Module work inside MADRE.

If a future deployment introduces a real adversarial multi-user or remote trust boundary, that requirement must be designed explicitly rather than pre-installed as generic security boilerplate.

Future deterministic AI-specific signals may be explored only when MADRE has a concrete design for them. They must not silently redefine today's boundary/provenance concepts.

## 10. Evidence

Kernel should persist enough normalized carried state, integrity/provenance continuity and concrete crossing/execution evidence to explain deterministic security decisions without persisting private prompt/context/output content.

The exact evidence schema should follow the final minimal algebra rather than preserving redundant fields solely because the current implementation already stores them.
