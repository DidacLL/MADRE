# MADRE Kernel closure handoff

Status: **operational handoff for work after Lane C closure**.

This document is not a new product authority and does not redefine Lane A, Lane B, the semantic SDK, Runtime, SPIRA, CORE or Module semantics. It records the qualified physical Kernel baseline, the boundary that later work may rely on, the concrete defects found during closure verification, and the remaining work that belongs above or beside Kernel.

When this document conflicts with a current Owner instruction or a higher authority in `AGENTS.md`, the higher authority wins. For Kernel architecture itself, `docs/architecture/kernel.md` remains the current architecture definition.

## 1. Closure identity and evidence

Lane C physical Kernel implementation was qualified at executable head:

```text
95ddf2250c27d28e90e215391223164965b68729
```

The subsequent closure-policy commit removed obsolete automatic Lane C CI without changing Kernel executable behavior:

```text
bacdb37eef38af90fe7b5c380bf69dacbd320872
```

Full requalification run:

```text
GitHub Actions run 36493627377
seed 12648430
scale medium
soak 60 seconds
```

The matrix passed all eight jobs:

```text
Ubuntu  regression     PASS
Ubuntu  qualification  PASS
Ubuntu  stress         PASS
Ubuntu  soak           PASS
Windows regression     PASS
Windows qualification  PASS
Windows stress         PASS
Windows soak           PASS
```

That evidence supports the current implementation. It does not elevate tests or CI above Owner intent, and it does not make future Kernel defects impossible.

## 2. What later lanes may rely on

The current Kernel is MADRE's shared **physical inference substrate**. Later semantic work may rely on these responsibilities being available without reimplementing them above the boundary:

- durable `PhysicalInferenceWork` identity and lifecycle;
- eligibility, urgency and deadline-aware physical scheduling;
- configured `InferenceCapability` facts separate from current availability and historical observations;
- hard effort and local/external admissibility;
- capability selection using availability, Owner preference and binding/version-scoped successful latency evidence where the current DRE consumes it;
- durable attempts, typed physical failure, retained result, cancellation and release;
- restart recovery that converts interrupted running execution to `UnknownCompletion` instead of silently replaying it;
- one physical process owner per SQLite database, including Linux path/symlink aliases covered by the ownership fix;
- bounded physical concurrency;
- supervised scheduler/probe/execution persistence so authoritative background failure terminates the unhealthy host rather than fabricating health;
- open physical execution through `IInferenceBinding`;
- provided process and MEAI bindings without provider/runtime ownership by Kernel;
- versioned bounded local IPC over Unix-domain sockets on Windows/Linux;
- Java 21 `madre-kernel-client` as the physical client surface;
- strict IPC/config/CLI boundary parsing;
- manual Linux/Windows regression, qualification, stress and soak verification through `.github/workflows/kernel-verification.yml`.

These are physical guarantees only. They do not imply semantic success, answer quality, application completion or semantic continuation.

## 3. Hard semantic / physical boundary

Future semantic lanes must preserve this direction:

```text
Module / Agent / semantic MADRE
        ↓
ReasoningRequest + actual semantic context
        ↓
semantic derivation of a small physical inference requirement
        ↓
madre-kernel-client
================ HARD BOUNDARY ================
        ↓
PhysicalInferenceWork
InferenceCapability truth/evidence
physical DRE
attempt / result / recovery
        ↓
IInferenceBinding
        ↓
Owner-selected independent inference environment
```

Kernel does **not** own or understand:

- Module/application domain meaning;
- Agent semantics or semantic continuation;
- Operation meaning;
- Material meaning;
- ReasoningRequest semantics;
- SPIRA as a semantic policy/evaluator object;
- CORE semantics;
- MADRE Skill, Workflow or WorkPlan semantics;
- Runtime semantic persistence/correlation;
- application-level answer quality;
- provider/model/runtime internal lifecycle;
- model loading, warmness or generic RAM/VRAM ownership.

Agents must not construct Kernel `PhysicalInferenceWork` directly as a public semantic programming model. Semantic MADRE derives the bounded physical requirement. The exact public carrier and Runtime journey remain work above Kernel.

## 4. What Kernel intentionally does not freeze

Closure does not mean every future physical strategy is prohibited. It means no additional architecture is justified without an actual need.

The current baseline deliberately leaves these extension points open:

- additional typed physical requirement fields when an implemented DRE decision actually consumes them;
- additional capability facts/observations when a real physical selector or strategy consumes them;
- additional `IInferenceBinding` implementations for Owner-selected inference mechanisms;
- future physical multi-stage strategies when a concrete product strategy requires them;
- future use of MAF, another workflow mechanism or direct code if an actual physical strategy earns that dependency;
- future network/provider bindings when a real integration needs them.

Do not turn current absence into permanent prohibition. Conversely, do not add speculative registries, checkpoints, generic plugin systems, connector marketplaces, provider ontologies or compatibility machinery in anticipation of possible future work.

## 5. Concrete defects discovered during closure

Closure verification found two real production defects worth preserving as architectural lessons.

### 5.1 Physical database ownership aliases

A database path could previously acquire distinct ownership identities through Linux filesystem aliases/symlinks. That could undermine the one-Kernel-per-authoritative-database guarantee.

The fix canonicalizes the physical ownership path before acquiring the database lease. Future work must preserve **physical database ownership identity**, not merely textual path identity.

Do not replace this with socket ownership: IPC endpoint identity and SQLite authority are separate concerns.

### 5.2 Urgency across slot-release races

A fast attempt could release a physical slot while the scheduler was still traversing one stale eligible ordering. The newly released slot could then be reused later in that same pass, allowing a lower-urgency candidate to consume capacity that should have been reconsidered under a fresh ordering.

The fix snapshots dispatch capacity for a scheduler pass and decrements that budget only after successful claim. A completion wakes a later scheduler pass rather than extending the stale pass.

Future scheduler changes must preserve urgency at physical dispatch boundaries, including races where capacity changes while an eligible set is being traversed.

## 6. Verification lessons that are not architecture

Many failures encountered while building the closure suite were verifier/harness defects rather than Kernel product defects: cancellation-insensitive fixtures, Unicode console assumptions, observer-induced SQLite pressure, Unix-domain-socket trailing-byte behavior, file-write races, timing assumptions and cleanup paths that could mask the actual assertion.

Do not turn those harness fixes into product architecture. The reusable lesson is narrower:

> Tests must observe externally meaningful physical behavior and must not create their own deadlocks, timing ownership or false physical semantics.

The verification project may use controlled bindings, deterministic clocks, fixture processes, raw IPC peers and deliberate SQLite failure injection. Production Kernel must not gain test-only architectural hooks merely to make tests convenient.

## 7. Backlog for the next semantic/runtime lanes

This backlog describes work that can proceed **without reopening Kernel**. It does not assign or redefine Lane A/B labels; the current Owner task for a lane determines its exact scope.

### Public semantic SDK

The public SDK still needs a small explicit construction vocabulary for ordinary, independent and eventually AI-generated Modules. Current architecture already establishes the semantic concepts and ownership rules:

- Module as independently installable application/domain boundary;
- optional Agent as semantic actor;
- Operation as bounded executable behavior;
- Skill / Workflow / WorkPlan as semantic reusable/planning concepts;
- Material/context and direct SPIRA carriers;
- semantic `ReasoningRequest`;
- public composition surfaces sufficient for first-party and independent Modules.

The SDK must not depend on Kernel implementation types or force Module authors to understand physical scheduling/provider machinery.

### Runtime Module environment

Runtime remains responsible for semantic installation mechanics:

- Module installation/configuration;
- CORE role assignment;
- live discovery/lifecycle/addressing;
- cross-Module Operation routing;
- explicit Agent-delegation routing;
- semantic inspection/diagnostics;
- persistence/correlation needed for delayed semantic reasoning;
- semantic continuation/recovery support;
- correlation of semantic requests with Kernel Work/results.

Runtime must not become a global semantic scheduler, SPIRA policy engine or owner of Module domain meaning merely because it transports calls.

### Semantic reasoning -> physical Kernel bridge

A later lane must implement the still-open semantic derivation boundary. Start from actual semantic facts and produce only the physical consequences consumed below the boundary.

The current Kernel request supports:

```text
prepared input
requested effort
urgency
eligibleAt
deadline
hard LocalOnly / ExternalAllowed execution boundary
```

Do not expose Kernel `PhysicalInferenceWork` directly as the semantic SDK request. Do not preselect an exact provider/model/configuration above Kernel when doing so removes meaningful physical DRE choice. Do not send Module/Agent/Material/SPIRA/CORE semantics into Kernel to avoid doing the semantic derivation properly.

If later semantic needs require an additional physical field, demonstrate the actual DRE consumer before extending the contract.

### CORE

CORE remains an ordinary Module assigned the CORE installation role. It may supply useful defaults and Owner interaction, but it must use the same public semantic construction surfaces as other Modules and must not become Runtime, Kernel, owner of other Modules/Agents or special SPIRA authority.

### End-to-end product proof

After SDK, Runtime and the semantic->physical bridge exist coherently, prove at least one ordinary Module path end to end:

```text
Module/Agent semantic need
→ ReasoningRequest
→ semantic physical derivation
→ Kernel Work
→ DRE capability selection/execution
→ retained physical result
→ semantic correlation/continuation
```

The proof should validate the boundary, not create hidden first-party shortcuts.

## 8. Anti-drift checks for future agents

Before changing Kernel because a later lane feels inconvenient, answer these questions:

1. Is the requested change a physical inference responsibility or semantic application/runtime responsibility?
2. Does a current product requirement actually consume the new field/abstraction?
3. Can the need be satisfied above Kernel through semantic derivation/correlation instead?
4. Would the change make Kernel know Module, Agent, Operation, Material, ReasoningRequest, SPIRA, CORE or semantic continuation?
5. Would it rematerialize provider/model/runtime internals as MADRE-owned workers/engines?
6. Would it preselect so much above Kernel that meaningful physical DRE disappears?
7. Is the proposed structure solving an observed defect or merely matching a familiar platform pattern?
8. Can the same requirement be implemented through the existing open binding seam?
9. Does the change preserve durable truth under cancellation, crash, restart and uncertain completion?
10. Is the architecture being reopened because of a concrete failing behavior, or only because a new agent would have designed it differently?

A concrete defect may justify changing settled implementation. Preference, familiarity or a greenfield redesign instinct does not.

## 9. When Kernel should be reopened

Reopen Kernel responsibility only when at least one of these exists:

- a reproducible physical correctness defect;
- a real semantic->physical requirement that the current physical contract cannot express and an implemented DRE consumer is identified;
- a real inference environment that cannot be integrated through the existing binding/capability construction without violating current physical truth;
- a measured physical scheduling/recovery requirement not representable by the current model;
- a current Owner decision explicitly changing the physical architecture.

When reopening, add the smallest regression evidence that demonstrates the need. Do not start with a new architecture catalogue.

## 10. Verification policy after closure

Kernel verification is intentionally **manual/on-demand**, not a permanent PR/push tax.

The active workflow is:

```text
.github/workflows/kernel-verification.yml
```

It exposes four suites on both Ubuntu and Windows:

- `regression` — deterministic contract/boundary/race/integration coverage plus preserved Lane C acceptance;
- `qualification` — regression/integration plus qualification stress and expanded load groups;
- `stress` — deterministic randomized/model/load/restart-chaos coverage;
- `soak` — sustained mixed workload/resource behavior for the requested duration.

Use the regression suite after a bounded physical change. Use qualification when a production physical invariant, scheduler, persistence, IPC or binding behavior changes materially. Use stress/soak when concurrency, recovery, load or resource behavior is relevant.

A green run is evidence, not authority. A failing test must first be classified as product defect, verifier defect or obsolete assertion against superseded Owner intent.

## 11. Repository truth after closure

Active Kernel architecture:

```text
docs/architecture/kernel.md
```

Whole-system boundary:

```text
docs/architecture/mid-level-architecture.md
```

Owner/DRE correction rationale:

```text
docs/product/lane-c-owner-decision.md
```

Product recovery checkpoint:

```text
NORTH_STAR.md
```

Agent authority and anti-drift rules:

```text
AGENTS.md
```

This handoff exists so future agents do not need to reconstruct Lane C history before doing semantic work. Historical C++, worker/runtime, loopback-web, protocol-v4, checkpoint/MAF-validation and other superseded shapes remain Git-history evidence only and must not be restored as active alternatives.
