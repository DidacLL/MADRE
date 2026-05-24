# Product Orientation

Author: ag
State: read
Authority: derived orientation only; if this conflicts with `docs/tex/MADRE-AgenticSystem.tex`, the TeX dossier wins.

MADRE is a local-first, model-agnostic runtime kernel for governed agentic systems. It coordinates foreground conversation, delayed reasoning, governed context, model invocation, bounded internal actions, memory, learning, audit, recovery, and user authority boundaries without treating a language model as the whole system.

## What MADRE Is

- A runtime kernel under agentic applications.
- A model-agnostic coordination layer for replaceable model runtimes.
- A delayed-reasoning architecture that separates immediate interaction from deeper module-local work.
- A policy-aware boundary for context, actions, memory, learning, and remote transfer.
- A developer foundation for private, domain-aware, extensible assistant and workflow-capable systems.

## What MADRE Is Not

- Not a chatbot product; chat is only one possible access surface.
- Not a prompt collection or prompt wrapper.
- Not a single-vendor API wrapper.
- Not an unrestricted automation agent.
- Not a passive conversation-history store.

## Core Invariants

- Local-first, not cloud-first.
- Model-agnostic by contract.
- Foreground response separated from durable delayed reasoning.
- Context is governed, sourced, minimized, classified, scoped, and revocable.
- Tools/actions are typed internal capabilities behind policy.
- Model output is not authority.
- Memory and learning start quarantined.
- Audit and recovery are part of the product claim.
- MADRE runtime is not MADREdev process.
