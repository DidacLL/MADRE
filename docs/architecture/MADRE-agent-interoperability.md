# MADRE Module SDK and Interoperability

This document defines the public Module programming and interoperability boundary. `MADRE.md` remains authoritative for durable product meaning; `MADRE-security-algebra.md` defines the algebra itself.

## Programming model

A MADRE Module is an independently installable semantic/application boundary implemented with ordinary software. The Java executable `Module` is the author's source of truth; MADRE derives a portable description from it.

The execution-side model is:

- `Module`: one semantic/application owner;
- `Agent`: optional Module-owned semantic actor;
- `StatefulAgent<S>`: optional execution-side convenience for typed Agent-owned state;
- `MaterialType<T>` / `Material<T>`: typed semantic contracts and values;
- `Operation<I,O>`: one bounded executable unit of Module logic;
- `OperationBinding<I,O>`: one exact executable binding and any receiver/product-specific executable adaptation owned by the Module;
- `OperationCall<I,O>`: one bounded typed invocation.

The portable description model is:

- `ModuleDefinition`;
- `AgentDefinition`;
- `MaterialTypeDefinition`;
- `OperationDefinition`;
- `SkillDefinition`;
- `WorkflowDefinition`;
- `EffectProfile`.

Portable descriptions contain only facts needed for discovery, composition, arbitration, adaptation and validation. They do not encode Java implementation classes, codecs, prompts, scripts or arbitrary metadata bags.

`Module.definition()` projects the portable `ModuleDefinition`. `Module.instance()` creates the validated runtime `ModuleInstance` joining that contract to exact Java Material and Operation bindings. `ModuleInstance` is runtime/adaptor assembly, not the primary authoring API.

## Operation ontology

An Operation is one bounded callable execution of Module logic through MADRE. The Module owns its implementation and semantic meaning. Declaring the execution as an Operation lets MADRE apply typed Material contracts, Security Algebra facts and bounded causal/effect arbitration.

Do not define an Operation by who invokes it or where it is presented:

```text
bounded Module execution       -> Operation
cross-Module interface         -> Module exposure
owner/debug host entry         -> host product boundary
owner interaction              -> selected CORE product entry
external/public disclosure     -> explicit public receiver boundary
reasoning eligibility          -> Material Sensitivity vs mechanism Privacy
transport/locality             -> implementation/provider concern
```

`OperationDefinition` deliberately contains no PUBLIC/PRIVATE visibility. Its portable facts are identity/purpose, accepted Material type to receiving Privacy, produced Material type to maximum Sensitivity, and declared EffectProfiles.

Cross-Module discoverability and invocation are owned by the Module interface through `exposedOperations`. External/public disclosure and owner interaction are executable boundary bindings. None of those facts changes whether the underlying bounded execution is an Operation.

Any Operation may use local or remote reasoning. Exposure and presentation never determine reasoning eligibility.

## Portable types versus Java typing

`OperationDefinition` is language-neutral and non-generic. Java `I,O` payload typing belongs to `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>`, where actual Java values exist.

`MaterialTypeDefinition` contains nominal `MaterialTypeId` and content type. Java `MaterialType<T>` adds `Class<T>` and `MaterialCodec<T>` as execution bindings.

`ModuleDefinitionJsonCodec` format version 3 serializes the portable model. Module exposure is represented at the Module boundary through `exposedOperations`; foreign nominal Material contracts are represented through `foreignMaterialReferences`; Operation objects have no visibility field.

## Material value ownership versus nominal type ownership

A concrete Material value and its nominal Material contract have independent ownership:

```text
MaterialId.moduleId      -> Module that created/owns this concrete value
MaterialTypeId.moduleId  -> Module that defines this nominal contract
```

These identities may differ. A Module may create a concrete value conforming to an explicitly referenced foreign nominal contract. The value remains owned by its creator; the defining Module continues to own the nominal contract.

This distinction is generic MADRE ownership semantics, not an integration-specific exception. A callee may return callee-owned Material that conforms to a nominal type defined by its caller. Receiver-boundary checks concern the declared contract and Sensitivity; they do not rewrite concrete value ownership.

Adaptation or minimization creates new Material with new identity and explicit Sensitivity. The source remains unchanged.

## Module and Agent authorship

A Module may contain zero Agents. Agent is used only when the Module genuinely owns semantic agency; it is not a mandatory wrapper around application code.

An Agent owns intent, interpretation, semantic state and continuation appropriate to its Module. There is no universal receive loop, planner, memory model, prompt format, tool loop or execution context.

An agentless Module remains directly callable through its exposed Module interface. When ordinary owner intent must be interpreted semantically, another Module's Agent—normally the Agent in the Module assigned CORE—owns that interpretation, invokes the agentless Module, interprets the result and owns continuation. Kernel does not invent agency on behalf of the callee.

An unproven Java Agent defaults to `Integrity.I1`. Stronger Integrity is an explicit assurance claim that must be justified.

`StatefulAgent<S>` is an optional execution-side OOP convenience. It supplies serialized reads/transitions and optional commit-before-publish persistence without defining a universal memory model or an alternate execution path around Operations.

`SkillDefinition` is lightweight Module-provided ability/knowledge/instruction metadata. `WorkflowDefinition` is currently an Agent-owned ordered Operation description. Kernel does not interpret it as a scheduler language.

## Module provider boundary

One installable Module artifact exposes a `ModuleProvider` through Java `ServiceLoader`.

Its stable responsibilities are:

```text
moduleId() -> canonical ModuleId
configurationDescriptor() -> owner-facing provider metadata
validateConfiguration(configuration) -> validated/canonicalized same-identity configuration
create(context, configuration) -> executable Module
```

Provider configuration is immutable and identity-scoped. The host does not know Module-specific setting meaning. Configuration metadata is not part of Module execution definitions or Security Algebra.

Duplicate provider identities, configuration for an uninstalled identity, provider/materialized identity mismatch and invalid runtime assembly fail before use.

## Runtime assembly invariants

`ModuleInstance` validates that Java Material bindings exactly match Material declarations and are owned by the Module, and that executable Operation bindings exactly match declared Operations and are owned by the Module.

The portable Module definition separately validates that canonical map keys match values, owned declarations use Module identity, foreign nominal Material references do not duplicate owned declarations, each Operation's accepted/produced Material contracts resolve to owned or explicitly referenced nominal types, each exposed Operation is owned by the Module, and Agent/Workflow/Skill references resolve canonically.

Concrete Material creation validates ordinary Sensitivity and Java payload/type compatibility. Operation output validation requires the output nominal type to be declared by the Operation, the concrete value to be owned by the Operation's Module, and output Sensitivity not to exceed the declared maximum. Nominal type ownership need not equal concrete value ownership.

## Caller-bound Module composition

`ModuleContext` contains caller-bound `ModuleDirectory` and `ModuleInvoker` ports. It does not contain generic owner/debug, owner-interaction or external/public host authority.

The caller identity is bound by runtime assembly. `ReachabilityQuery` therefore contains neither caller identity nor a caller-selected receiver Privacy.

The target Module defines its ordinary cross-Module interface through `exposedOperations`. Directory reachability considers only those exact Operations and then applies their accepted-Material Privacy to the offered information.

`ModuleInvoker.invoke(...)` resolves the canonical target binding and executes its ordinary `OperationBinding.invoke(...)`. It does not run a public-disclosure transformer.

The Module-to-Module receiver is fixed at `Privacy.MODULE`. A successful result crosses only when the caller structurally declares the nominal Material contract it will receive in `foreignMaterialReferences` and the result Sensitivity can reach `Privacy.MODULE`.

When valid, the exact callee-created Material crosses unchanged. Its `MaterialId` continues to identify the callee as concrete owner even when its `MaterialTypeId` identifies the caller or another Module as nominal-contract owner.

No caller identity or receiver Privacy is supplied per call. No CORE privilege is involved.

## Generic owner/debug entry

Generic owner/debug invocation is a host-only expert boundary. `OwnerModuleInvoker` resolves an exact installed Operation, executes its canonical bounded call and returns the Module-created Material unchanged.

This entry is not constrained by the Module's cross-Module `exposedOperations`; otherwise host debugging would simply be another Module-composition route. It is also not ordinary product conversation and does not imply external disclosure.

Accepted Material Privacy, EffectProfile arbitration and output validation remain in force because the host still constructs a normal `OperationCall`.

## Owner interaction

Owner interaction is a distinct host/product boundary. An owning Module can bind an exact Operation with `OperationBinding.ownerInteractionOperation(...)`.

`OwnerInteractionInvoker` may enter only such a binding in the currently installed Module selected by `roles.core`. A binding in another Module remains unreachable through this product port even if it is marked as an interaction entry.

The port is host-only and absent from `ModuleContext`; assigning CORE therefore does not let Module code invoke another Module's interaction entries or internal Operations.

Owner-interaction entry is not portable UI taxonomy and does not alter cross-Module exposure or Security Algebra. Every entry still executes through canonical `OperationCall` validation.

## External/public disclosure

Actual external/public disclosure is a separate receiver boundary. `OperationBinding.publicDisclosure(...)` pairs an exact Operation with a Module-owned `PublicResultTransformer`.

`PublicModuleInvoker` can cross this boundary only through a binding that supplies such a transformer. The transformer must create new declared Material with a new identity. The transformed result is revalidated against the Operation output contract and must have Sensitivity capable of reaching `Privacy.PUBLIC`.

This preserves explicit semantic minimization/transformation when information truly becomes public. It is independent from whether the same Operation is listed in `exposedOperations` for ordinary Module composition.

`Privacy.PUBLIC` therefore means an actual information receiver boundary. It is not a Module-exposure bit, owner-visibility level or generic access-control role.

## Security Algebra responsibility

Security Algebra attaches only where the corresponding responsibility exists:

- Material carries Sensitivity;
- accepted Operation input contracts carry receiving Privacy;
- Agent carries/derives causal Integrity;
- EffectProfile carries Risk and Autonomy;
- reasoning mechanisms declare receiving Privacy.

Module exposure, generic host entry, owner-interaction binding, external-disclosure binding and CORE assignment are not Algebra carriers.

The algebra governs MADRE-mediated information/effect composition. It is not a general sandbox for installed Java code and never infers values from locality, endpoint, provider, model, process, classloader placement, shipped status or CORE role.

## Reasoning boundary

A bounded Operation may create a typed `ReasoningComputation<R>` and `ReasoningRequest` from its valid `OperationCall`.

The request derives originating Module and carried Sensitivity structurally from the call. It carries reasoning computation plus execution controls such as priority, timeout, eligibility, cancellation, retry and typed preferences. It does not carry semantic continuation, Agent/Workflow identity, concrete mechanism identity or Operation Risk.

If several values are combined before reasoning, the Module constructs actual contextual Material at the combined Sensitivity and derives the reasoning request from a call over that Material. The SDK provides no arbitrary carried-Sensitivity override.

Kernel owns compatible reasoning selection, resources, immediate/durable execution, retry/cancellation and opaque persistence. Module owns interpretation, association and continuation. Provider/adapter owns concrete model/runtime tuning.

## Durable interpretation

Durable Kernel work is physical reasoning state. Domain meaning remains Module-owned.

The shipped owner-interaction Module demonstrates the split: it owns WorkId-to-pending association and bounded conversation state; Kernel owns opaque durable reasoning work; the Module later interprets a terminal result into its own Material before acknowledgement/removal.

There is no generic Kernel callback/continuation object, semantic result router or workflow scheduler.

## CORE

CORE is an installation role assigned to one ordinary installed Module. It is expected to lead default owner interaction and coordination, but it has no private runtime authority.

The shipped owner-interaction Module owns bounded persisted multi-turn conversation state through a concrete `StatefulAgent<OwnerConversationState>`. Its three current owner-interaction Operations remain ordinary Operations and are not cross-Module exposed merely because their Module is CORE.

Current Operation names and `interaction.*` host bindings are implementation evidence, not a universal assistant/UI protocol.

## Independent interoperability proof

`verification/sdk-consumer` builds independently against published public artifacts and implements the same code-first `Module` contract used by shipped Modules. It exercises the stable testkit and heterogeneous reasoning contracts without application/Kernel implementation dependencies.

`verification/module-interoperability` independently builds an installed caller and callee. It proves Module-owned exposure, caller-bound discovery/invocation, unchanged callee-owned Material receipt, foreign nominal-contract semantics, S5 blocking at `Privacy.MODULE`, undeclared-type blocking and invisibility of unexposed Operations. External/public transformation is independently exercised through the host receiver boundary.

Integration experiments may expose SDK friction, but external application/domain semantics do not become MADRE product dependencies or architecture authority by default.

## Evolution rule

Stable SDK concepts are extracted from real Module code with evidence proportional to abstraction scope. A new inference capability, reusable library or integration technique does not by itself justify a new Module, Material, Agent, Workflow or stable framework concept.

Preserve one coherent runtime and explicit ownership rather than flattening receiver boundaries for local convenience.
