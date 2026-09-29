# MADRE Kernel closure handoff

Status: **operational handoff after the repaired Lane C closure**.

This document is not a new product authority. It records the qualified physical Kernel baseline, the boundary Lane A/B may rely on, the concrete production defects/corrections discovered during Lane C, and the anti-drift backlog for future agents.

Current lane map:

```text
Lane A = public Java 21 semantic SDK / Module construction surface
Lane B = minimal Runtime: Module environment + semantic reasoning persistence/correlation + semantic→physical bridge
Lane C = closed qualified physical Kernel
```

Authority remains the order in `AGENTS.md`. For Kernel architecture itself, `docs/architecture/kernel.md` is current. For the Owner reasoning that fixed the boundary, read `docs/product/lane-c-owner-decision.md`.

## 1. Closure identity and evidence

Current executable closure baseline:

```text
77a862941e9c05d15652317616069a296af0f397
```

Deterministic boundary requalification:

```text
GitHub Actions run 36604046199
seed 12648430
scale medium

Ubuntu  regression     PASS
Ubuntu  qualification  PASS
Windows regression     PASS
Windows qualification  PASS
```

The preserved Lane C acceptance suite ran inside every job and includes the repaired semantic→physical boundary cases.

The earlier closure baseline `95ddf2250c27d28e90e215391223164965b68729` is **superseded as a closure point**. Its physical Kernel quality was strong, but the public boundary still lost exact/eligible physical restrictions and did not expose enough factual execution-path information. Do not use the old green CI as authority for the repaired contract.

## 2. Stable physical responsibilities

Lane A/B may rely on Kernel providing:

- durable `PhysicalInferenceWork` identity and lifecycle;
- durable physical admissibility metadata;
- optional opaque eligible capability set, with singleton preserving exact physical selection;
- eligibility, urgency and deadline-aware physical scheduling;
- request-side allowed exposure (`LocalOnly` / `ExternalAllowed`);
- factual capability execution location/destination plus optional route/retention facts with provenance;
- configured `InferenceCapability` facts separate from current availability and historical observations;
- effort admissibility;
- capability selection only inside the Work's physical admissible space;
- availability preference, Owner preference and binding/version-scoped successful latency evidence where current DRE consumes them;
- durable attempts, typed physical failure, retained result, cancellation and release;
- restart recovery to `UnknownCompletion` rather than silent replay;
- one physical process owner per SQLite database, including Linux path/symlink aliases;
- bounded physical concurrency;
- supervised scheduler/probe/execution persistence;
- open physical execution through `IInferenceBinding`;
- process and MEAI bindings without provider/runtime ownership by Kernel;
- versioned bounded local IPC over Unix-domain sockets on Windows/Linux;
- Java 21 `madre-kernel-client` as the physical client surface;
- strict IPC/config/CLI parsing;
- manual Linux/Windows physical verification through `.github/workflows/kernel-verification.yml`.

These are physical guarantees only. They do not imply semantic success, answer quality, application completion or semantic continuation.

## 3. The repaired hard boundary

The final direction is:

```text
Module / Agent / semantic MADRE
        ↓
ReasoningRequest + SPIRA + Owner choice
        ↓
resolve information journey
        ↓
derive physical admissibility / exact physical restriction
        ↓
madre-kernel-client
================ HARD BOUNDARY ================
        ↓
PhysicalInferenceWork
    prepared input
    effort / urgency / timing
    allowed exposure
    optional eligible capability IDs
        ↓
Kernel DRE chooses only inside that physical space
        ↓
InferenceCapability factual execution path + state/evidence
        ↓
IInferenceBinding
        ↓
Owner-selected independent inference environment
```

In the other direction:

```text
configured capability/binding
        ↓
factual execution location
factual destination
optional route/intermediary facts
optional retention/history facts
+ provenance
        ↓
semantic MADRE / Owner inspection
        ↓
information-journey decision above Kernel
```

Kernel does **not** know why a capability was eligible or excluded. It does not receive Module, Agent, Material, SPIRA, ReasoningRequest, CORE, Workflow/WorkPlan or semantic continuation.

## 4. Exact selection versus meaningful DRE

Two rejected extremes must not return.

Too wide:

```text
semantic MADRE derives privacy/Owner restrictions
        ↓
restriction is lost
        ↓
Kernel may select any generally compatible capability
```

Too narrow:

```text
semantic MADRE chooses exact provider/model/invocation every time
        ↓
Kernel receives no meaningful physical choice
        ↓
DRE degenerates into a timer/executor
```

Current rule:

- no eligible-id set: ordinary physical DRE among all otherwise admissible configured capabilities;
- several eligible ids: semantic/Owner admissibility is preserved, Kernel DRE chooses within that set;
- one eligible id: exact Owner/semantic physical choice is preserved;
- Kernel never widens the set.

Capability IDs are deliberately physical/opaque. Do not add semantic reason codes or SPIRA to Work to explain the restriction.

## 5. Factual path versus request permission

Do not conflate these contracts again.

Request-side:

```text
ExecutionBoundary.LocalOnly
ExecutionBoundary.ExternalAllowed
```

This is what the Work is allowed to do.

Capability-side:

```text
ExecutionPath.Location: Local / External
ExecutionPath.Destination
ExecutionPath.Route? 
ExecutionPath.DataRetention?
```

This is what the configured physical path factually is/does.

`ExternalAllowed` is never a truthful answer to "where does this capability send the information?". Semantic MADRE uses factual capability data plus actual SPIRA/Owner context to derive the downward restriction.

Route/retention text is intentionally opaque to Kernel policy. If future product work needs more structured physical facts, add them only when a real semantic/DRE consumer justifies them; do not build a provider ontology pre-emptively.

## 6. Production defects/corrections found during Lane C

### Physical database ownership aliases

Linux filesystem aliases/symlinks could previously produce distinct ownership lock paths for the same SQLite database. The fix canonicalizes physical database ownership identity. IPC endpoint identity remains separate.

### Urgency across slot-release races

A fast completion could release capacity during a scheduler pass and let later stale ordering consume it. Dispatch capacity is now fixed for the pass; released capacity is reconsidered in a fresh pass.

### Semantic→physical boundary compression

The first capability-aware request lacked exact/eligible capability restrictions and reused one local/external enum as both Work permission and capability fact. This could either violate an Owner/semantic information-journey decision or force all physical choice above Kernel.

The repaired contract separates:

- opaque Work capability eligibility/exact selection;
- request-side allowed exposure;
- factual capability execution path exposed upward.

Protocol and pre-release schema moved to version 2 because this is a real public physical boundary change.

## 7. Verification lessons that are not architecture

During physical verification, several failures were test-harness defects: cancellation-insensitive fixtures, Unicode console assumptions, observer-induced SQLite pressure, Unix-domain-socket trailing-byte behavior, file-write races and timing assumptions.

Do not turn those harness fixes into product architecture. Tests must observe meaningful physical behavior without inventing their own physical semantics. Production Kernel must not gain test-only architecture merely to simplify tests.

## 8. Lane A backlog — semantic SDK

Lane A owns the public Java 21 semantic construction surface. It does not own Runtime installation mechanics or Kernel physical scheduling.

The SDK must provide the small public vocabulary for:

- Module;
- optional Agent;
- Operation;
- Skill / Workflow / WorkPlan where accepted;
- Material/context and direct SPIRA carriers;
- semantic `ReasoningRequest`;
- semantic composition surfaces;
- the public semantic side of reasoning→physical derivation.

Lane A must preserve:

- first-party, independent and generated Modules use the same surface;
- agentless Modules are normal;
- Operation invocation and Agent delegation are distinct;
- SPIRA stays on actual semantic carriers rather than one generic security object;
- `ReasoningRequest` stays semantic;
- Module authors do not construct Kernel Work directly;
- Module authors do not need provider/binding/SQLite/IPC scheduling knowledge.

The SDK may expose the capability facts needed for Owner/semantic inspection through a semantic-friendly surface, but must not copy Kernel implementation classes upward as the semantic ontology.

## 9. Lane B backlog — Runtime and semantic→physical bridge

Lane B owns the installed semantic environment and the actual crossing into the already-closed physical Kernel.

Runtime responsibilities include:

- Module installation/configuration;
- CORE role assignment;
- live discovery/lifecycle/addressing;
- cross-Module Operation routing;
- explicit Agent delegation routing;
- semantic inspection/diagnostics;
- persistence/correlation needed for delayed semantic reasoning;
- semantic continuation/recovery;
- correlation of semantic requests with Kernel Work/results;
- transport through `madre-kernel-client`;
- semantic derivation of physical inference restrictions.

For each reasoning need, Lane B must be able to use actual semantic facts, SPIRA and Owner choice together with factual capability execution paths to derive:

```text
prepared physical input
requested physical effort
urgency
eligibleAt / deadline
allowed exposure
optional eligible physical capability IDs
```

An explicit Owner-selected capability/provider/model/endpoint must map to an exact physical restriction when that configured physical capability is known. Otherwise semantic MADRE may produce a larger eligible set and leave DRE meaningful choice inside it.

Do not solve the bridge by:

- sending SPIRA into Kernel;
- exposing `PhysicalInferenceWork` as the normal semantic SDK request;
- letting Kernel decide the semantic information journey;
- preselecting every exact invocation above Kernel even when several physically valid capabilities remain;
- silently broadening the eligible set because a preferred capability is unavailable.

## 10. CORE and end-to-end proof

CORE remains an ordinary Module assigned the CORE role. It may supply defaults and Owner interaction but must use the same public semantic surfaces as other Modules. It is not Runtime, Kernel, owner of other Modules/Agents or a special SPIRA authority.

After Lane A/B exist coherently, prove an ordinary public path:

```text
Module/Agent semantic need
→ ReasoningRequest
→ inspect factual capability paths
→ semantic/SPIRA/Owner derivation
→ physical admissibility / exact restriction
→ Kernel Work
→ DRE selection inside admissible space
→ physical result
→ semantic correlation/continuation
```

No hidden first-party shortcut should be needed.

## 11. Anti-drift questions before reopening Kernel

1. Is the requested change actually physical inference responsibility?
2. Can Lane A/B solve it through semantic derivation/correlation using the current physical contract?
3. Would the change make Kernel know Module, Agent, Material, SPIRA, ReasoningRequest, CORE or semantic continuation?
4. Would it rematerialize provider/model/runtime internals as MADRE-owned engines/workers?
5. Would it discard an exact Owner choice or semantic eligible physical set?
6. Would it let Kernel broaden semantic/Owner admissibility?
7. Would it move all exact physical choice above Kernel and amputate DRE?
8. Is factual execution-path information genuinely missing, or is a new ontology being invented speculatively?
9. Can a new inference mechanism use the existing binding/capability seam?
10. Is there a reproducible physical defect/current product need, or only a preference for another design?

## 12. When Kernel may be reopened

Reopen Lane C only for:

- a reproducible physical correctness defect;
- a real semantic→physical physical restriction that the current contract cannot express and has an actual consumer;
- factual physical execution-path information genuinely needed above Kernel but unavailable;
- a real inference environment that cannot use the current binding/capability seam without violating physical truth;
- a measured scheduling/recovery requirement not representable now;
- an explicit new Owner decision.

Then add the smallest behavioral regression proving the need. Do not begin with a new platform architecture.

## 13. Verification policy

Kernel verification stays **manual/on-demand**:

```text
.github/workflows/kernel-verification.yml
```

Available suites:

- `regression` — deterministic contract/boundary/race/integration coverage plus preserved acceptance;
- `qualification` — regression/integration plus heavier deterministic concurrency/load groups;
- `stress` — randomized/model/load/restart-chaos;
- `soak` — sustained mixed workload/resource behavior.

Lane A/B work does not run the Kernel arsenal merely because it shares the repository. Run Kernel verification when the physical contract/implementation actually changes.

A green run is evidence, never product authority.

## 14. Repository truth

Read in this order for Lane C interaction:

```text
NORTH_STAR.md
AGENTS.md
docs/product/lane-c-owner-decision.md
docs/architecture/mid-level-architecture.md
docs/architecture/kernel.md
docs/architecture/kernel-handoff.md
```

Historical C++, worker/runtime, exact-preselected-invocation, ambiguous `ExecutionBoundary` capability facts, loopback-web, protocol-v4 and checkpoint/MAF-validation shapes are evidence only, not active alternatives.
