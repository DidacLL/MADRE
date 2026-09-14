# MADRE Agent Harness

MADRE is personal, owner-sovereign modular software and the dependable base for the Owner's PhD work. Optimize for a coherent usable system and durable clarity, not incremental demonstrations.

## Authority

Use the current Owner request as the task goal and `MADRE.md` as the durable product model. Read the focused documents in `docs/architecture/` and `docs/implementation-baseline.md` before implementation.

Code, tests, history, deleted documents and familiar software patterns are evidence only. Recover Owner intent rather than preserving a historical design because it is detailed or tested. A concept that fits a generic hosted AI platform, agent harness, enterprise policy system or tool-calling framework needs a concrete MADRE responsibility before it enters the repository.

Third-party libraries, protocols, providers, models and runtimes may constrain an adapter implementation, but they do not define MADRE architecture. Keep transport, sessions, wire formats, lifecycle and naming behind the boundary that actually needs them.

## Product boundary

- A Module owns meaning, state, persistence, Material, transformations, Agents, Skills, Agent-owned Workflows, interpretation, continuation, UI and bounded Operations.
- An Operation is bounded Module/application behavior. Ordinary file, network, database, device and search I/O remains ordinary Module/application behavior unless a specific shared Kernel responsibility exists.
- Kernel owns the live executable Module registry/public invocation boundary, optional CORE-role resolution, and the shared reasoning runtime: reasoning-mechanism registration/selection, resources, immediate/durable reasoning, retries, cancellation, delivery and runtime logging.
- `ReasoningCapability` is intentionally narrow. It accepts only nominal `ReasoningComputation<R>` values and returns reasoning results. It knows no Module, Agent, Operation, Workflow, Material or semantic continuation.
- Reasoning adapters are ordinary owner-installable JVM artifacts discovered independently from Modules through the public reasoning-adapter SPI. `madre-app` must not know concrete reasoning-provider types or parse provider-specific configuration.
- Module providers declare their canonical `ModuleId` before materialization and receive immutable owner configuration scoped to that exact identity through the public Module SDK. `madre-app` may extract/deliver the generic `modules.config[<ModuleId>].*` namespace, but Module-specific keys, parsing, validation and typed settings remain inside the Module artifact.
- Module installation and reasoning-mechanism installation are distinct. They use separate directories, service-provider contracts and identities; CORE creates no reasoning privilege.
- CORE is an optional installation role assigned to one ordinary registered Module. It creates no subtype or privilege.
- Search is not a Kernel capability. SearXNG is an ordinary reusable client for domain Modules or applications that legitimately need web search.
- Provider connection and account mechanics remain outside MADRE.

Kernel may persist opaque bytes originating from a reasoning request. That never transfers Material ownership or semantic meaning to Kernel.

## Security Algebra

Sensitivity, Privacy, Integrity, Risk and Autonomy are distinct ordered public types. Each owns only its intrinsic immutable composition.

- Sensitivity combines by maximum.
- Privacy combines by minimum.
- Integrity combines by minimum.
- Information can reach a receiver only while accumulated Sensitivity is no greater than accumulated Privacy.
- One EffectProfile uses only its own Risk and Autonomy.
- Its non-user causal demand is `min(Risk, Autonomy)`, supported by the minimum Integrity of actual non-user causal participants, or I5 when there are none.
- Values from different EffectProfiles never combine.

`Risk` is Module-domain consequence metadata. It is not propagated into `ReasoningRequest`, and `ReasoningCapability` manifests do not carry action-realizer Integrity. A reasoning mechanism does not become the physical realizer of a Module's external effect merely because a Module used inference while deciding what to do.

Attach a value directly to the real object or contract where it applies. Do not add a universal optional-value container, generic security object, surface identity, policy decision, evidence/history system or checking service.

A non-composable participant is simply unreachable for that construction. No persistent security result or special security lifecycle exists.

`Privacy.UNKNOWN` is explicit P2 for an applicable third-party boundary outside owner control. It is not missing information and is never inferred from locality or provider identity.

## Public engineering model

The implementation is Java 21. Use nominal types, immutable records/final classes, generics, sealed hierarchies only for genuinely closed domains, and small responsibility-specific interfaces. JSON is a codec boundary, never the domain programming model.

Windows and Linux are first-class hosts for the same application, Kernel, SDK, Modules, reasoning-adapter installation, persistence and ordinary execution path. Windows is the Owner's active development environment and is not a compatibility port of a Unix design.

Platform-specific reasoning transports may have platform-specific adapters. Keep differences below the reasoning adapter boundary. Do not promote one platform's transport into a universal architecture claim.

Module developers may depend on the published SDK and relevant published computation-contract artifacts, but not on `madre-app` or Kernel runtime implementation classes merely to receive installation configuration or execute bounded Module behavior. Reasoning adapter developers may depend on the published reasoning SPI and relevant published computation-contract artifacts, but not on `madre-app` or Kernel runtime implementation classes merely to register a mechanism. Keep registries, SQLite stores, schedulers and application assembly private to their runtime responsibilities.

Do not add containers, VM layers, orchestration systems, hosted services or provider accounts to the mandatory build/CI/product path without a concrete Owner-accepted requirement. Prefer the JDK, checked-in Gradle wrapper, native host execution and the smallest real dependency required by the behavior under test.

Do not restore the removed Python packages class-for-class. In particular, do not restore Material-aware capabilities, a generic `Capability<C,R>` Kernel SPI, universal physical-action dispatch, autonomous input/output surfaces, a universal Module/Agent loop, arbitrary property bags or test-only product Modules.

Tests establish mathematical invariants, public contracts, failure mechanics and real integrations. A fixture may replace an external reasoning mechanism for deterministic tests, but it cannot stand in for claimed product behavior. No production adapter may invent favorable availability or reachability.

## Delivery discipline

`docs/master-development-plan.md` is the completed foundation-plan record. Current executable truth belongs in `docs/implementation-baseline.md`; product meaning belongs in `MADRE.md`; focused architecture belongs in `docs/architecture/`; runnable instructions belong in `README.md`.

For each coherent change:

1. inspect current public contracts and the affected acceptance journey;
2. implement complete behavior across its real boundaries;
3. exercise behavior proportionally, including real execution for real-execution claims;
4. review the changed surface for responsibility leakage and platform/framework assumptions;
5. commit and push a coherent green result;
6. never merge without explicit Owner instruction.

MADRE has no installed-base compatibility obligation for discarded prototypes. Replace incompatible development artifacts directly and preserve unrelated Owner changes.
