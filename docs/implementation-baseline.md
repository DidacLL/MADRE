# Implementation Baseline

This document describes current repository state only. It is not product authority.

Product meaning comes from `MADRE.md`; detailed architecture comes from `docs/architecture/`. Design rationale lives under `docs/design-memory/`.

## Current development stage

**The Kernel foundation, modular SDK, shipped default CORE foundation, and frozen MADRE Security Algebra are implemented together end to end.**

The canonical security contract is `docs/architecture/MADRE-security-algebra.md`. The Python runtime now executes that five-concept, transition-local model directly; the pre-freeze compatibility evaluator and its Trust/Isolation/IntendedUse schema are gone.

Third-party/default security valuation remains intentionally outside the current implementation stage. Runtime roles require complete bound values and fail structurally when they are absent.

## Implemented repository truth

### Final Security Algebra

The `madre.security` public contract now provides immutable, deterministically bound security facts around exactly:

```text
Sensitivity
Privacy
Integrity
Risk
Autonomy
```

Subject-specific values are:

```text
Artifact / ContextBundle -> Sensitivity + Integrity
Module / Agent / endpoint -> Privacy + Integrity
Capability -> Privacy + Integrity
Operation EffectProfile -> Risk + Autonomy + Integrity + optional Privacy
```

`SYSTEM_RESERVED = 0` is not an ordinary value. Ordinary values are levels 1 through 5 only.

A `SecurityObject` is bound to a globally unambiguous `SecuritySubjectRef` containing owning Module identity, subject kind, publication revision, subject-local identity, subject/representation revision, and parent Operation identity where required. Canonical binding evidence is included in the deterministic SecurityID. The same immutable binding produces the same SecurityID across restart; changed representation/profile/evidence produces another SecurityID; conflicting contents for one SecurityID fail structurally. SecurityID possession grants no authority.

Operation security is represented by immutable Operation-owned `EffectProfile`s. A concrete invocation selects an existing profile identity. Callers cannot submit ad-hoc Risk, Autonomy, Integrity, or Privacy values.

`SecurityTransition` carries explicit prospective DISCLOSURE, CONTROL, and EFFECT_EXECUTION relationships. `SecurityDerivation` carries explicit DERIVATION relationships for immutable representation transformations. Kernel constructs relationships that are objectively known from public protocol topology and never interprets prompt/output meaning.

`SecurityHistory` is immutable/idempotent keyed composition of SecurityObjects, accepted transitions, and derivations. Duplicate identical identities collapse; conflicting identities remain detectable structural errors. Historical objects are evidence and continuity state, not a global min/max reduction domain.

The executable evaluator is `SecurityAlgebra`. It applies exactly:

```text
Sensitivity(material) <= min(Privacy(actual disclosure path))
min(Risk(effect), Autonomy(effect)) <= min(Integrity(actual non-user controllers))
Risk(effect) <= min(Integrity(actual effect executors))
```

The selected EffectProfile contributes its own execution Integrity. With no non-user controller, controller assurance is neutral level 5. Non-effectful transitions do not acquire control/effect predicates.

Decision evidence is deterministic and transition-local: algebra version, transition identity, structural failure categories, disclosure material/path/privacy/limiting SecurityIDs, and effect Risk/Autonomy/control-demand/controller/executor Integrity evidence. Evidence contains no protected payload.

### Derivation and generated material

Security-relevant transformation creates a new material representation and SecurityID.

SDK ordinary derivation requires an explicit `InvocationContext` and cannot increase Integrity beyond its sources, known active production path, and additional Module-declared producers. `Artifact.validated()` derives validators from that context; it no longer accepts an arbitrary list of historical validator identities. A validation derivation may create a stronger-Integrity representation only up to that actual validator-path Integrity. Sensitivity may be lowered only on a new Module-classified representation; Kernel does not inspect content to decide semantic minimization correctness.

Transient and durable inference results expose output Integrity plus source/producer SecurityIDs. SDK helpers turn consumed model/work output into a new Artifact with ordinary DERIVATION evidence instead of silently relabeling generated content.

Brokered endpoints returning a new representation must preserve a derivation chain back to the invoked input and include the actual producing Module/Agent/endpoint in its derivation. A true input pass-through or previously completed nested result retains its immutable production facts. Merely finding an unrelated output SecurityID in input history does not establish pass-through.

New validation claims are checked against the producing invocation at broker completion. The stable derivation identities and exact completion context are recorded separately in broker evidence; ephemeral invocation IDs do not enter SecurityDerivation identity. Completed nested validation is reusable for its immutable output, including after database reopen. Borrowing its validators for another output requires another actual validation completion. Broker input and inference/work admission reject validation claims without completed participation evidence. Consequently a freshly constructed validation result must complete its brokered invocation before reuse through another Kernel boundary; this is a deliberately restricted helper, not a validation subsystem. Semantic validation correctness remains Module-owned.

Each immutable output has one authoritative derivation; duplicate identical relations collapse, competing relations and indirect cycles fail. Persistence checks the union with already stored ancestry so separate completions cannot fork or cycle the same immutable graph. Unrelated ordinary Module-private ancestry is preserved without attributing its production to the current actor.

### Kernel execution and brokering

The Kernel preserves the existing execution behavior while using the final algebra:

- reference-only durable `WorkSubmission -> WorkRecord -> WorkAttempt`;
- Module-owned JIT material resolution and digest/security continuity checking;
- restart/retry/cancellation and delayed eligibility;
- originator fairness plus priority/FIFO ordering;
- heavyweight local-resource admission;
- generic transient inference;
- hard requirements, preferences, fallback, provider/model/mechanism separation;
- one OpenAI-compatible adapter;
- explicit owning-Module + local exported-id Agent/Operation routing;
- unknown external-effect handling and transient result/loss evidence.

Capability selection evaluates each candidate with a prospective DISCLOSURE transition before private material is resolved. A rejected candidate is retained only in decision evidence; it is not recorded as an accepted disclosure. The selected candidate transition is added to durable history only when the concrete attempt is ready to execute.

An immutable `InvocationContext` explicitly carries the exact active Module, Agent or Operation, and endpoint binding through broker, endpoint, SDK behavior, and nested-client calls. It remains separate from SecurityHistory; replacing optional carried history cannot remove the active caller. There is no ambient execution state. The context carries identity facts, not authentication or permission.

Broker Agent input constructs the actual disclosure path through target Module, Agent, and endpoint. Operation input constructs disclosure through target Module/endpoint, CONTROL from the concrete input material, the active selector, and additional declared semantic controllers, and EFFECT_EXECUTION through the target Module/endpoint plus the selected EffectProfile. The selecting Agent contributes its own Integrity; agentless or Operation-owned selection contributes its executing Module. Owning Modules and forwarding endpoints are not automatically additional selectors. A material-disclosing EffectProfile contributes to its actual disclosure path.

Return disclosure uses the captured requester Module and active requesting Agent. In the implemented in-process SDK route, the forwarding endpoint does not receive the nested return payload and is not inserted as a return recipient. New endpoint/transport implementations must represent any additional actual recipients when such a route exists; this correction adds no transport.

Endpoint attachment captures and validates the exact Module publication and endpoint SecurityObject. Dispatch checks both against that captured attachment, including same-version publication replacement, and retains the snapshot through completion. Endpoint SecurityObjects are not added to ModuleManifest.

Registry identity supplies discovery and routing facts only. Registration, Module/Agent identity, credentials, references, prior successful execution, Work IDs, SecurityIDs, and CORE identity are not algebra operands or alternate authority sources.

### Canonical relation identity and binding evidence

Set-like controller, executor, source, producer, validator and disclosure-edge collections are deduplicated and sorted before identity generation and on deserialization. Deliberately ordered disclosure paths retain their order. Realized histories must satisfy all three predicates under their immutable operands; historical numeric failures are no longer tolerated. Rejected prospective transitions remain decision evidence, and persistence refuses inadmissible realized histories.

BindingEvidence uses a bounded set of structural keys, at most 16 entries and at most 256 characters per value, with canonical unique keys and constrained values. Capability construction rejects URL user-info, query and fragment components. It records scheme, normalized host, effective port and a path digest, not the raw endpoint or path. Provider credentials remain outside SecurityObject identity.

SecurityID cross-language encoding is deliberately deferred. The current Python encoding is `json.dumps(value, sort_keys=True, separators=(",", ":"), default=int)` with default ASCII escaping, encoded as UTF-8 and hashed with SHA-256. Identity schemas use strings, integer levels, arrays, objects and null; no floating-point identity policy is introduced. The executable `module.vector` fixture has SecurityID `security:v1:f321e83ef5f843f18903e22100a8fe397a27e5f978d509c4b021633fe92abb2f`. A language-neutral canonical encoding specification, including Unicode ordering/escaping, remains cross-language debt rather than a claimed interoperability guarantee.

### Persistence and recovery

Generated development SQLite state is recreated when the schema format changes; no production migration layer is retained for superseded development state.

Persistence now stores:

```text
immutable SecurityObjects
accepted SecurityTransitions
SecurityDerivations
transition-local decision evidence
SecurityHistory carried by durable work
attempt transition identity
result digest/size/Integrity and source/producer SecurityIDs
```

It does not store prompt/context/output payload bytes or arbitrary provider-error text. Retries restore the same immutable history. Identical operands reproduce the same transition identity and deterministic decision evidence; selecting a different Capability creates a different prospective transition.

### Modular SDK

`madre_sdk` remains restricted to public `madre.contracts`, `madre.interfaces`, `madre.registry`, and `madre.security` imports.

It now provides typed helpers for:

- participant Privacy/Integrity SecurityObjects;
- deterministic SecurityHistory construction;
- immutable Operation EffectProfile declaration/selection;
- DISCLOSURE and effect-transition construction;
- Artifact/ContextBundle immutable binding;
- ordinary derivation and explicit validation derivation;
- transient inference and durable result reuse with production Integrity evidence;
- Agent/Operation brokering with carried history continuity.

`Operation` exposes immutable EffectProfiles rather than direct caller-overridable security numbers. `OperationBrokerClient` selects a profile identity and requires the active invocation context on each call. Concrete Operation input and the known active selector are automatically represented as controllers. Additional semantic controllers may be supplied explicitly when Module semantics require them.

The SDK still does not define universal Agent sessions/memory, a Planner, a Workflow engine, Skill instances, Task ontology, a security-policy DSL, generic credential framework, shell, or unrestricted Internet interface.

### Shipped default CORE

`madre_core` remains an ordinary replaceable Module implemented only through `madre_sdk`.

The shipped CORE Module and interaction Agent declare level-5 Privacy and Integrity through the same public participant-security API as any Module. CORE-owned interaction/context/output material may retain level-5 Sensitivity independently. Its intermediate and output representations carry ordinary derivation evidence through the actual CORE/inference path.

CORE retains its existing behavior only: immediate transient interaction, optional explicit Agent delegation, and optional durable continuation. It receives no Kernel/security bypass and no richer UX/memory/routing functionality was added during this migration.

### Architecture boundaries

Tests enforce that:

- `madre_sdk` imports only the public Kernel contract namespaces listed above;
- `madre_core` imports no `madre.*` namespace directly;
- Kernel imports neither `madre_sdk` nor `madre_core`;
- CORE selection remains ordinary configuration;
- brokered security history propagates through nested CORE inference/delegation/durable work;
- legacy Trust/Isolation/IntendedUse/CompatibilitySecurityEvaluator symbols are absent from the shipped security implementation.

## Security validation coverage

The deterministic suite covers the required final-algebra cases, including maximum-sensitivity local CORE disclosure, lower-Privacy rejection, actual-path locality, rejected candidate history isolation, minimization, immutable source history, low-Integrity effect control, display-only non-control, direct-user control demand, executor Integrity independence, low-risk autonomy, explicit validation, no ordinary Integrity laundering, publishing confidentiality, SecurityHistory idempotence, SecurityID conflicts, Module-local name collisions, restart/retry determinism, different-Capability transitions, missing role values, level 0 rejection, absence of alternate authority, SQLite private-material exclusion, SDK/CORE boundaries, and existing scheduling/runtime behavior.

Numeric tests exhaustively enumerate ordinary levels 1..5 for disclosure, all 5^3 Risk/Autonomy/controller-Integrity control combinations, and all Risk/executor-Integrity combinations, including the required monotonicity properties.

The causal-topology correction was validated locally on Windows with **253 passing tests**: `test_causal_topology.py`, `test_security_algebra.py`, `test_registry_broker.py`, `test_sdk_core.py`, `test_architecture_boundaries.py`, and the five runtime cases covering rejected-candidate locality, denial before JIT resolution, durable metadata/private-byte exclusion, same-operand retry identity, and changed-capability retry identity. Mypy passed for all 26 source files. Ruff lint and formatting checks cover the changed Python surface. These are deterministic broker/SDK/SQLite and controlled capability tests, not live-provider or cross-platform acceptance. Final constructor/caller inspection covered all transition, role, derivation and history construction sites.

Broad CI, package/reinstall validation and integration were deliberately left to follow-up work. The existing broader CI recipe remains:

```text
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
wheel reinstall + isolated madre/madre_sdk/madre_core imports
```

## Structurally outside Kernel

Kernel still does not own Agent private reasoning/state/memory, semantic WorkPlans, Workflow/Skill execution, conversation state, interaction strategy, semantic fallback routing, prompt construction, user profiles, generated-result meaning, material classification/validation, domain mutations, third-party valuation, identity/authentication architecture, ACLs, policy DSLs, generic shell, or unrestricted Internet authority.

The next product stage is intentionally not selected by this migration. Further CORE, provider, valuation, developer-tooling, or agent-ontology work remains separate Owner-directed work.
