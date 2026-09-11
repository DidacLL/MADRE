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
Integrity warrant.

The next active stage is **an assembled, installable local MADRE usage path**.
Complete that stage before expanding the algebra, Agent ontology or provider set.
The product contract remains `MADRE.md`; the criteria below apply existing product
responsibilities to the first local deployment, rather than defining new features.

### Confirmed assembly gaps

- `madre.cli` launches `service.create_app`, which constructs storage, registry,
  Capability registry and WorkRuntime. It does not assemble Broker, attach Module
  endpoints/resolvers, instantiate CORE or provide CORE interaction.
- HTTP manifest registration publishes descriptors; it does not make a Module's
  private execution or material resolver available. Durable submission without a
  resolver can end in `material_unavailable`.
- SDK `MaterialRepository` is in-memory. Work metadata survives restart, but a
  usable restart journey also needs Module-owned material retention/reconstruction
  and resolver reattachment. Never solve this by persisting private payloads in Kernel.
- CORE currently assigns S5 to every interaction context/result. The generic local
  example now declares P3, so this is not an immediately usable CORE route. Establish
  exact bounded source sensitivity and real participation containment in the next
  slice; do not fabricate stronger Privacy or silently lower sensitive material.
- A built wheel and deterministic tests establish foundation consistency, not
  installed real-inference or Owner-machine acceptance.

### Next coherent behavior

Provide a supported local composition/launch path that wires the existing Kernel,
Broker, shipped CORE and a small independent SDK Module into a real user operation.
Prefer the simplest composition supported by the existing interfaces. Separate
process transport is not a prerequisite unless the concrete integration requires it.
Keep composition in the host/application layer; Kernel must not import CORE/SDK or
special-case CORE identity. A small CLI is sufficient to exercise initial interaction;
no dashboard, hosted service, universal Agent engine or installer framework is implied.

Use an actual configured local inference mechanism and correctly scoped material.
Expose enough configuration and failure reporting for an owner to reproduce the run.
A missing model, unavailable resolver or denied disclosure must remain an accurate
failure, never a canned answer or inflated security declaration.

### First local deployment completion criteria

1. A clean environment installs the built distribution and starts the supported
   local entrypoint using documented configuration, without checkout-private imports.
2. An ordinary user interaction reaches shipped CORE through the public SDK/broker
   path, performs real local inference and returns a result or accurate failure.
3. An independent SDK Module can use the same shared runtime directly and exercise
   one real bounded Module-owned Operation through its immutable EffectProfile.
4. Delayed work survives process restart with Module-owned material reacquisition,
   reattached resolver and inspectable execution/result-delivery evidence. Results
   the Module needs to retain are persisted by that Module. Kernel result loss keeps
   its documented semantics.
5. The actual route demonstrates admissible execution plus a meaningful denial,
   with exact release/derivation handling when required by that route. CORE has no bypass.
6. Setup, start, stop, restart and one complete usage journey are reproducible from
   README on the Owner's local target. Run relevant regression and package checks;
   record real-machine evidence separately from fixtures and GitHub checks.

Implement successive coherent behaviors toward those criteria. Do not call a wheel,
mock-only demonstration or green CI a deployed first version. Remote providers,
elaborate UI, planner/memory frameworks and broader developer tooling are subsequent
work unless a concrete acceptance path requires them. Public release publication,
default-branch integration and changes to the Owner's installed environment follow
explicit Owner authority; preparing reviewable artifacts does not require a new ceremony.

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

## Validation evidence and commands

The Owner supplied the follow-up validation report for `a7cf5b7`:
88 tests passed; mypy passed for 26 source files; Ruff lint and formatting passed;
sdist and wheel built after locked environment synchronization. Regression additions
cover release/profile binding, durable release evidence, retry-projection invariance,
nested Sensitivity closure, correlation transforms, paired profiles, independent
disclosures and public SDK construction. No production-code correction was needed
in that follow-up. This report establishes deterministic/build validation, not live
provider or installed local product acceptance. The development-readiness cleanup
verified the published head and entrypoint gaps without rerunning that full suite.

Use these commands from the current checkout when implementation changes justify them:

```text
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```

Keep validation proportional to each slice; run full regression/package checks at
integration/release boundaries. Do not repeatedly spend local execution time on
unchanged evidence. GitHub agents can handle repository edits and deterministic
checks; actual local inference, process recovery and installed usability require
execution on an appropriate machine.

## Structurally outside Kernel

Kernel still does not own Agent private reasoning/state/memory, semantic WorkPlans, Workflow/Skill execution, conversation state, interaction strategy, semantic fallback routing, prompt construction, user profiles, generated-result meaning, material classification/validation, domain mutations, third-party valuation, identity/authentication architecture, ACLs, policy DSLs, generic shell, or unrestricted Internet authority.

The active next stage is the local assembly and deployment path above. Preserve Module/Kernel ownership while choosing concrete implementation mechanics from working evidence.
