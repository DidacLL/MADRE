# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. Its defining runtime capability is model-agnostic reasoning that may continue durably after a useful foreground result has already been returned.

MADRE has two equally important identities:

1. an end-user product the Owner installs, configures, launches and uses without needing to understand Gradle, Java classpaths, ServiceLoader or MADRE's internal property namespaces;
2. an application-development and experimentation platform through which an unrelated developer can consume public artifacts, build/test/package independent Modules and reasoning mechanisms, and iterate on agentic/inference techniques without depending on application or Kernel implementation classes.

MADRE is not a hosted AI platform and does not attempt to protect the owner from software the owner deliberately installs. The owner may install, replace, configure or remove Modules and reasoning mechanisms independently.

## Owner reasoning: why experimentation is a product requirement

This section records the Owner's product reasoning so future wording changes do not accidentally invert the architecture or sequencing.

An acceptable owner UX is not expected to appear from one fixed assistant design chosen in advance. It requires substantial experimentation on two coupled sides.

On the semantic/application side, useful behavior may depend on experiments with Agent definitions, Skills, Workflows, semantic memory, semantic databases and knowledge graphs, retrieval/context construction, prompt/request optimization, coordination patterns, Module composition, and different ways of combining immediate and durable reasoning.

On the inference side, useful behavior may depend on different inference origins and runtimes, different models behind common contracts, richer generation controls, embeddings, multimodal computation, and mechanism-specific optimization. This is especially important for local low-resource environments, where small language models may require careful model/runtime tuning to obtain acceptable results.

Those examples identify experimentation areas, not architectural components that MADRE must mirror one-for-one. They do not imply a roadmap, a mandatory Module inventory or a requirement to lift every inference primitive into Module semantics. A technical capability earns a higher-level abstraction only when real semantic software demonstrates that abstraction's ownership and reuse.

A Module exists because a coherent application/domain owns meaning, state, interpretation, bounded behavior and domain semantics. Search, embeddings, model APIs, databases, transports, storage and similar facilities may be implementation techniques or reusable libraries used by a Module without becoming Modules themselves. Likewise, a typed reasoning computation contract may remain a lower-layer inference primitive indefinitely; its existence alone does not justify Material types, Agents, Workflows or stable SDK concepts around it.

Therefore experimentation ergonomics are not secondary developer convenience. They are part of MADRE's product strategy. MADRE should make it easy to build ordinary local software around heterogeneous inference engines while preserving typed contracts, modularity, owner control and clear responsibility boundaries.

The SDK is the primary experimentation surface for semantic agentic engineering. Public reasoning computation contracts and independently installed adapters/providers are the inference experimentation surface. Higher-level experimental facilities should have an explicit incubation lifecycle and may change during 0.x. Only patterns that survive real experiments and demonstrate value proportional to their scope should graduate into stable SDK APIs or dedicated stable public contract artifacts.

`Model-agnostic` does not mean feature-poor inference or a lowest-common-denominator request object. It means the Kernel does not depend on concrete models, providers or engine implementations. Common portable inference semantics may be represented by rich typed computation contracts when multiple implementations can honor them. Engine/model/runtime-specific tuning remains provider/adapter responsibility. Shared scheduling, selection, resources, retry, cancellation and durable execution remain Kernel responsibility.

This causal reasoning is authoritative product intent. Do not reduce it to a slogan such as "finish CORE first", "keep inference minimal", "put all model controls into Kernel", "build a Module for each reasoning capability" or "turn the latest experiment into the next product layer". CORE is an important owner-facing reference Module and UX benchmark, but it is also a major consumer of the experimentation platform. Its present interaction shape should not prematurely define universal stable SDK contracts.

## Product layering

The durable responsibility model is:

```text
Owner
  |
  +--> MADRE host product-management surfaces
  |      installation / uninstallation
  |      persistent product configuration
  |      Module and reasoning-artifact lifecycle
  |      CORE selection
  |      startup / shutdown
  |      diagnostics / health
  |
  `--> owner interaction surfaces
         semantically led by the ordinary Module assigned CORE
         |
         +--> other installed Modules through ordinary Module composition
         |
         `--> ReasoningService when Module logic needs reasoning
                |
                v
              narrow Kernel reasoning/runtime machinery
                |
                v
              independently installed ReasoningCapabilities
```

Ordinary files, HTTP, databases, search, MCP, devices and similar application I/O remain Module/application responsibilities unless a concrete shared Kernel responsibility is established.

Security Algebra is cross-cutting behavior of values and contracts in this system. It is not another runtime service.

## Host platforms

Windows and Linux are first-class hosts for the same MADRE application, Kernel, SDK, Module installation mechanism, reasoning-mechanism installation mechanism and persistence model. Windows is not a compatibility port of a Unix implementation, and hosted Linux CI is not a product architecture dependency.

A concrete reasoning transport may be platform-specific. That difference remains inside its adapter and does not justify separate Kernel, SDK, Module, Workflow or Security Algebra architectures.

## Building blocks

A **Module** is an owner-installed application or integration. It owns meaning, domain state, persistence, Material, transformations, Agents, Skills, Workflows, interpretation, continuation, domain-specific integrations, domain-specific user experience and bounded Operations. A reusable technical primitive with no coherent semantic/domain ownership is not a Module.

An **Agent** is a Module-owned intelligent actor. It has no universal loop or assistant behavior. Its available behavior comes from learned Skills, Agent-owned Workflows and Operations its Module exposes or uses internally.

A **Skill** is reusable Module-provided ability, knowledge or instruction.

A **Workflow** is reusable Agent-owned semantic behavior. In the current minimal model it is an ordered sequence of Operations. It is not a Kernel scheduler language.

An **Operation** is one bounded execution of Module logic through MADRE. Its purpose is to make that execution participate in MADRE's ordinary typed, modular, trust and Security-Algebra arbitration while the Module retains ownership of the implementation and meaning. Exposure, presentation, external disclosure, reasoning use/locality and transport are separate concerns; none defines whether something is an Operation.

An **EffectProfile** represents one bounded consequential execution variant of an Operation. It carries that variant's Risk and Autonomy. Reasoning computation is not itself a consequential external effect and does not justify an EffectProfile by itself.

A **Material** is a typed Module-owned value with nominal identity, content type, payload and Sensitivity. Adapting information creates new Material with a new identity and explicit Sensitivity; the source remains unchanged.

A **ReasoningCapability** is one executable model/mechanism implementation of a reasoning contract. It exposes only the reasoning-contract, receiving-Privacy, location/latency, resource and availability facts Kernel needs. It knows nothing about Modules, Agents, Workflows, Operations, Material, external actions or semantic continuation.

A **reasoning computation contract** is a nominal typed description of one portable family of inference work and its result. The current public families include preserved text inference (`madre.text-inference.v1`), richer text generation (`madre.text-generation.v2`) and text embeddings (`madre.text-embedding.v1`). These are inference contracts, not Module-domain concepts. Future computation families such as multimodal work may be added when real inference experiments establish their portable semantics.

A **reasoning adapter** is an independently installable JVM artifact that provides one or more configured `ReasoningCapability` instances through the public reasoning-adapter SPI. Installation does not imply mechanism enablement.

**Kernel** is deliberately narrow. It owns live executable Module registration and receiver mechanics plus shared reasoning-runtime responsibilities: reasoning-mechanism registration and deterministic selection, resources, immediate/durable execution, retry, cancellation, persistence, result delivery and ordinary runtime logging. Kernel may resolve which ordinary installed Module is assigned CORE; that resolution does not make CORE privileged.

The **MADRE host product** owns product-management mechanics that should not be pushed into CORE: installation/uninstallation, persistent configuration, artifact lifecycle, CORE selection, startup/shutdown, diagnostics, health and owner-facing presentation mechanics.

The **SDK** supplies the strongly typed Module construction model and responsibility-specific ports, including executable provider/registration contracts, Module composition, host invocation boundaries and Module-facing reasoning. It is also the primary place where developer ergonomics for semantic experimentation are built. Stable SDK contracts remain deliberately small; higher-level authoring/testing/semantic-engineering facilities may incubate experimentally until evidence proportional to their scope justifies stable promotion. Domain objects are Java objects; JSON is only a boundary representation.

Modules and reasoning mechanisms are distinct installation concepts. CORE designation has no relation to reasoning installation or selection privilege.

## CORE

CORE is the installation role identifying MADRE's default owner-interaction/coordinator Module. It is semantically meaningful but non-privileged.

A Module assigned CORE is still an ordinary Module. CORE assignment changes no Security Algebra value, generic Operation exposure, Module-composition authority, reasoning selection, scheduling, class-loader treatment, installation authority or host authority. There is no privileged `CoreModule` subtype.

The target CORE responsibility is to lead ordinary agentic owner interaction: foreground conversation, reasoning choices, useful delayed follow-up, and coordination/routing to installed Modules through ordinary Module composition. It may own interaction state and semantics appropriate to that role. It does not own product installation, global lifecycle or host administration.

CORE is also a primary reference experiment for MADRE's SDK and inference surfaces. Improving its UX should exercise real Module/Agent/Operation engineering and heterogeneous inference capabilities. Stable public interaction abstractions should be recovered from repeated successful experiments rather than frozen directly from the current console or shipped Operation names.

The exact behavior/surface that structurally qualifies a Module for CORE remains intentionally unfrozen until experiments demonstrate the smallest reusable contract worth stabilizing. Do not invent role-specific APIs, exact universal Operation names or a generic UI/surface framework merely to eliminate transitional wiring.

The installed product now uses `roles.core` as the single Module identity for ordinary owner interaction. Current `interaction.*` properties bind replaceable host presentation mechanics to exact Operations/Material types on that selected Module; they are not a second Module-selection identity. This does not make CORE privileged, and the exact presentation keys/names remain 0.x implementation detail.

## Module installation and configuration

Executable Modules are discovered as JVM JARs exposing `ModuleProvider`. Each provider declares its canonical `ModuleId` before materialization and receives an immutable `ModuleProviderConfiguration` scoped to that same identity.

Installed providers expose the implemented stable `ModuleConfigurationDescriptor` / `ModuleConfigurationField` metadata and `validateConfiguration(...)` contract without Module materialization. The host renders this metadata generically through `madre modules list`, `inspect`, `configure`, local-file install/replace and uninstall/purge without materializing Modules for configuration/lifecycle commands. Stable field kinds are currently only demonstrated `TEXT`, `INTEGER`, `CHOICE`.

Reasoning-provider configuration is separately implemented through provider-owned descriptors/configurators for repeatable named instances. Zero configured/materialized mechanisms remains valid.

Do not generalize these proven domain-specific contracts into one universal settings/property framework without demonstrated need.

Managed artifact lifecycle distinguishes `shipped`, lifecycle-managed `owner`, manually placed `manual`, and development override sources. Manual files remain discoverable but are not silently adopted/replaced/deleted. Shipped artifacts are protected. Module semantic state and Kernel durable reasoning work are not artifact bytes and are not deleted by uninstall.

## Operation exposure, receiver boundaries and reasoning boundaries

MADRE must not collapse these independent questions:

1. What bounded execution of Module logic is being arbitrated? That is the Operation.
2. Which caller or presentation surface may enter it? That is an exposure/invocation concern.
3. Which reasoning mechanism may receive Material used by it? That is governed by actual contextual Material Sensitivity and the mechanism's explicit receiving Privacy.

The current Java SDK has a two-state `OperationVisibility` (`PUBLIC` / `PRIVATE`). `PUBLIC` is the generic installed-operation reachability marker; `PRIVATE` excludes an Operation from generic installed invocation. This representation must not be interpreted as a confidentiality label, external publication, owner visibility, reasoning locality or the definition of Operation.

**Module-to-Module invocation** is ordinary installed application composition. `ModuleContext` supplies a caller-bound `ModuleDirectory` and `ModuleInvoker`; Module code supplies neither caller identity nor receiver Privacy. The current runtime exposes only target `PUBLIC` Operations whose accepted Material can receive the offered information. Before result delivery, the runtime requires the caller to canonically reference the foreign Material type and requires the returned Sensitivity to reach fixed `Privacy.MODULE`. When valid, the exact callee Material crosses unchanged. `PublicResultTransformer` is not run on this receiver path.

**Owner interaction/local presentation** is not semantically the same thing as Module-to-Module publication. The installed product therefore has a separate host-only owner-interaction entry. An owning Module may bind an exact PRIVATE Operation with `OperationBinding.ownerInteractionOperation(...)`; only those exact bindings are reachable through `OwnerInteractionInvoker`. Generic PRIVATE Operations remain unreachable, the port is not supplied through `ModuleContext`, and CORE assignment grants no authority to invoke another Module's PRIVATE Operations. The host still enters the bounded execution through a canonical `OperationCall`, so ordinary Privacy/EffectProfile/output validation remains in force.

This executable distinction is deliberately narrow. It is evidence from the first sustained owner deployment, not a universal presentation/exposure enum or role-specific CoreModule API.

**Generic owner/debug invocation** remains a separate host-only path over PUBLIC Operations and returns valid Module Material unchanged. It exists for expert/debug use and is not the ordinary conversation boundary.

**External/PUBLIC disclosure** is another receiver boundary. `PublicModuleInvoker` targets a `PUBLIC` Operation and applies its `PublicResultTransformer`; the transformer must create new declared Material with new nominal identity and Sensitivity capable of reaching `Privacy.PUBLIC`. Explicit semantic transformation at actual public disclosure is durable.

**Reasoning execution** is independent again. A PRIVATE Operation may request reasoning. If contextual Material can reach an eligible local or remote mechanism's declared receiving Privacy, Kernel may select it. If it cannot, the request is not compatible. The Module may deliberately create new minimized/derived Material and issue a different request; neither Kernel nor Operation visibility/presentation silently lowers Sensitivity.

## Current local interaction implementation

The present local text console is a replaceable `madre-app` adapter implemented by `MadreMain`. It owns the current read/evaluate command loop, presentation timing, generic debug commands and session Sensitivity selection.

`LocalInteractionBinding` resolves current `interaction.*` Operation/Material configuration against the exact Module selected by `roles.core`. Ordinary text and `/standard` enter only explicit owner-interaction bindings. `/updates` remains available for compatibility/debugging but is not required for normal delayed follow-up.

The application does not interpret Kernel reasoning bytes. The shipped owner-interaction Module owns bounded persisted conversation state, contextual Material construction, foreground/background reasoning choices, pending durable-work association, interpretation, acknowledgement and the decision whether a useful owner-visible follow-up exists. Kernel persists only opaque reasoning-runtime state.

While the owner interaction surface is active, host presentation polls the Module's bounded collection Operation. If the Module decides that completed work produces a useful follow-up, the host presents it. After restart, Module-owned conversation/pending state and Kernel durable work recover independently and rejoin through ordinary Module logic; useful recovered follow-up can therefore re-enter the owner interaction without requiring `/updates`.

The existing bounded Operations (`standard-prompt`, `fast-lane`, `collect-background`), poll timing and text update representation are experimentation evidence. Their exact names and shape are not frozen as universal CORE or UI APIs.

## Security Algebra

MADRE has five distinct ordered carriers:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity combine by minimum. Information can reach a receiver iff accumulated Sensitivity is no greater than accumulated Privacy.

For one EffectProfile the non-user causal demand is `min(Risk, Autonomy)` and must be supported by the minimum Integrity of actual non-user causal participants, or I5 when none exist. Values from different EffectProfiles do not combine.

Risk is not propagated into reasoning requests. Reasoning mechanisms do not become action realizers merely because reasoning contributed to a Module decision. `Privacy.MODULE` is the fixed structural receiver for declared foreign Material between installed Modules. There is no OWNER Privacy shortcut.

Operation exposure and owner-interaction presentation binding are not Algebra carriers. Neither determines Material Sensitivity or grants a reasoning mechanism permission to receive information. Reasoning eligibility remains the independent `Sensitivity <= receiving Privacy` relation for the actual contextual Material and selected mechanism.

Security Algebra never infers values from localhost, provider identity, model name, endpoint, process/classloader placement, shipped/bundled status, CORE designation or Operation visibility/presentation binding.

## Reasoning execution and persistence

A Module creates a nominal `ReasoningComputation<R>` from a valid bounded `OperationCall` and submits a `ReasoningRequest` through `ReasoningService`. The request derives originating Module and carried Sensitivity and contains only reasoning execution controls such as mode, priority, timing, timeout, cancellation, retry and typed selection preferences.

It contains no Material identity/type, Agent, Workflow, Operation identity, concrete reasoning mechanism, semantic continuation or future output Material. It also contains no Operation Risk.

The reasoning architecture deliberately separates three kinds of variability:

- **portable request semantics** belong to a typed reasoning computation contract when meaningful across multiple implementations;
- **mechanism/model/runtime tuning** belongs to the reasoning provider/adapter and its owner configuration;
- **shared execution mechanics** belong to Kernel.

A computation family being available does not create an application responsibility above it. Modules may use a computation when their logic requires it, but Kernel and the SDK do not manufacture domain meaning around that computation.

Kernel selects compatible reachable mechanisms, coordinates resources, executes immediate or durable work and persists durable runtime state in SQLite. Persisted reasoning input/output remains opaque to Kernel semantics. After restart, compatible reasoning mechanisms can resume queued work; the originating Module remains responsible for interpretation and any continuation.

A Module owns semantic/domain persistence. The shipped owner-interaction Module's conversation/pending state and Kernel's durable work store are intentionally separate persistence domains.

## SDK experimentation lifecycle

MADRE needs an explicit distinction between stable contracts and experimental developer facilities.

Stable public SDK/contract artifacts contain concepts already justified by durable ownership and interoperability requirements. They should change deliberately because independently developed Modules and adapters may depend on them.

Experimental SDK facilities are an incubation channel for higher-level authoring/testing/semantic-engineering techniques. They may include helpers for Agent/Workflow construction, harness engineering, context/request optimization, semantic-store integration or similar capabilities only when concrete experiments require them. These are reusable software facilities, not automatic Module boundaries. Experimental facilities may evolve or disappear during 0.x and must not become dependencies of Kernel or the stable SDK merely by existing.

Graduation is explicit and evidence is proportional to scope. Broad semantic frameworks require repeated diverse use and clear ownership. A narrow optional OOP specialization may stabilize from one substantial real consumer when it captures a generic orthogonal programming responsibility, materially reduces ceremony and focused tests preserve MADRE's execution/security invariants without imposing domain semantics on unrelated Modules.

This lifecycle is how MADRE can remain solid while still being a productive playground for agentic engineering.

## Public development platform status

The current SDK/reasoning SPI are a strong public-contract foundation. Independent Gradle builds compile Modules and reasoning adapters against published MADRE artifacts only; installed-distribution acceptance proves discovery, Module-to-Module composition, distinct host receiver boundaries and independent reasoning execution on Windows and Linux.

The SDK foundation includes aligned public artifacts, deterministic `madre-sdk-testkit`, an explicit `madre-sdk-experimental` incubation artifact, and a complete independent external-directory Module journey. Heterogeneous inference demonstrates multiple stable computation families and generic value-level mechanism compatibility without expanding Module semantics or making Kernel model-specific. `StatefulAgent<S>` is the current narrow stable optional specialization recovered from substantial owner-interaction code; it owns typed state mechanics but no universal memory/conversation semantics.

The installed owner product now provides a coherent sustained experiment through the shipped interaction Module plus independently configured reasoning mechanisms. Native acceptance uses the shipped OpenAI-compatible provider against a deterministic loopback endpoint to exercise provider-owned configuration, actual mechanism selection by carried Sensitivity, multi-turn conversation, durable work, process restart and natural Module-approved follow-up without internet credentials.

That evidence is necessary but not sufficient for a mature experimentation/developer product. MADRE still needs continued SDK refinement from real developer use, external release/versioning mechanics, and evidence from multiple unrelated semantic applications. The goal is not to accumulate framework concepts; it is to make ordinary modular agentic software easy to build, test, package and evolve.

Optional future standard libraries for ordinary application I/O, MCP, UI, audio, testing or similar developer ergonomics may belong above Kernel when concrete use demonstrates value. Semantic databases, knowledge graphs, retrieval and memory facilities likewise belong in Module/SDK/application layers unless a distinct shared-runtime responsibility is demonstrated; their usefulness does not make them Kernel services, and their existence does not itself justify a dedicated Module.

## Current development posture

Native Windows/Linux owner deployment, generic Module/reasoning-provider configuration, local Module/reasoning artifact lifecycle, the SDK/testkit/experimental lifecycle, the independent developer journey, preserved durable text inference, richer text generation and text embeddings are implemented foundations.

The active priority is to make MADRE a progressively more complete and low-friction framework for ordinary modular agentic software. Development should start from concrete programming and owner/developer friction, not from a recently added inference primitive or from a list of fashionable agentic techniques.

Use these rules when selecting the next slice:

1. **Deepen the SDK from real use.** Exercise stable contracts through existing consumers such as the owner-interaction Module and through genuinely independent domain software. Improve authoring, composition, testing, reasoning orchestration and semantic-engineering ergonomics only where concrete code demonstrates real friction.
2. **Incubate reusable techniques without inventing domains.** Higher-level helpers may enter `madre-sdk-experimental` when they simplify real code while still producing/consuming the stable object model. A helper, library or inference capability does not become a Module unless an actual application/domain owns its semantics.
3. **Keep inference evolution independent.** Provider/runtime tuning and new portable computation families continue when concrete experiments require them. They do not become Module architecture merely because they are available.
4. **Use CORE as evidence, not authority.** CORE/owner interaction is a major UX benchmark and a useful SDK consumer, but current Operation names and presentation wiring do not define universal stable contracts.
5. **Complete host/developer mechanics from real journeys.** Release/versioning tooling and related remaining product facilities should be recovered from demonstrated owner/developer needs.
6. **Promote only proven abstractions.** Evidence must match abstraction scope: broad semantic abstractions require broad/repeated evidence; narrow optional execution-side conveniences may stabilize from a substantial real consumer plus focused invariant tests when responsibility is generic and orthogonal.

These are responsibility rules, not a feature checklist. Research on QVAC, AAAAT, OpenWhispr and other external systems remains evidence only unless the Owner explicitly accepts a product direction.
