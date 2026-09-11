# Implementation Baseline

This document describes current repository state only. It is not product authority.

Product meaning comes from `MADRE.md`; detailed architecture comes from `docs/architecture/`. Design rationale lives under `docs/design-memory/`.

## Current development stage

The Kernel and modular SDK foundation use the scoped MADRE-native Security Algebra.
The focused architecture owner defines meaning; current code defines executable scope.

`SecurityValues` exposes role-local optional facets. SecurityObject binds declared
Sensitivity sources. EffectProfiles pair Risk/Autonomy; explicit executors supply
execution Integrity. UserRelease carries an exact disclosure/effect route and user
interaction revision. SDK selection retains exact members; semantic transforms and
validation bind a procedure revision. Generated inference output establishes no
Integrity warrant. Broad validation of this replacement remains outstanding. Narrow local verification passed 67 focused algebra, SDK, broker, execution-binding and selected durable/retry cases, plus type checks on the five changed algebra/material/runtime source owners. This is controlled execution evidence, not live-provider acceptance.

### Kernel execution and brokering

The Kernel preserves the existing execution behavior while using the scoped algebra:

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

Broker Agent input constructs the actual disclosure path through target Module, Agent, and endpoint. Operation input constructs disclosure through target Module/endpoint, CONTROL from the active selector, and additional declared semantic controllers, and EFFECT_EXECUTION through the target Module/endpoint. The selecting Agent contributes its own Integrity; agentless or Operation-owned selection contributes its executing Module. Owning Modules and forwarding endpoints are not automatically additional selectors. A material-disclosing EffectProfile contributes to its actual disclosure path.

Return disclosure uses the captured requester Module and active requesting Agent. In the implemented in-process SDK route, the forwarding endpoint does not receive the nested return payload and is not inserted as a return recipient. New endpoint/transport implementations must represent any additional actual recipients when such a route exists; this correction adds no transport.

Endpoint attachment captures and validates the exact Module publication and endpoint SecurityObject. Dispatch checks both against that captured attachment, including same-version publication replacement, and retains the snapshot through completion. Endpoint SecurityObjects are not added to ModuleManifest.

Registry identity supplies discovery and routing facts only. Registration, Module/Agent identity, credentials, references, prior successful execution, Work IDs, SecurityIDs, and CORE identity are not algebra operands or alternate authority sources.

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
result digest/size/optional Integrity and source/producer SecurityIDs
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
- transient inference and durable result reuse with production provenance;
- Agent/Operation brokering with carried history continuity.

`Module.execute_agent()` and `Module.execute_operation()` establish the active causal identity and supply behavior with per-execution `ExecutionServices`. Host-side `ModuleServices` contains inert client configuration; it is not injected into behavior. Agent/Operation brokering, transient inference, durable submission, and CORE delegation use bound clients with no caller-selected `invocation=` argument. Replacing supplied history cannot narrow the binding. Handles expire when execution returns, fails, or is cancelled, and cannot be rebound as another execution's configuration. Concurrent executions receive independent bindings. Agentless Module execution remains supported. `InvocationContext` remains explicit evidence for derivation and checked dispatch; no ambient execution state, permission system, or durable invocation identity was introduced. This is an SDK contract boundary, not isolation from arbitrary Python access to private infrastructure or host-owned raw transports.

`Operation` exposes immutable EffectProfiles rather than direct caller-overridable security numbers. `OperationBrokerClient` selects a profile identity using its established execution binding. The known active selector is automatically represented as a controller; control-relevant material is declared explicitly. Additional semantic controllers may be supplied explicitly when Module semantics require them.

The SDK still does not define universal Agent sessions/memory, a Planner, a Workflow engine, Skill instances, Task ontology, a security-policy DSL, generic credential framework, shell, or unrestricted Internet interface.

### Shipped default CORE

`madre_core` remains an ordinary replaceable Module implemented only through `madre_sdk`.

The shipped CORE Module and interaction Agent declare level-5 Privacy and Integrity through the same public participant-security API as any Module. CORE-owned interaction/context/output material may retain level-5 Sensitivity independently. Its intermediate and output representations carry ordinary derivation evidence through the actual CORE/inference path without automatic Integrity.

CORE retains its existing behavior only: immediate transient interaction, optional explicit Agent delegation, and optional durable continuation. It receives no Kernel/security bypass and no richer UX/memory/routing functionality was added during this migration.

### Architecture boundaries

Tests enforce that:

- `madre_sdk` imports only the public Kernel contract namespaces listed above;
- `madre_core` imports no `madre.*` namespace directly;
- Kernel imports neither `madre_sdk` nor `madre_core`;
- CORE selection remains ordinary configuration;
- brokered security history propagates through nested CORE inference/delegation/durable work;
- legacy Trust/Isolation/IntendedUse/CompatibilitySecurityEvaluator symbols are absent from the shipped security implementation.

## Validation handoff

Run from this checkout with its `src` on PYTHONPATH:

```text
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```

Old validation counts predate the algebra replacement and do not establish current
acceptance. Narrow replacement evidence is reported by the implementing task.

## Structurally outside Kernel

Kernel still does not own Agent private reasoning/state/memory, semantic WorkPlans, Workflow/Skill execution, conversation state, interaction strategy, semantic fallback routing, prompt construction, user profiles, generated-result meaning, material classification/validation, domain mutations, third-party valuation, identity/authentication architecture, ACLs, policy DSLs, generic shell, or unrestricted Internet authority.

The next product stage is intentionally not selected by this migration. Further CORE, provider, valuation, developer-tooling, or agent-ontology work remains separate Owner-directed work.
