# MADRE Module SDK and Interoperability

This document defines the public Module programming boundary. `MADRE.md` remains authoritative for product meaning; the Security Algebra document defines the algebra itself.

## Programming model

A MADRE Module is an owner-installed semantic/application boundary implemented with ordinary software. A Java Module does not begin as a metadata graph: the executable `Module` object is the Java author's source of truth and MADRE derives a portable description from it.

The execution-side Java model is:

- `Module`: one semantic/application owner;
- `Agent`: optional Module-owned intelligent semantic actor;
- `StatefulAgent<S>`: optional execution-side specialization for typed Agent-owned state;
- `MaterialType<T>` / `Material<T>`: Java typed semantic values;
- `Operation<I,O>`: executable bounded behavior;
- `OperationBinding<I,O>`: one exact executable binding and, in the current 0.x external/PUBLIC wiring, an optional/required Module-owned public result transformation according to binding kind;
- `OperationCall<I,O>`: one bounded typed Java invocation.

The portable description model is:

- `ModuleDefinition`;
- `AgentDefinition`;
- `MaterialTypeDefinition`;
- `OperationDefinition`;
- `SkillDefinition`;
- `WorkflowDefinition`;
- `EffectProfile`.

The distinction is intentional. Portable descriptions contain semantic/security facts needed for discovery, composition, adapters and validation. They do not encode Java classes, codecs, executable class names, prompts, scripts or arbitrary metadata bags.

`Module.definition()` projects the portable `ModuleDefinition`. `Module.instance()` creates the validated runtime `ModuleInstance` joining that portable contract to exact Java Material and Operation bindings. `ModuleInstance` is runtime/adaptor assembly, not the primary Java authoring API.

## What an Operation is

An Operation is one bounded piece of Module-owned semantic behavior. It is defined by what behavior the Module owns, which Material boundaries participate, which results may be produced, and which consequential execution variants exist.

Three questions must remain separate:

```text
semantic behavior     -> Operation
caller/surface access -> exposure / invocation boundary
reasoning eligibility -> contextual Material Sensitivity vs mechanism receiving Privacy
```

An Operation does not become public because it uses a model. A PRIVATE Operation may request reasoning, including remote reasoning when its actual contextual Material can reach the selected mechanism. A PUBLIC Operation may perform no reasoning at all.

Likewise, changing from local to remote reasoning does not mutate `OperationVisibility`. If a request's contextual Material cannot reach a mechanism's declared receiving Privacy, Kernel cannot select that mechanism. The Module may deliberately transform/minimize information into new Material and make a different bounded request when that is semantically justified. Runtime does not silently lower Sensitivity or change Operation exposure.

The current two-state `OperationVisibility` is a 0.x installed-runtime exposure mechanism. Today, `PUBLIC` is used by generic Module-composition and host invocation wiring; `PRIVATE` is omitted from those generic installed invocation paths. That is executable truth, but it is not a confidentiality label, an external-publication label, an owner-visibility label, or the durable semantic definition of Operation.

## Portable types versus Java typing

`OperationDefinition` is language-neutral and deliberately non-generic. Its facts are Operation identity/purpose/current visibility, accepted Material type to receiving Privacy, produced Material type to maximum Sensitivity, and declared EffectProfiles.

Java `I,O` payload typing belongs to `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>`, where actual Java values exist. A remotely decoded Operation contract therefore does not pretend to know Java payload classes.

Likewise, `MaterialTypeDefinition` contains only nominal `MaterialTypeId` plus content type. Java `MaterialType<T>` adds `Class<T>` and `MaterialCodec<T>` as execution bindings. `ModuleDefinitionJsonCodec` format version 2 serializes the portable description without needing a Java Material resolver.

This separation permits language-neutral discovery/adapters while retaining strong Java typing at executable boundaries.

## Module and Agent authorship

A Module may contain zero Agents. Agent is used only when the Module genuinely owns an intelligent semantic actor; it is not a mandatory wrapper around application code.

An Agent defines no universal receive loop, planner, memory model, prompt format, tool loop or execution context. Its current public semantic facts are identity, purpose, Integrity, Skills, Workflows and associated Operations. An unproven Java Agent defaults to `Integrity.I1`, the lowest ordinary value, rather than inventing stronger assurance. Stronger Integrity is an explicit claim that must be justified by the implementation context.

`StatefulAgent<S>` is an optional execution-side OOP convenience for a concrete Agent that owns typed state. It supplies serialized state reads/transitions and optional commit-before-publish persistence. The concrete Agent/Module owns state meaning, persistence representation and semantic use. It does not add a universal memory model or a second semantic execution route.

`SkillDefinition` is lightweight Module-provided semantic ability/knowledge/instruction metadata. `WorkflowDefinition` is currently an Agent-owned ordered Operation description. Kernel does not interpret it as a scheduler or planner language.

Unknown or experimental behavior remains valid software. Lack of semantic proof reduces the trust/composability claims the Module should make; it is not a reason to fabricate certainty or to make arbitrary local code impossible.

## Module provider boundary

One installable Module artifact exposes a `ModuleProvider` through Java `ServiceLoader`.

Its stable responsibilities are:

```text
moduleId() -> canonical ModuleId
configurationDescriptor() -> owner-facing installation metadata
validateConfiguration(configuration) -> validated/canonicalized same-identity configuration
create(context, configuration) -> executable Module
```

`ModuleProvider.create(...)` returns the executable Module. Host/testkit code derives and validates the `ModuleInstance`. The materialized Module identity must equal the provider identity.

Provider configuration is immutable and identity-scoped. The host does not know Module-specific setting keys or semantic meaning. Configuration metadata is not part of Module semantic definitions or Security Algebra.

Duplicate provider identities, configuration for an uninstalled identity, provider/materialized identity mismatch and invalid runtime assemblies fail before semantic use.

## Runtime assembly invariants

`ModuleInstance` validates by construction that:

- Java Material bindings exactly match the portable Material declarations;
- every Java Material binding is owned by the Module;
- executable Operation bindings exactly match the portable Operations;
- every executable binding is owned by the Module;
- no declared executable Operation is missing and no undeclared binding is present.

The live registry can therefore treat a received `ModuleInstance` as a valid assembly and focus on registration/reachability/invocation responsibility.

`Material` construction itself validates ordinary Sensitivity, Java payload compatibility and that `MaterialId` and `MaterialTypeId` belong to the same Module. Operation output validation remains separate: the result type must be declared by the Operation, owned by its Module and no more sensitive than the declared maximum.

## Caller-bound Module composition

`ModuleContext` contains caller-bound `ModuleDirectory` and `ModuleInvoker` ports. It contains neither generic owner-local nor external/PUBLIC host invocation authority.

The caller identity is bound by installation/runtime assembly. `ReachabilityQuery` does not contain a caller identity or receiver Privacy. `ModuleInvoker.invoke(...)` receives only the exact bounded `OperationCall`. Module code therefore cannot forge its caller identity or choose a weaker receiver boundary.

In the current runtime, directory reachability exposes only installed `PUBLIC` Operations compatible with the offered Material. `PRIVATE` Operations remain unavailable to generic Module-to-Module composition.

The Module-to-Module receiver is fixed at `Privacy.MODULE`. A successful result crosses unchanged only when:

```text
caller declares the returned foreign Material type in publicMaterialReferences
and returned Sensitivity can reach Privacy.MODULE
```

The exact callee Material identity, owner, type and Sensitivity are preserved. The caller may then create a new caller-owned Material representing its own interpretation.

This is ordinary installed application composition. It is not external publication and does not run the external/PUBLIC result transformer.

## Owner interaction, owner-local invocation and external/PUBLIC disclosure

Owner interaction is not the same semantic boundary as Module-to-Module composition or external publication.

A Module may own conversational or other owner-facing behavior that is internal to its application semantics. A local presentation surface can let the Owner use that behavior without implying that the same Operation is a public composition API for arbitrary Modules. MADRE has not yet frozen the final reusable interaction-surface contract.

The current host implementation has two generic invocation ports in addition to Module-to-Module composition:

```text
current Module receiver -> PUBLIC Operation, fixed Privacy.MODULE, no public transform
current Owner-local     -> PUBLIC Operation, host-only, valid Material unchanged
current External/PUBLIC -> PUBLIC Operation, host-only, explicit Module-owned public transform
```

This table is executable 0.x truth, not the target semantic definition of Operation exposure.

The current `OwnerModuleInvoker` resolves an installed `PUBLIC` Operation, executes the canonical bounded call and returns valid Module-created Material unchanged. Requiring `PUBLIC` here is transitional host wiring. It must not be generalized into a rule that all owner-facing behavior must also be public to other Modules.

The current `PublicModuleInvoker` also targets a `PUBLIC` Operation and requires its `PublicResultTransformer`. The transformer must create new declared Material with new nominal identity and Sensitivity capable of reaching `Privacy.PUBLIC`. The durable principle is explicit semantic transformation at an actual external/public disclosure boundary. Coupling that transformation to the same two-state visibility marker used for Module composition is not itself an architectural requirement.

Do not solve owner interaction by making every PRIVATE Operation host-callable. That would erase Module encapsulation in the opposite direction. The correct reusable owner-interaction exposure should be recovered from real product experiments and remain non-privileged with respect to CORE.

## Security Algebra responsibility

Security Algebra attaches only where responsibility applies. Material carries Sensitivity; accepted Operation inputs carry receiving Privacy; Agent carries/derives causal Integrity; consequential Operation variants carry Risk and Autonomy; reasoning mechanisms declare receiving Privacy.

Operation exposure is not another Algebra carrier. `OperationVisibility.PUBLIC` does not mean Material is public, and `PRIVATE` does not imply that reasoning must stay inside one process or machine.

The algebra governs MADRE-mediated semantic information/effect composition. It is not a general sandbox for installed Java code and does not infer values from locality, endpoint, provider, model, process, classloader placement, shipped status or CORE role.

The Owner explicitly chooses software to install. Such software can perform ordinary application/OS behavior inside its responsibility. MADRE's contracts constrain what enters the MADRE composition model; they do not pretend to make arbitrary installed code safe.

## Reasoning boundary

Bounded Module behavior creates a typed `ReasoningComputation<R>` and `ReasoningRequest` from an existing valid `OperationCall`.

The request derives originating Module and carried Sensitivity structurally from that call. It carries only reasoning computation plus execution controls such as priority, timeout, eligibility, cancellation, retry and typed preferences. It does not contain semantic continuation, Material identity/type, Agent/Workflow identity, concrete mechanism identity or Operation Risk.

If several semantic values are combined before reasoning, the Module must create the actual combined/contextual Material with the combined Sensitivity and derive the reasoning request from a call over that Material. The SDK provides no arbitrary carried-Sensitivity override.

Kernel owns compatible reasoning selection, resources, immediate/durable execution, retry/cancellation and opaque persistence. Module owns semantic interpretation, association and continuation. Provider/adapter owns concrete mechanism/model/runtime tuning.

## Durable interpretation

Durable Kernel work is physical reasoning state. Domain meaning of that work remains Module-owned.

The shipped owner-interaction Module demonstrates this split: it keeps its WorkId-to-semantic pending association and typed bounded conversation state, Kernel keeps opaque durable reasoning work, and the Module later interprets a completed result into its own Material before acknowledgement/removal.

There is no generic Kernel callback/continuation object, semantic result router or workflow scheduler.

## Owner interaction and CORE

CORE is an optional installation role assigned to one ordinary installed Module. It is expected to be an important owner-facing/coordinator reference consumer, but it has no private runtime authority.

The current `interaction.*` console binding is independent from `roles.core`. This is a transitional product arrangement, not a universal SDK contract. The console currently maps ordinary text, `/standard` and `/updates` onto configured installed `PUBLIC` Operations, but that does not define the target interaction exposure model.

The shipped owner-interaction Module now owns bounded persisted multi-turn conversation state through a concrete `StatefulAgent<OwnerConversationState>`. Its semantic execution still occurs through ordinary Operation bindings/calls. This proves stateful Agent programming, not a generic conversation/memory API and not a requirement that conversation Operations remain `PUBLIC`.

## Installation/discovery

A Module is currently an ordinary JVM JAR with one canonical `ModuleProvider` for managed installation. Shipped and independently built Modules use the same provider/registration path; bundling, classloader placement and CORE status confer no privilege.

Managed lifecycle distinguishes shipped, lifecycle-managed owner, manual direct-placement and development override sources. Artifact administration is a host responsibility and is absent from `ModuleContext`.

## Independent interoperability proof

`verification/sdk-consumer` builds independently against published public artifacts and implements the same code-first `Module` contract used by shipped Modules. It exercises stable testkit and heterogeneous reasoning contracts without application/Kernel implementation dependencies.

`verification/module-interoperability` separately builds an installed caller and callee. It proves the current caller-bound reachability/invocation behavior, preservation of valid foreign Material, caller-owned interpretation, S5 blocking at `Privacy.MODULE`, undeclared-foreign-type blocking, PRIVATE invisibility to Module composition, current owner-local raw receipt and current external/PUBLIC transformation.

These fixtures are architecture evidence for the boundaries they actually prove. They do not establish that one two-state Operation visibility marker must forever encode every owner/public presentation surface.

## Evolution rule

Stable SDK concepts should be extracted from real Module code, with evidence proportional to abstraction scope, rather than invented to anticipate every possible agentic application. The explicit experimental artifact exists for higher-level incubation; it currently contains no public authoring helper after removal of the obsolete definition-first builder.

A narrow optional OOP specialization can be stable when one substantial real consumer demonstrates a generic orthogonal responsibility and focused tests preserve the invariants. Broader semantic frameworks need broader/repeated evidence.

A new inference capability, reusable library or implementation technique does not by itself justify a new Module, Material, Agent, Workflow or stable SDK abstraction. Semantic ownership defines Module boundaries; demonstrated programming friction justifies convenience abstractions.