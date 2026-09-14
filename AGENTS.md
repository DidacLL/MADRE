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

Third-party libraries, protocols, providers, models, and runtimes may constrain an
adapter implementation, but they do not define MADRE's architecture. Keep their
transport, session, wire-format, lifecycle, and naming concepts behind the relevant
Capability boundary. Change Kernel, SDK, Module, Agent, Material, Workflow, Operation,
or Security Algebra contracts only for a real MADRE responsibility, never because an
external project happens to expose a convenient abstraction.

## Product boundary

- A Module owns meaning, state, persistence, Material, transformations, Agents, the
  Skills it provides, interpretation, continuation, UI, and bounded Operations.
  Workflows are owned by Agents; learning a Module-provided Skill may materialize as
  one or more Workflows for that Agent.
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

Windows and Linux are first-class host platforms for the same MADRE application,
Kernel, SDK, Modules, persistence, and ordinary execution path. Windows is the
Owner's active development environment; never treat it as a compatibility port of a
Unix design. Do not introduce OS-specific assumptions into the public runtime merely
because CI happens to run on one particular Linux distribution.

Platform-specific physical mechanisms may have platform-specific Capability adapters
or acceptance helpers. Keep that difference below the Capability boundary and do not
promote one platform's transport into a universal architecture claim. A Linux-only
adapter may remain useful to Linux users while Windows uses or later qualifies another
physical realization of the same typed contract.

Do not add containers, VM layers, orchestration systems, hosted services, provider
accounts, or other infrastructure to MADRE's mandatory build/CI/product path merely
because they are common portability tooling. Add such a dependency only for a concrete
MADRE requirement accepted by the Owner. Prefer the JDK, the checked-in Gradle
wrapper, native host execution, and the smallest real physical dependency required by
the behavior under test.

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

No production adapter may invent a favorable physical state to keep development
moving. If availability, reachability, or another physical fact has not been observed,
represent that uncertainty explicitly or leave the mechanism unreachable. A fixture
may exercise the state transition, but it may not justify a production constant that
claims the mechanism is healthy.

## Delivery discipline

The active development plan is `docs/master-development-plan.md`. Implement its
large coherent slices in dependency order. Each slice must leave a complete usable
boundary, not a placeholder API or a test that promises later implementation.

For every slice:

1. inspect the current public contracts and affected acceptance journey;
2. implement the complete behavior across its real boundaries;
3. exercise the behavior proportionally, including real execution for real-execution
   claims;
4. review the entire changed surface for responsibility leakage, platform assumptions,
   and familiar framework assumptions;
5. commit and push the coherent green result;
6. never merge without explicit Owner instruction.

MADRE has no installed base. Replace incompatible development artifacts directly;
write no compatibility or migration machinery for discarded prototypes.

Preserve unrelated Owner changes. Keep product meaning in `MADRE.md`, focused
architecture in `docs/architecture/`, current executable truth in
`docs/implementation-baseline.md`, and runnable instructions in `README.md`.
