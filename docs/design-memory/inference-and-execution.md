# Inference and Execution Design Memory

Authority: non-normative design memory. Canonical execution semantics remain in `docs/architecture/MADRE-execution-contract.md`.

## Capability means physical mechanism

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

A Capability is an available physical inference/execution mechanism with known properties. It is not a semantic statement that an Agent is capable of a task.

One provider can expose multiple mechanisms: direct API, account-authenticated CLI/session, SDK, MCP path, local gateway/bridge, externally supplied credentials, or an unconventional user-installed adapter over software the user controls. Provider identity must not be flattened into one connection model.

The current OpenAI-compatible HTTP adapter is one implemented mechanism, not provider architecture.

## Modules request properties, Kernel selects installed mechanisms

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

Modules generally should not need to know the installed model/provider inventory. They express execution requirements/preferences such as:

- modality/specialization;
- latency class;
- reasoning effort/quality;
- cost policy (forbid paid / prefer free / paid allowed, etc.);
- locality/privacy;
- resource/availability constraints;
- optional provider/model/mechanism preference;
- fallback permission/ordering.

Kernel deterministically matches those requests against installed physical mechanisms and current resource state.

An explicit preference such as "use Claude" is normally a preference/fallback concern unless the Module says the exact mechanism/model is semantically required.

Cost should preferably be made visible by the Module/UI rather than awkwardly negotiated through conversational permission text.

## Local-first does not mean local-only

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

Local inference is the primary product focus because MADRE targets personal machines and privacy/resource control. Admissible remote/provider mechanisms remain valid first-class options. Local/cloud is only one execution dimension among modality, specialization, cost, latency, privacy and others.

## Durable material stays Module-owned

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

All durable accepted/queued work is reference-only from Kernel's perspective.

Durable state contains only a verifiable material handle:

```text
reference
expected digest
immutable material security binding/envelope
simple opaque retrieval claim/coordination value
```

The retrieval claim is not authorization, trust, identity or a permission token. It exists only so the Module can resolve the exact prepared material later without ambiguous/random lookup.

Kernel should establish eligibility, compatible mechanism/boundary facts and resource readiness where practical before requesting the payload. Actual material is acquired just-in-time for the concrete execution attempt, verified, used transiently and discarded. Restart/retry reacquire it from the Module.

No durable prompt cache, encrypted prompt vault or queued in-memory payload cache belongs in Kernel.

## Transient interaction is intentionally different

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

Fast transient inference may directly carry minimal ephemeral input because it is not accepted/queued durable work and has no restart/recovery promise.

Do not force the privacy/recovery mechanics of durable work onto the fast interaction path.

## Generated outputs are Module material, not forbidden state

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

A Module/Agent may freely use generated output as the next inference input, store it in Module-owned state, treat it as evidence, incorporate it into a WorkPlan, or let it influence a later bounded Operation according to the Module's own semantics.

Kernel does not interpret prompt/output content. It only handles the explicit execution/security metadata and transient bytes needed for the concrete crossing. There is therefore no need for a generic Kernel rule saying generated output "has no authority"; the relevant invariant is that payload text cannot rewrite Kernel boundary/security state by semantic assertion.

## Provider access remains adapter-specific

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

Provider API keys, OAuth/login, vendor sessions, CLI authentication, SDK credentials and similar mechanics stay inside the concrete adapter or external provider software. MADRE Kernel should not become a generic credential framework merely because a mechanism requires credentials.

Flexibility should not structurally forbid unusual user-created adapters. The user/developer is responsible for the software/account implications of an adapter they install; MADRE evaluates the declared execution boundary and risk rather than assuming only vendor-standard access paths exist.
