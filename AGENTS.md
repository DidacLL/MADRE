# MADRE Agent Harness

MADRE is a personal, owner-sovereign modular environment. The repository is being
rebuilt as the dependable base for the Owner's PhD work. Optimize for a coherent,
usable system and durable clarity, not incremental demonstrations.

## Authority and interpretation

Use the current Owner request as the task goal and `MADRE.md` as the durable product
model. Read the focused architecture documents before implementation.

Code, tests, history, deleted documents, generated plans, and familiar software
patterns are evidence only. Recover the intention of Owner examples; do not preserve
a literal historical design merely because it is detailed or tested.

A concept that would fit an ordinary hosted AI platform, agent harness, enterprise
security system, or generic tool-calling framework needs a concrete MADRE
responsibility before it enters this repository.

## Product boundary

- A Module owns meaning, state, persistence, Material, transformations, Agents,
  Skills, Workflows, interpretation, continuation, UI, and bounded Operations.
- An Operation is bounded Module behavior. It creates physical work when it needs a
  Capability and creates new Material when it interprets physical output.
- Kernel owns live registries, physical Capability selection, routing, resources,
  scheduling, durable physical work, physical retry, delivery, and execution logs.
- A Capability is a connector that exposes only its own manifest, accepts physical
  input, and returns physical output. It knows no Module, Agent, Operation, or
  Material concept.
- CORE is a real installation role assigned to one ordinary registered Module. The
  shipped CORE-capable Module uses exactly the same SDK and Kernel path as every
  Module.
- Provider connection and account mechanics remain outside MADRE.

Kernel may transport opaque data that originated in Material. That never transfers
Material ownership. Capability selection uses physical request values and Capability
manifest values, never Material identity or semantic type.

## Algebra

Sensitivity, Privacy, Integrity, Risk, and Autonomy are distinct ordered public
types. Each type owns its intrinsic immutable composition.

- Sensitivity combines by maximum.
- Privacy combines by minimum.
- Integrity combines by minimum.
- Information can continue to a receiver only while accumulated Sensitivity is no
  greater than accumulated Privacy.
- One EffectProfile uses only its own Risk and Autonomy.
- Its non-user causal demand is `min(Risk, Autonomy)`, supported by the minimum
  Integrity of actual non-user causal participants or I5 when there are none.
- Its Risk is supported by a nonempty collection of actual physical realizers.
- Values from different EffectProfiles never combine.

Attach a value directly to the real object or contract where it applies. Do not add a
universal optional-value container, generic security object, autonomous surface
objects, surface identities, decision objects, evidence, history, or a checking
service.

A non-composable participant is simply unreachable for that construction. No
persistent result or special security lifecycle exists.

`Privacy.UNKNOWN` is explicit P2 for an applicable third-party boundary outside
owner control. It is not missing information and is never inferred from locality or
provider identity.

## Public engineering model

The target implementation is Java 21. Use nominal types, immutable records or final
classes, generics, sealed hierarchies only when the domain is genuinely closed, and
small responsibility-specific interfaces. JSON is a codec boundary, never the domain
programming model.

Do not translate the removed Python packages class for class. In particular, do not
restore Material-aware Capabilities, autonomous input/output/responsibility surfaces,
a universal Module or Agent loop, generic property bags, or a test-only Module.

Add an abstraction only when a current MADRE responsibility needs it. Prefer one
cohesive type with meaningful behavior over collections of descriptor fragments.

Tests establish mathematical invariants, public contracts, failure mechanics, and
real integrations. A fixture may replace an external physical mechanism for a
deterministic test, but it cannot stand in for a claimed Module or product behavior.
Product acceptance must execute the shipped CORE-capable Module against real
llama.cpp inference.

## Delivery discipline

The active development plan is `docs/master-development-plan.md`. Implement its
large coherent slices in dependency order. Each slice must leave a complete usable
boundary, not a placeholder API or a test that promises later implementation.

For every slice:

1. inspect the current public contracts and affected acceptance journey;
2. implement the complete behavior across its real boundaries;
3. exercise the behavior proportionally, including real execution for real-execution
   claims;
4. review the entire changed surface for responsibility leakage and familiar
   framework assumptions;
5. commit and push the coherent green result;
6. never merge without explicit Owner instruction.

MADRE has no installed base. Replace incompatible development artifacts directly;
write no compatibility or migration machinery for discarded prototypes.

Preserve unrelated Owner changes. Keep product meaning in `MADRE.md`, focused
architecture in `docs/architecture/`, current executable truth in
`docs/implementation-baseline.md`, and runnable instructions in `README.md`.
