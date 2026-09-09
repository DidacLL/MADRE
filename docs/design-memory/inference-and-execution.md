# Inference and Execution Design Memory

Authority: non-normative design memory. Canonical execution semantics remain in `docs/architecture/MADRE-execution-contract.md`.

## Capability means physical mechanism

**OWNER — CONFIRMED**

A Capability is an available physical inference/execution mechanism with known properties. It is not a semantic statement that an Agent can solve a task.

One provider can expose many different mechanisms:

```text
direct API
account-authenticated CLI/session
SDK
MCP path
local gateway/bridge
externally supplied credentials
user-installed adapter over software the user controls
```

Those mechanisms may differ in model inventory, cost, latency, modality, privacy, authentication and resource behavior.

## Requirements versus installed mechanisms

**OWNER — CONFIRMED**

Modules generally should not need to know the installed provider/model inventory.

They express requirements/preferences such as:

- modality/specialization;
- latency;
- reasoning effort/quality;
- cost policy;
- locality/privacy;
- resource/availability constraints;
- optional provider/model/mechanism preference;
- fallback permission/order.

Kernel matches them deterministically against installed mechanism facts and current resources.

A provider/model name may be a preference or a hard requirement depending on the request.

## Local-first ecosystem

**OWNER — CONFIRMED**

Local inference is the primary focus, but local/cloud is only one dimension. Remote mechanisms remain valid when the security algebra and request permit them.

MADRE should make a diverse local/remote ecosystem usable without forcing every application developer to integrate each provider/runtime personally.

## Durable material ownership

**OWNER — CONFIRMED**

All durable accepted/queued work is reference-only from Kernel's perspective.

The owning Module retains actual material. Kernel keeps a `MaterialHandle` containing reference/integrity/security binding plus a simple opaque retrieval coordination value.

Actual bytes are resolved only when the concrete attempt is ready, verified, used transiently and discarded. Restart/retry resolve the material again.

This is both a privacy property and a resource property: private application context should not be duplicated merely because work is scheduled for later.

## Transient inference

**OWNER — CONFIRMED**

Transient inference is a non-durable execution primitive and may directly carry minimal ephemeral material.

Modules/Agents decide why they use it. One important use is CORE's fast interaction behavior, but Kernel should not encode that semantic strategy as a special lane.

## Advanced local inference experiments

**OWNER — DIRECTION**

MADRE should leave room for research/optimization over local inference mechanisms: model residency, KV-cache/session reuse, resource-aware switching and mechanism-native execution state can be important on constrained machines.

These are mechanism/adapter concerns unless a generic execution requirement genuinely emerges.

The SDK should allow optional advanced adapter extensions so researchers/developers can reach such native functionality without contaminating every generic Kernel contract.

## Generated output

**OWNER — CONFIRMED**

Once delivered, generated output is ordinary Module-owned Artifact material. It may be stored, fed into another Agent/model, incorporated into a WorkPlan, treated as evidence or used to select a bounded Operation according to Module semantics.

Kernel is content-opaque and has no semantic rule that generated material is inherently authoritative or non-authoritative.

## Provider access

**OWNER — CONFIRMED**

API keys, OAuth/account sessions, vendor login, CLI authentication and similar details belong inside a Capability adapter or external provider software.

MADRE should not become a generic credential framework, nor should it structurally forbid unconventional user-created adapters.
