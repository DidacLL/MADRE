# MADRE Kernel — current physical architecture

This document is the current public Kernel architecture. It describes the physical inference subsystem implemented in the active tree.

For whole-system semantics and the semantic/physical boundary, see `docs/architecture/mid-level-architecture.md`. For SPIRA, see `docs/architecture/security-algebra.md`.

## Purpose

The Kernel exists so Module/Agent developers do not need to manage physical inference machinery: process lifetimes, engines, models, RAM/VRAM, queues, retries, crashes, delayed physical execution, scheduling or worker supervision.

The Kernel owns **physical inference Work only**.

## Hard boundary

```text
SEMANTIC MADRE

Agent creates ReasoningRequest
        ↓
Runtime executes configured Module-owned reasoning executor
        ↓
semantic choices resolved above the boundary
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

SPIRA composition remains on the semantic side. Runtime does not become the algebra's semantic owner/evaluator; the responsible Agent/Module semantic context establishes the actual composition before the configured reasoning executor projects acceptable physical requirements.

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

For example, semantic software may conclude that only a subset of physical engines is acceptable. Kernel receives that physical candidate restriction; it does not receive the Material Sensitivity, receiving Privacy, Agent Integrity, Operation Risk, Agent Autonomy or other SPIRA facts that led to the choice.

## Engine descriptors

Kernel exposes factual physical information needed to understand possible execution destinations, such as:

- engine identity;
- supported work types/capabilities;
- placement/execution location;
- provider/model identity where applicable;
- resources;
- availability/health;
- current loaded/warm state where relevant.

Kernel does not translate these facts into semantic Privacy or Integrity.

Semantic software above the boundary can inspect the facts, combine them with its own semantic context, and express the chosen result as physical Work constraints.

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

The Kernel is the physical control plane; workers own model/runtime-specific inference execution.

The active tree includes a native llama.cpp worker path. Other engine families can be added without turning Kernel core into a provider-specific subsystem.

A Module may also use an entirely private inference stack without passing that internal work through the shared Kernel.

## Persistence split

Kernel persists only technical lifecycle.

Semantic persistence belongs above the boundary and may include the reasoning request, context, origin/correlation and continuation.

Kernel persistence contains only physical Work identity, requirements, scheduling state, attempts, engine/resource facts, retry/cancel state and terminal technical outcome.

## Replaceability and experimentation

The Kernel does not use the semantic SDK, but MADRE's broader philosophy still applies: solid default behaviour should not imply deliberately closed internal journeys.

Physical responsibilities should stay bounded enough that the Owner can replace or interpose experiments when there is a real need. Examples include alternate engine matching, different resource-admission behaviour, physical observability or learning-assisted physical routing.

An experiment may itself use inference internally. That does **not** allow semantic MADRE concepts to leak into Kernel. A learning-assisted router still reasons only over physical Kernel facts.

This is architectural openness, not a mandate to build a generic plugin framework now.

## Current implementation

The integrated Kernel baseline descends from accepted Lane C head:

```text
fc7ce5c75bc84de5b277aaa91e796a97a54242fd
```

That baseline passed the full Lane C validation on Linux and Windows, including native build/tests, durability/restart scheduling, worker lifecycle/resource accounting, local IPC hardening and real native llama.cpp inference.

The semantic SDK/Module and Runtime layers described by the whole-system architecture are not yet implemented in the active tree. The repository deliberately keeps that gap visible rather than filling it with discarded historical code.