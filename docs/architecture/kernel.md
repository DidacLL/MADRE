# MADRE Kernel — current physical architecture

This document is the current public Kernel architecture. It describes the physical inference subsystem that is implemented in the active tree.

For whole-system semantics and the semantic/physical boundary, see `docs/architecture/mid-level-architecture.md`.

The older `docs/architecture/lane-c-native-kernel.md` is retained as the detailed Lane C implementation contract/evidence. Where its upstream semantic examples conflict with the current mid-level architecture, the current mid-level architecture wins. The physical requirements and evidence remain useful.

## Purpose

The Kernel exists so Module/Agent developers do not need to manage physical inference machinery: process lifetimes, engines, models, RAM/VRAM, queues, retries, crashes, delayed physical execution, scheduling or worker supervision.

The Kernel owns **physical inference Work only**.

## Hard boundary

```text
SEMANTIC MADRE

ReasoningRequest
+ execution preferences/declarations
        ↓
Runtime executes configured Module-owned reasoning executor Operation
        ↓
physical Work requirements
        ↓
madre-kernel-client

================ HARD BOUNDARY ================

native Kernel
        ↓
physical engine / worker
```

The Kernel must not know:

```text
Module
Agent
Operation semantics
Material
ReasoningRequest
SPIRA / Sensitivity / Privacy / Integrity / Risk / Autonomy
CORE
Skill
Workflow
WorkPlan semantics
semantic continuation
semantic persistence
```

If Kernel code needs one of those concepts, the semantic/physical boundary has drifted.

## Physical client

`madre-kernel-client` is deliberately small and physical. It does not depend on `madre-sdk`.

The current contract supports physical concerns such as:

- submit Work;
- inspect status;
- collect result;
- cancel Work;
- acknowledge retained results;
- inspect factual engine descriptors.

Exact implemented types live in the artifact itself.

## Physical Work

Physical Work can carry technical facts such as:

- physical inference family/work type;
- opaque physical input;
- effort;
- urgency;
- technical capability requirements;
- exact or constrained engine/model choices where supplied;
- eligible time;
- timeout/deadline;
- retry specification.

It does not carry the semantic explanation for those requirements.

For example, a locality requirement may have been produced because of an upstream SPIRA composition. Kernel receives only the resulting technical constraint, not SPIRA values or the semantic reason.

## Kernel responsibilities

Kernel owns:

- durable physical Work lifecycle;
- physical eligibility and scheduling;
- engine inventory and factual descriptors;
- technical compatibility/matching;
- resource admission/accounting;
- worker lifecycle and crash isolation;
- retry and cancellation;
- technical attempts/failures;
- durable terminal physical result.

Kernel remains correct if Runtime and all Module processes disappear while physical Work is pending.

## Engines and workers

Heavy inference runs outside the Kernel process in replaceable engine/worker implementations.

The Kernel is the control plane; workers own model/runtime-specific inference execution.

The active tree includes a native llama.cpp worker path. Other engine families can be added without turning the Kernel core into a provider-specific subsystem.

A Module may also use an entirely private inference stack without passing that internal work through the shared Kernel.

## Persistence split

Kernel persists only technical lifecycle.

Semantic persistence belongs above the boundary and may include the reasoning request, context, origin/correlation and continuation.

Kernel persistence contains only physical Work identity, requirements, scheduling state, attempts, engine/resource facts, retry/cancel state and terminal technical outcome.

## Replaceability and experimentation

The Kernel does not use the semantic SDK, but MADRE's broader philosophy still applies: solid default behaviour should not imply deliberately closed internal journeys.

Physical responsibilities should stay bounded enough that the Owner can replace or interpose experiments when there is a real need. Examples could include alternate engine matching, different resource-admission behaviour, physical observability, or learning-assisted physical routing.

An experiment may itself use inference internally. That does **not** allow semantic MADRE concepts to leak into Kernel. A learning-assisted router still reasons only over physical Kernel facts.

This requirement is architectural openness, not a mandate to build a generic plugin framework now.

## Current implementation

The integrated Kernel baseline descends from accepted Lane C head:

```text
fc7ce5c75bc84de5b277aaa91e796a97a54242fd
```

That head passed the full Lane C validation on Linux and Windows, including native build/tests, durability/restart scheduling, worker lifecycle/resource accounting, local IPC hardening and real native llama.cpp inference.

The semantic SDK/Module and Runtime layers described by the whole-system architecture are not yet implemented in the active tree. The current repository deliberately keeps that gap visible rather than filling it with discarded historical code.