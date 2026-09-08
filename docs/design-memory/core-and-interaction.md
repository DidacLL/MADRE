# CORE and Interaction Design Memory

Authority: non-normative design memory. Canonical product meaning remains in `MADRE.md` and focused architecture.

## CORE is ordinary, replaceable Module

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

CORE is not a privileged architectural layer and not a second Kernel. Kernel and CORE are completely independent. CORE uses the same public Module contracts as any third-party Module.

The shipped CORE is the default/first general Module provided with MADRE. It may supply generic functions that are useful when no domain Module owns them, for example:

- fallback intelligence for Agentless Modules;
- fast-response/default interaction behavior;
- generic UI/UX support;
- module-independent intelligent tasks such as configuration/installation assistance;
- filtering/escalation toward higher reasoning when appropriate;
- other generic coordination not owned by a specific domain Module.

These are Module capabilities, not Kernel privileges. A developer/user may provide another Module implementing those functions and configure it as the installation's CORE/default Module instead of the shipped implementation.

Default/CORE status is therefore a replaceable routing/fallback convention. It must not imply security bypass, private Kernel API, resource exemption, hidden Agent ontology or irreplaceable behavior.

## Interaction should not block on long reasoning

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED principle; exact topology still refining**

MADRE is not fundamentally a blocking chatbot or an oracle expected to answer everything synchronously.

Interactive software should remain responsive while deeper reasoning continues. The fast response path is itself inference, not a fixed set of canned acknowledgements.

Current strong direction:

```text
user input
    -> fast MADRE inference for natural immediate interaction
    -> concurrent/background reasoning path
```

Possible outcomes include:

- trivial/chatty input: fast inference may already be sufficient;
- modestly harder request: background reasoning completes soon and supplies the substantive answer;
- substantive work: background reasoning may schedule durable work or delegate and naturally tell the user that results will come later;
- insufficient knowledge: an Agent may delegate/escalate (for example to a research Agent) instead of behaving like an omniscient oracle.

Exact wording and UX are Module/Agent semantics. Kernel provides execution primitives and resource arbitration only.

## Resource-constrained UX is a product constraint

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

MADRE targets ordinary personal computers, including systems with very limited free VRAM and RAM already pressured by the operating system and applications. Recent concrete target discussions used machines with less than about 4 GB VRAM and 16–24 GB RAM under Windows pressure as realistic cases.

This constraint is why interaction continuity, shared inference resources, JIT durable material, and avoiding duplicated queued context are product-relevant rather than optimization trivia.

## Rejected realization: explicit `/deeper` chatbot protocol

Provenance: **GENERATED INTERPRETATION / experiment**
Status: **REJECTED as product UX**

Explicit `/deeper` markers, terminal `reasoning>` protocol and model-generated escalation markers were development experiments. They are not stable MADRE UX or architecture.
