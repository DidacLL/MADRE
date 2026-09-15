# MADRE Module SDK and Interoperability

This document defines the public Module programming boundary. `MADRE.md` remains authoritative for product meaning; the Security Algebra document defines the algebra itself.

## Programming model

A MADRE Module is an owner-installed semantic/application boundary implemented with ordinary software. A Java Module does not begin as a metadata graph: the executable `Module` object is the Java author's source of truth and MADRE derives a portable description from it.

The execution-side Java model is:

- `Module`: one semantic/application owner;
- `Agent`: optional Module-owned intelligent semantic actor;
- `MaterialType<T>` / `Material<T>`: Java typed semantic values;
- `Operation<I,O>`: executable bounded behavior;
- `OperationBinding<I,O>`: one exact executable binding and, for external/PUBLIC behavior, the Module-owned public result transformation;
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

## Portable types versus Java typing

`OperationDefinition` is language-neutral and deliberately non-generic. Its facts are Operation identity/purpose/visibility, accepted Material type to receiving Privacy, produced Material type to maximum Sensitivity, and declared EffectProfiles.

Java `I,O` payload typing belongs to `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>`, where actual Java values exist. A remotely decoded Operation contract therefore does not pretend to know Java payload classes.

Likewise, `MaterialTypeDefinition` contains only nominal `MaterialTypeId` plus content type. Java `MaterialType<T>` adds `Class<T>` and `MaterialCodec<T>` as execution bindings. `ModuleDefinitionJsonCodec` format version 2 serializes the portable description without needing a Java Material resolver.

This separation permits language-neutral discovery/adapters while retaining strong Java typing at executable boundaries.

## Module and Agent authorship

A Module may contain zero Agents. Agent is used only when the Module genuinely owns an intelligent semantic actor; it is not a mandatory wrapper around application code.

An Agent defines no universal receive loop, planner, memory model, prompt format, tool loop or execution context. Its current public semantic facts are identity, purpose, Integrity, Skills, Workflows and exposed Operations. An unproven Java Agent defaults to `Integrity.I1`, the lowest ordinary value, rather than inventing stronger assurance. Stronger Integrity is an explicit claim that must be justified by the implementation context.

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

`ModuleContext` contains caller-bound `ModuleDirectory` and `ModuleInvoker` ports. It contains neither owner-local nor external/PUBLIC host invocation authority.

The caller identity is bound by installation/runtime assembly. `ReachabilityQuery` does not contain a caller identity or receiver Privacy. `ModuleInvoker.invoke(...)` receives only the exact bounded `OperationCall`. Module code therefore cannot forge its caller identity or choose a weaker receiver boundary.

Directory reachability exposes only installed `PUBLIC` Operations compatible with the offered Material. `PRIVATE` Operations remain Module-internal.

The Module-to-Module receiver is fixed at `Privacy.MODULE`. A successful result crosses unchanged only when:

```text
caller declares the returned foreign Material type in publicMaterialReferences
and returned Sensitivity can reach Privacy.MODULE
```

The exact callee Material identity, owner, type and Sensitivity are preserved. The caller may then create a new caller-owned Material representing its own interpretation.

This is ordinary installed application composition. It is not external publication and does not run the external/PUBLIC result transformer.

## Owner-local and external/PUBLIC receivers

The host owns two different invocation ports in addition to Module-to-Module composition.

Owner-local invocation resolves an installed externally callable Operation, executes the canonical bounded call and returns the valid Module-created Material unchanged. It does not lower Sensitivity because the receiver is local, shipped, CORE or same-process.

External/PUBLIC invocation additionally requires the Module's `PublicResultTransformer`. The transformer must create a new declared Material with new nominal identity and Sensitivity capable of reaching `Privacy.PUBLIC`. Runtime validates that transformation but does not invent it.

The three boundaries therefore remain distinct:

```text
Module receiver  -> fixed Privacy.MODULE, no public transform
Owner-local      -> host-only, valid Material unchanged
External/PUBLIC  -> host-only, explicit Module-owned public transform
```

CORE assignment changes none of them.

## Security Algebra responsibility

Security Algebra attaches only where responsibility applies. Material carries Sensitivity; accepted Operation inputs carry receiving Privacy; Agent carries/derives causal Integrity; consequential Operation variants carry Risk and Autonomy; reasoning mechanisms declare receiving Privacy.

The algebra governs MADRE-mediated semantic information/effect composition. It is not a general sandbox for installed Java code and does not infer values from locality, endpoint, provider, model, process, classloader placement, shipped status or CORE role.

The Owner explicitly chooses software to install. Such software can perform ordinary application/OS behavior inside its responsibility. MADRE's contracts constrain what enters the MADRE composition model; they do not pretend to make arbitrary installed code safe.

## Reasoning boundary

Bounded Module behavior creates a typed `ReasoningComputation<R>` and `ReasoningRequest` from an existing valid `OperationCall`.

The request derives originating Module and carried Sensitivity structurally from that call. It carries only reasoning computation plus execution controls such as priority, timeout, eligibility, cancellation, retry and typed preferences. It does not contain semantic continuation, Material identity/type, Agent/Workflow identity, concrete mechanism identity or Operation Risk.

If several semantic values are combined before reasoning, the Module must create the actual combined/contextual Material with the combined Sensitivity and derive the reasoning request from a call over that Material. The SDK provides no arbitrary carried-Sensitivity override.

Kernel owns compatible reasoning selection, resources, immediate/durable execution, retry/cancellation and opaque persistence. Module owns semantic interpretation, association and continuation. Provider/adapter owns concrete mechanism/model/runtime tuning.

## Durable interpretation

Durable Kernel work is physical reasoning state. Domain meaning of that work remains Module-owned.

The shipped owner-interaction Module demonstrates this split: it keeps its WorkId-to-semantic pending association, Kernel keeps opaque durable reasoning work, and the Module later interprets a completed result into its own Material before acknowledgement/removal.

There is no generic Kernel callback/continuation object, semantic result router or workflow scheduler.

## Owner interaction and CORE

CORE is an optional installation role assigned to one ordinary installed Module. It is expected to be an important owner-facing/coordinator reference consumer, but it has no private runtime authority.

The current `interaction.*` console binding is independent from `roles.core`. This is a transitional product arrangement, not a universal SDK contract. The console may map ordinary text, `/standard` or `/updates` onto configured installed Operations, but the semantic behavior remains Module-owned.

The shipped owner-interaction Module is currently still single-turn for foreground reasoning. Its code-first conversion does not constitute a generic conversation/memory design.

## Installation/discovery

A Module is currently an ordinary JVM JAR with one canonical `ModuleProvider` for managed installation. Shipped and independently built Modules use the same provider/registration path; bundling, classloader placement and CORE status confer no privilege.

Managed lifecycle distinguishes shipped, lifecycle-managed owner, manual direct-placement and development override sources. Artifact administration is a host responsibility and is absent from `ModuleContext`.

## Independent interoperability proof

`verification/sdk-consumer` builds independently against published public artifacts and now implements the same code-first `Module` contract used by shipped Modules. It exercises stable testkit and heterogeneous reasoning contracts without application/Kernel implementation dependencies.

`verification/module-interoperability` separately builds an installed caller and callee. It proves caller-bound reachability/invocation, preservation of valid foreign Material, caller-owned interpretation, S5 blocking at `Privacy.MODULE`, undeclared-foreign-type blocking, PRIVATE invisibility, owner-local raw receipt and external/PUBLIC transformation.

These fixtures are architecture evidence, not special privileged Modules.

## Evolution rule

Stable SDK concepts should be extracted from repeated real Module code, not invented to anticipate every possible agentic application. The explicit experimental artifact exists for higher-level incubation; it currently contains no public authoring helper after removal of the obsolete definition-first builder.

A new inference capability, reusable library or implementation technique does not by itself justify a new Module, Material, Agent, Workflow or stable SDK abstraction. Semantic ownership defines Module boundaries; repeated demonstrated friction justifies convenience abstractions.
