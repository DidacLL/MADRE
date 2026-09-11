# CORE and Interaction Design Memory

Authority: non-normative design memory. Canonical product meaning remains in `MADRE.md` and focused architecture.

## CORE as the shipped default general Module

**OWNER — CONFIRMED**

MADRE ships with a default CORE-capable Module. It remains selected by default unless the user installs/selects another Module satisfying the CORE contract.

CORE is replaceable and independent from Kernel. Its importance comes from the generic work it owns and the sensitive material it may legitimately handle, not from a bypass around ordinary MADRE boundaries.

Typical shipped CORE behavior includes:

- general/default interaction and UI/UX;
- fallback intelligence for UI-less or Agentless Modules;
- generic delegation/escalation;
- system-oriented intelligent assistance such as configuration/install help;
- user/system profiles, preferences and interaction continuity that belong to CORE's semantic domain.

## CORE security intuition

**OWNER — CONFIRMED**

CORE may handle some of the installation's most sensitive and personally identifying/profilable material: prompts, secrets, preferences, schedules, inferred profiles, system metadata and cross-domain context.

CORE-capable Modules therefore need the strongest applicable Privacy and Integrity
characteristics under the current scoped Security Algebra.

This is independent from the Sensitivity of CORE-owned data. CORE can carry strong
Privacy and Integrity characteristics while its Artifacts/ContextBundles carry
maximum Sensitivity.

Cross-domain material still follows the normal algebra. CORE can receive material when the carried security objects permit it and should minimize/anonymize/omit information before sending it through less-private or higher-risk boundaries when its semantics allow.

## Interaction surfaces

**OWNER — CONFIRMED**

MADRE does not force one UI.

User interaction may be owned by:

```text
a native Module UI
the shipped/replacement CORE UI
an AI-provider/tool GUI or CLI through an adapter
a machine/automation surface
```

A native Module may handle its interaction completely itself or delegate selected governed input to CORE.

A Module without its own UI may use CORE as the default UI/UX. This is also the natural default for simple third-party adapters or unknown wrappers.

## CORE interaction Agent

**OWNER — CONFIRMED**

When CORE owns the user-facing interaction surface, direct user input is handled by CORE's interaction Agent/behavior.

That Agent can use low-latency transient inference to maintain natural interaction and independently decide whether further reasoning/work is useful.

Possible outcomes include:

```text
trivial/chatty input -> immediate response is enough
small reasoning need -> additional transient/background inference
substantive work -> delegate / build WorkPlan / submit durable work
unknown domain -> find/delegate to a suitable Agent/Module
```

This is Agent/Module reasoning, not a special Kernel lane. Kernel only executes the resulting inference/work requests.

Fast response should be actual inference and natural to the situation rather than a fixed acknowledgement protocol.

## Resource-constrained personal computers

**OWNER — CONFIRMED**

MADRE targets ordinary personal machines where OS/app workloads already consume substantial memory and local inference resources are scarce.

Concrete design discussions have used machines with less than roughly 4 GB free VRAM and 16–24 GB RAM under Windows pressure as realistic cases.

This motivates:

- inexpensive interaction paths;
- globally coordinated inference resources;
- avoiding unnecessary model loads/context duplication;
- durable reasoning that can happen when resources are available;
- JIT acquisition rather than queued private payload copies.

The exact scheduler strategy should follow measured workloads rather than being frozen from this example hardware.

## Product character

**OWNER — DIRECTION**

MADRE should feel like software that gets useful work done, not an oracle forced to answer immediately and not a chatbot that exposes technical scheduling details through robotic dialogue.

Agents may acknowledge work naturally, delegate to specialists/researchers when needed, and let UI surfaces communicate cost/status/progress where visual presentation is better than conversational ceremony.
