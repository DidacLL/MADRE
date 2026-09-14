# MADRE Module SDK and Interoperability

## Public object model

The SDK is a public Java 21 library under the Owner's namespace. It contains immutable objects with nominal identities and constructor-enforced invariants.

The core Module model is:

- `ModuleDefinition`: identity, version, declared Material types, Agents, Module-provided Skills, Operations and public references;
- `ModuleInstance`: one executable installation of the canonical definition plus exact executable binding for every declared Operation;
- `AgentDefinition`: identity, purpose, applicable Integrity, learned Skills, owned Workflows and exposed Operations;
- `SkillDefinition`: reusable Module-provided ability, knowledge or instruction;
- `WorkflowDefinition`: Agent-owned semantic behavior, currently an ordered sequence of Operations;
- `OperationDefinition`: one bounded callable Module behavior, accepted Material/Privacy, produced Material/maximum Sensitivity and applicable EffectProfiles;
- `OperationBinding`: exact executable implementation of one canonical Operation declaration, including the public-result transformation for `PUBLIC` behavior;
- `EffectProfile`: one consequential Operation variant with Risk and Autonomy;
- `MaterialType<T>` and `Material<T>`: Module-owned semantic values.

Module, Agent, Operation, Skill, Workflow, EffectProfile, Material and MaterialType use nominal identity types where real independent identity exists. Definitions reject unknown/conflicting references; executable Module instances additionally reject missing, undeclared, foreign or non-canonical Operation bindings.

## Executable Module boundary

A Module is a complete application/domain boundary implemented with ordinary Java. An Operation is one bounded callable function of that application. Definitions contain no executable class names, scripts, prompts, provider payloads or arbitrary metadata bags.

Runtime behavior is bound through responsibility-specific SDK interfaces:

- `Operation<I,O>` implements one bounded declared Operation;
- `ModuleInstance` associates one `ModuleDefinition` with exact executable bindings;
- `ModuleProvider` materializes one installable executable Module from `ModuleContext`;
- `ModuleRegistration` registers executable Module behavior;
- `ModuleDirectory` exposes currently reachable public definitions;
- `ModuleInvoker` invokes an exact installed `PUBLIC` Operation across the external/public boundary;
- `OwnerModuleInvoker` is the host/application owner-local invocation port over an exact installed externally callable Operation;
- `ReasoningService` is the Module-facing port only for reasoning work.

There is no universal `receive` method, Agent loop, assistant turn, planner, policy engine, workflow interpreter or generic Kernel action dispatcher.

Ordinary application I/O stays inside Module behavior unless a concrete shared-Kernel responsibility is established. In particular, search is not required to route through Kernel.

## Module installation and discovery

An installable Module is an ordinary JVM JAR containing a Java service provider for `ModuleProvider`. MADRE discovers JARs from the configured Module directory using JDK class-loading and `ServiceLoader` APIs.

Shipped and independently built Modules use the same provider/registration route. Bundling, process placement, class-loader placement and CORE assignment confer no privilege.

The independent verification Module is built in a separate Gradle build against only the published `madre-sdk` artifact. CI installs its JAR into a built distribution, discovers it and invokes its public Operation on both Windows and Linux.

## Canonical Operation execution

`OperationBinding.invoke` accepts only an `OperationCall` that references the exact installed canonical declaration. The Module-owned implementation executes, then the internal result is contract-validated before any receiver-specific transformation: output Material type must be declared, its owner must be the Operation's Module, and its Sensitivity must not exceed the declared maximum.

A real `OperationCall` structurally enforces accepted Material/Privacy, exact EffectProfile selection where applicable and causal Integrity composition before bounded behavior executes. Host adapters do not replace this call with a weaker convenience envelope.

## Owner-local invocation boundary

`OwnerModuleInvoker.invokeOwner` resolves the exact installed Module and canonical Operation binding just as the public path does. It is a host/application receiver boundary for the owner using the local MADRE installation.

Only Operations declared `PUBLIC` are owner-callable. `PRIVATE` remains Module-internal; owner locality does not confer implementation authority.

The owner-local route executes the canonical `OperationCall`, returns the contract-valid Material created by the Module and does not call `PublicResultTransformer`. Result Sensitivity is not lowered merely because the owner receives it locally. No Privacy value is inferred from localhost, same-process execution, class-loader placement, shipped placement or CORE assignment.

`OwnerModuleInvoker` is deliberately not present in `ModuleContext`. Installed Modules receive `ModuleInvoker` for cross-Module PUBLIC invocation only. Application assembly passes facade objects for the directory and public invoker rather than the concrete registry, so a Module cannot obtain the host owner-local port by downcasting a context object.

The current application/console generic adapter decodes input through the exact installed `MaterialType` codec and constructs the canonical call. An Operation with no EffectProfile uses `OperationCall.withoutEffect`. A single declared EffectProfile can be selected without extra ceremony; if multiple variants exist the generic selector uses `<operation>@<effect-profile>`. The host supplies only actual non-user causal participants and does not ask a user to invent Integrity values.

## PUBLIC Operation boundary

`ModuleInvoker.invokePublic` resolves the installed Module and exact canonical Operation binding. Missing Modules, undeclared bindings, forged/non-canonical calls and private Operations are rejected.

A `PUBLIC` binding must provide a Module-owned `PublicResultTransformer`. Internal result Material is validated first; before crossing the external boundary, the transformer must create new declared Material with a new nominal identity and Sensitivity able to reach `Privacy.PUBLIC`.

The transformed Material must still satisfy declared output type, owner and maximum Sensitivity. Returning raw internal Material or a replacement that remains too sensitive is rejected.

Semantic transformation remains Module behavior. Runtime enforcement only prevents bypass of the public boundary. Owner-local invocation never transforms first and attempts to reconstruct sensitive information later; the two receiver boundaries are distinct executions of the same bounded Operation contract.

## Structural algebra in definitions

An Operation directly declares accepted Material types and receiving Privacy. It directly declares produced Material types and maximum Sensitivity. An Agent derives effective Privacy from its exposed Operations. A Module derives effective Sensitivity for exact state from reachable Material and declared outputs.

A Workflow adds no independent security decision. Every Operation call composes from the actual Material entering that Operation.

For one consequential call, `OperationCall.withEffect` binds one exact EffectProfile and the Integrity values of actual non-user causal participants. The causal requirement is `min(Risk, Autonomy)`. If there are no non-user causal participants the existing algebra uses I5. EffectProfile Risk is not forwarded into reasoning work.

Reasoning computation alone does not justify an EffectProfile. The shipped owner-interaction Module therefore declares `standard-prompt` as no-effect. `fast-lane` has `WRITE/AUTONOMOUS` because it creates durable work plus Module-owned persistent pending state that continues after foreground interaction. Its Module-specific `collect-background` has `DELETE/LIVE_INTERACTION` because explicit owner collection acknowledges completed durable work and removes completed pending semantic state.

## Reasoning port

When bounded Module behavior needs reasoning, it constructs a nominal `ReasoningComputation<R>` and creates a `ReasoningRequest` from an existing valid `OperationCall`.

The request derives originating Module and carried Sensitivity from the call. Module code supplies only the reasoning computation plus ordinary execution controls such as priority, timeout, eligibility, cancellation, retry and typed location/latency preferences.

The request does not expose Material identities/types, semantic continuation, a concrete reasoning-mechanism identity or Operation Risk.

This compile-time restriction is intentional: the SDK does not present a generic command envelope that ordinary application effects can reuse accidentally.

## Durable Module interpretation

Kernel durable reasoning stores opaque runtime bytes and stable reasoning-contract/runtime state. Semantic meaning of a pending result remains Module-owned.

The shipped owner-interaction fast lane demonstrates the composition: the Module persists WorkId-to-semantic-state association, Kernel persists opaque reasoning work, process restart rebuilds Module/reasoning installations, a compatible independent reasoning mechanism resumes work, and the installed Module's `collect-background` Operation interprets the completed result into Module Material, acknowledges Kernel work and removes its pending state.

There is no generic Kernel callback, continuation object, background result router or scheduler language.

## CORE assignment

CORE is an optional installation role containing one ordinary `ModuleId`. If configured and installed, the live registry resolves it. If absent or unresolved, MADRE still boots.

CORE changes no Module definition, visibility, owner-local/public invocation authority, algebraic value, reasoning privilege, scheduling privilege or class hierarchy.

## Codecs

Explicit versioned codecs map declarative definitions to JSON boundary representations. Domain classes do not inherit from codec/HTTP framework classes and expose no `Map<String,Object>` extension bag. Executable behavior is never serialized.

Definition codec version 2 nests Workflow declarations inside their owning Agent and preserves Operation order. There is no installed-base compatibility obligation for discarded development formats.

## Search interoperability

`madre-web-search` is a reusable typed value library. `madre-adapter-searxng` is an ordinary Java SearXNG client over that value model.

Neither belongs to the Kernel reasoning SPI. The SearXNG client has no Kernel dependency and does not implement `ReasoningCapability`. The previous standalone WebSearch Module is intentionally removed.

A future domain Module may use the search client directly and own the semantics of search, synthesis, persistence and continuation itself.
