# MADRE Agent Harness

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It has two equally important product identities: an owner-installed application that must be usable without understanding its implementation machinery, and a public development/experimentation platform on which unrelated developers can build independently installable Modules and reasoning mechanisms.

Optimize for one coherent usable product, fast experimentation and durable public contracts, not architecture demonstrations or framework growth.

## Authority

Use the current Owner request as the task goal and `MADRE.md` as the durable product model. Read every focused document in `docs/architecture/`, then `docs/implementation-baseline.md`, before implementation.

The authority hierarchy is:

1. the Owner's current instruction;
2. `MADRE.md` for durable product meaning, Owner reasoning/rationale and responsibility;
3. `docs/architecture/*` for focused architectural boundaries;
4. `docs/implementation-baseline.md` for what actually executes now;
5. code, tests and history as implementation evidence.

`docs/master-development-plan.md` is the historical/completed foundation-plan record, not an immutable roadmap. Historical code, tests, deleted documents and familiar software patterns are evidence only. Recover Owner intent rather than preserving a sequencing decision because it is detailed or tested.

Third-party libraries, protocols, providers, models and runtimes may constrain an adapter implementation, but they do not define MADRE architecture. Research on external systems is evidence only unless the Owner explicitly accepts a direction.

## Owner reasoning and interpretation discipline

The `Owner reasoning` section in `MADRE.md` is normative causal context, not explanatory prose that may be compressed into a different roadmap slogan. Preserve that reasoning when resolving ambiguity.

The intended chain is:

- acceptable owner UX cannot be specified reliably before substantial experimentation in both semantic agentic engineering and inference/runtime behavior;
- therefore making experiments cheap, typed, modular, testable and independently consumable is itself a product requirement;
- the SDK is the primary software-engineering surface for semantic experiments such as Agent definitions, Skills, Workflows, context/request construction, semantic memory/knowledge structures and Module composition;
- typed reasoning computation contracts and independently installed providers are the experimentation surface for heterogeneous inference origins;
- successful experimental patterns may graduate into stable SDK/contract artifacts only after real use demonstrates that they are worth freezing;
- `model-agnostic` means Kernel is independent of concrete model/runtime implementations, not that public inference contracts must remain lowest-common-denominator or under-configurable;
- portable request semantics belong to typed computation contracts, mechanism/model/runtime-specific tuning belongs to providers/adapters, and shared scheduling/selection/resource/durability mechanics belong to Kernel.

The experiment areas named above are illustrative evidence of the kinds of work MADRE must make cheap. They are not an implied product decomposition, roadmap, Module inventory or instruction to create one abstraction per technique. In particular, adding or exposing a technical/inference primitive does not create a semantic domain and therefore does not justify a Module, Agent, Material type or higher-level stable SDK abstraction by itself.

A Module boundary exists only when some coherent application/domain behavior actually owns meaning, state, interpretation, bounded Operations and domain semantics. Technical facilities such as search, embeddings, databases, transports, model APIs or storage may be used internally by such software or supplied by reusable libraries without becoming Modules. A low-level reasoning computation contract may remain exactly that: a typed inference capability below Module semantics. Promote it upward only when a real semantic consumer demonstrates a reusable higher-level abstraction.

Do not recursively reinterpret this into "finish CORE before SDK", "keep inference surfaces minimal because MADRE is model-agnostic", "put model knobs into Kernel", "build a Module for each inference capability", "turn every example into the next roadmap item", or "promote every experiment to stable API". CORE remains an important owner-facing reference Module and UX benchmark, but its current interaction shape is evidence to experiment with rather than a reason to freeze a universal assistant contract prematurely.

## Product and responsibility boundary

- The MADRE host product owns product-management mechanics: installation and uninstallation, persistent product configuration, Module and reasoning-artifact lifecycle, CORE selection, startup/shutdown, diagnostics, health and the owner-facing mechanics needed to operate the installed product.
- A Module owns meaning, domain state, persistence, Material, transformations, Agents, Skills, Agent-owned Workflows, interpretation, continuation, domain-specific integrations, domain-specific UX and bounded Operations. A reusable technical primitive with no coherent semantic/domain ownership is not a Module.
- An Operation is bounded Module/application behavior. Ordinary file, network, database, device, search, MCP or similar I/O remains ordinary Module/application behavior unless a concrete shared Kernel responsibility is established.
- Kernel stays narrow. It owns the live executable Module registry and receiver mechanics plus the shared reasoning runtime: reasoning-mechanism registration/selection, resources, immediate/durable reasoning, retry, cancellation, persistence, delivery and runtime logging.
- Installed Module composition uses caller-bound `ModuleDirectory`/`ModuleInvoker` facades. Runtime assembly binds the canonical caller identity; Module code does not supply a caller identity or arbitrary receiving Privacy. Contract-valid foreign results are receivable only for canonically referenced foreign Material types that can reach fixed `Privacy.MODULE`.
- External/PUBLIC disclosure is a separate host boundary. `PublicResultTransformer` remains mandatory there and is not run merely because one installed Module invoked another.
- `OwnerModuleInvoker` and the external/PUBLIC host invoker remain outside `ModuleContext`. PRIVATE Operations remain Module-internal.
- `ReasoningCapability` is intentionally narrow. It accepts only nominal `ReasoningComputation<R>` values and returns reasoning results. It knows no Module, Agent, Operation, Workflow, Material, external action or semantic continuation. Computation-family concepts do not automatically propagate into the Module object model.
- Reasoning adapters are ordinary owner-installable JVM artifacts discovered independently from Modules through the public reasoning-adapter SPI. `madre-app` must not know concrete reasoning-provider types or parse provider-specific configuration.
- Module installation and reasoning-mechanism installation are distinct. They use separate directories, service-provider contracts and identities.
- Search is not a Kernel capability. SearXNG is an ordinary reusable client for domain Modules or applications that legitimately need web search.
- Provider account/session mechanics remain outside MADRE unless a concrete product requirement later assigns them to an appropriate host or Module boundary.

Kernel may persist opaque bytes originating from a reasoning request. That never transfers Material ownership or semantic meaning to Kernel.

## CORE

CORE is the installation role identifying MADRE's default owner-interaction/coordinator Module. That meaning is useful, but it confers no privilege.

A CORE Module remains an ordinary installed Module. CORE assignment must not change Security Algebra, Operation visibility, Module/owner-local/external-PUBLIC invocation authority, reasoning installation or selection, scheduling, class-loader treatment, installation authority or host invocation authority. Do not introduce a privileged `CoreModule` subtype.

The target owner experience is semantically led by CORE: foreground conversation, reasoning choices, useful delayed semantic follow-up, and ordinary coordination/routing to installed Modules. The host product still owns product-management mechanics. CORE does not install artifacts, own global configuration, manage process lifecycle, become Kernel, or receive host-only invocation ports through `ModuleContext`.

Do not freeze speculative CORE Operation names, a universal surface interface or a UI framework. Use CORE as an important reference consumer for SDK/inference experimentation. Recover stable structural contracts only when repeated real interaction experiments demonstrate that the abstraction deserves to graduate. The present `roles.core` lookup, `interaction.*` binding and `MadreMain` console split are executable transitional behavior, not the final product contract.

## Installation and configuration

Module providers declare their canonical `ModuleId` before materialization and receive immutable owner configuration scoped to that exact identity through the public Module SDK. `madre-app` may extract/deliver the current generic `modules.config[<ModuleId>].*` namespace, but Module-specific keys, parsing, validation and typed settings remain inside the installed Module artifact.

Reasoning-provider configuration now has a demonstrated public ownership boundary. Each installed provider declares a stable `ReasoningProviderId`, owner-facing descriptor metadata and a narrow configurator for repeatable named instances. The current generic host configurator renders only the provider-owned `TEXT`, `INTEGER` and `CHOICE` field metadata it needs, delegates parsing/validation/raw-property mapping to the provider, and owns transactional persistence plus the generic owner commands. Provider discovery remains independent from mechanism materialization, and zero configured/materialized mechanisms remains valid.

The raw `reasoning.*` string representation remains compatibility/developer executable truth, not the ordinary owner setup contract. Do not re-invent provider-specific reasoning configuration in `madre-app`, Kernel or CORE, and do not generalize the proven reasoning descriptor/configurator into a universal settings framework without demonstrated product need.

Generic owner-facing Module configuration remains unfinished. A future Module configurator must not hard-code settings belonging to independently installed Modules. Recover the minimum Module-provider-owned typed metadata from demonstrated owner behavior when that configurator is actually implemented; do not invent a general schema framework in advance.

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

`Privacy.UNKNOWN` is explicit P2 for an applicable third-party boundary outside owner control. It is not missing information and is never inferred from locality or provider identity. `Privacy.MODULE` is the structural P4 receiver contract for declared foreign Material crossing between installed Modules; it is not caller-chosen per invocation.

Security Algebra is a cross-cutting value model, not another runtime service.

## Public engineering model

The implementation is Java 21. Use nominal types, immutable records/final classes, generics, sealed hierarchies only for genuinely closed domains, and small responsibility-specific interfaces. JSON is a codec boundary, never the domain programming model.

Windows and Linux are first-class hosts for the same application, Kernel, SDK, Modules, reasoning-adapter installation, persistence and ordinary execution path. Windows is not a compatibility port of a Unix design.

Module developers may depend on published SDK and relevant computation-contract artifacts, but not on `madre-app` or Kernel runtime implementation classes merely to receive installation configuration, discover/invoke compatible installed Modules or execute bounded Module behavior. Reasoning-adapter developers may depend on the public reasoning SPI and relevant computation contracts, but not on application/Kernel implementation merely to register a mechanism.

The SDK has two lifecycle levels conceptually. Stable public contracts are the small set already justified by durable architecture and compatibility needs. Higher-level semantic authoring/test/optimization facilities may incubate in an explicitly experimental public boundary, where change is expected during 0.x. Experimental code may depend on stable SDK; stable SDK, Kernel and reasoning SPI must not depend on experimental facilities. Promotion into a stable SDK or dedicated stable contract artifact must be explicit and justified by repeated use.

The existing independent SDK/reasoning fixtures prove strong contract isolation. They do not by themselves make MADRE a community-ready development platform. Treat stable consumable publication, documentation, tooling/testkit, packaging conventions and a complete independent developer journey as a product gate to be proven explicitly.

Module-to-Module invocation is proven infrastructure and must remain available. Do not remove it because its generalization preceded real CORE use. Equally, do not keep expanding its public shape without concrete product demand before 1.0.

Do not add containers, VM layers, orchestration systems, hosted services or provider accounts to the mandatory build/CI/product path without a concrete Owner-accepted requirement. Prefer the JDK, checked-in Gradle wrapper, native host execution and the smallest real dependency required by the behavior under test.

Do not restore the removed Python packages class-for-class. In particular, do not restore Material-aware capabilities, a generic `Capability<C,R>` Kernel SPI, universal physical-action dispatch, autonomous input/output surfaces, a universal Module/Agent loop, arbitrary property bags or test-only product Modules.

Tests establish mathematical invariants, public contracts, failure mechanics and real integrations. A fixture may replace an external reasoning mechanism for deterministic tests, but it cannot stand in for claimed product behavior.

## Product gates and sequencing

Native Windows/Linux owner deployment, generic reasoning-provider configuration, the public SDK/testkit/experimental lifecycle, the independent developer journey, and the first heterogeneous inference families are implemented foundations. Preserve them as substrate rather than repeatedly treating them as the next product objective.

The active orchestration priority is to make MADRE a progressively more complete and low-friction framework for ordinary modular agentic software while preserving inference experimentation as an independent lower layer. Choose work from concrete developer/owner friction, not from whichever technique or inference family was most recently mentioned.

Current dependency-oriented priorities are:

1. **Deepen the SDK as an experimentation framework.** Exercise the existing stable SDK through real semantic consumers and independent projects; improve authoring, composition, testing, reasoning orchestration and semantic-engineering ergonomics only where concrete code demonstrates repeated friction. Incubate reusable higher-level helpers experimentally rather than freezing a universal Agent loop, memory system, planner, prompt framework or workflow language.
2. **Use real semantic consumers as evidence.** CORE/owner interaction is an important reference consumer and UX benchmark, and unrelated domain Modules are equally valid evidence when they have real domain ownership. Do not manufacture a new Module merely to demonstrate a technical primitive or inference capability.
3. **Continue heterogeneous inference from demonstrated need.** Add provider/runtime tuning or new portable computation families when actual experiments require them; do not propagate those concepts upward into Module semantics by default.
4. **Complete owner/developer product mechanics from real journeys.** Generic Module configuration, artifact lifecycle/release tooling and related host facilities should be recovered from demonstrated owner/developer requirements rather than generalized pre-emptively.
5. **Promote only proven reusable abstractions.** Broader integrations and semantic-engineering facilities become stable only after repeated use establishes responsibility, value and interoperability.

This is not permission to build speculative frameworks. SDK-first means making experiments cheap and rigorous, not pre-implementing every possible agentic feature. A later outcome may move earlier when a concrete experiment proves it is prerequisite to the current one.

## Delivery discipline

Current executable truth belongs in `docs/implementation-baseline.md`; durable product meaning and Owner reasoning belong in `MADRE.md`; focused architecture belongs in `docs/architecture/`; runnable current instructions belong in `README.md`.

For each coherent change:

1. inspect current public contracts and the affected acceptance journey;
2. identify the owner-visible or developer-visible outcome before inventing abstractions;
3. implement complete behavior across its real boundaries;
4. exercise behavior proportionally, including real execution for real-execution claims;
5. review the changed surface for responsibility leakage and platform/framework assumptions;
6. commit and push a coherent green result;
7. never merge without explicit Owner instruction.

MADRE has no installed-base compatibility obligation for discarded prototypes. Replace incompatible development artifacts directly and preserve unrelated Owner changes.