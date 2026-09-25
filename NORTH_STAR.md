# MADRE — North Star

This is the mandatory short recovery checkpoint for substantial MADRE work.

Before designing, implementing, auditing or correcting a meaningful MADRE slice, answer the eight questions below in the context of the task. Do not answer from industry convention, historical implementation or memory when the repository authorities are available.

This document is deliberately concise. It does **not** replace `docs/product/owner-intent-corpus.md` or the detailed reasoning in `MADRE.md`. If a statement here seems ambiguous, recover its meaning from those richer authorities rather than expanding it with familiar platform assumptions.

## 1. What is MADRE?

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is the Owner's local-first environment for **using and creating AI-native software**.

Its central thesis is that useful application capability does not have to equal one synchronous frontier-model call. Domain-aware software can combine persistent knowledge, deterministic Operations, Agents, accumulated results, local/open inference and **Delayed Reasoning Effort**, then use stronger external inference selectively where it adds real value.

MADRE provides a simple user surface over that complexity and a public SDK intended to make owner-local AI-native applications practical to build, including eventually through AI-assisted builders.

## 2. What is MADRE not?

MADRE is not one model, one assistant, one Agent framework, one provider harness, one workflow engine, CORE, or the Kernel.

It is not a universal ontology of everything on the Owner's computer, and not every useful capability becomes a Module.

It is not a security authority above the Owner.

It is not defined by whichever inference provider or implementation happens to be used today.

## 3. Why does MADRE exist?

Because much current dependence on frontier-cloud AI is partly architectural.

Many user tasks can be decomposed into domain knowledge, bounded deterministic work, smaller reasoning needs, delayed verification/research, accumulated state, and only a remaining subset that genuinely benefits from frontier inference.

MADRE exists to make that composition practical so local/open intelligence can do far more useful work than isolated model capability suggests, while retaining selective access to stronger providers.

Provider independence, privacy and data ownership are major consequences of keeping the application, its domain state and much of its reasoning inside the Owner's environment rather than surrendering the whole software harness to a third party.

## 4. What does MADRE own, and what does it delegate?

MADRE owns only the common concepts and shared machinery that genuinely need to be common across independently installed AI-native applications.

That includes the public SDK surface, the installed Runtime coordination/execution environment, and the shared physical inference Kernel boundary.

Modules own their application/domain semantics, state, persistence, UI, integrations and internal implementation. Modules provide their own Agents where they need them. Agents belong to their providing Modules. Not every Module provides an Agent.

Models, providers and engines are replaceable mechanisms used by the environment; they are not MADRE's semantic identity.

## 5. What does the final user want?

The user wants useful AI-native software and a natural general experience, not infrastructure administration.

They want applications that can use domain knowledge, local/open intelligence, delayed reasoning and stronger providers where worthwhile without requiring them to manually operate workers, schedulers, routing, GPU allocation or provider plumbing.

## 6. What should the Owner be able to know and edit?

The Owner should be able to inspect the meaningful structure of their installation: installed Modules, available Agents and capabilities, important configuration, relevant information movement, consequential/delayed work, and external intelligence use where it matters.

Because the system belongs to them, they should ultimately be able to modify, replace or remove the software, state, configuration and implementations they own, including experimenting at Runtime and Kernel level.

MADRE mechanisms serve the Owner; they do not protect MADRE from the Owner.

## 7. How much friction / learning curve should MADRE impose?

Very little mandatory friction.

The intended progression is:

```text
use
→ inspect
→ configure
→ understand/develop
→ modify
→ replace
→ experiment
```

A normal user can remain at the first step. Greater depth is optional and progressively available.

## 8. What is the development scope?

MADRE is a one-Owner research/product project developed heavily with AI assistance.

Build the smallest solid environment that can make the MADRE thesis real: powerful public SDK, independently installable Modules, simple Runtime composition/execution, Delayed Reasoning Effort, meaningful SPIRA semantics, and a narrow durable physical Kernel.

Keep the architecture explicit enough that humans and a wide range of capable AI builders can create ordinary Modules against public contracts without hidden first-party knowledge.

Prefer working behaviour, clear replaceable boundaries and fast experimentation over enterprise/platform abstractions, compatibility machinery without users, or speculative frameworks.

## Anti-drift use

Before substantial work, answer all eight questions explicitly.

If the proposed implementation changes any answer, stop and determine whether the Owner actually changed MADRE or whether the implementation is drifting.

If this short checkpoint and a richer authority appear to conflict, do not simplify the richer reasoning away. Re-read `docs/product/owner-intent-corpus.md` and `MADRE.md` and recover the intended meaning before changing architecture.