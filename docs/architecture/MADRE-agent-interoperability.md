# MADRE Module SDK and Interoperability

## Public object model

The SDK is a public Java 21 library under the Owner's namespace. It contains immutable objects with nominal identities and constructor-enforced invariants.

The core Module model is:

- `ModuleDefinition`: identity, version, declared Material types, Agents, Module-provided Skills, Operations and references to public Material type declarations owned by other Modules;
- `ModuleInstance`: one executable installation of the canonical definition plus exact executable binding for every declared Operation;
- `AgentDefinition`: identity, purpose, applicable Integrity, learned Skills, owned Workflows and exposed Operations;
- `SkillDefinition`: reusable Module-provided ability, knowledge or instruction;
- `WorkflowDefinition`: Agent-owned semantic behavior, currently an ordered sequence of Operations;
- `OperationDefinition`: one bounded callable Module behavior, accepted Material/Privacy, produced Material/maximum Sensitivity and applicable EffectProfiles;
- `OperationBinding`: exact executable implementation of one canonical Operation declaration, including the external/PUBLIC result transformation for `PUBLIC` behavior;
- `EffectProfile`: one consequential Operation variant with Risk and Autonomy;
- `MaterialType<T>` and `Material<T>`: Module-owned semantic values.

Module, Agent, Operation, Skill, Workflow, EffectProfile, Material and MaterialType use nominal identity types where real independent identity exists. Definitions reject unknown/conflicting references; executable Module instances additionally reject missing, undeclared, foreign or non-canonical Operation bindings.

`ModuleDefinition.publicMaterialReferences` names public type declarations owned elsewhere; `public` in this name does not classify all values of that type as S1. The same declaration is the structural opt-in that permits an installed Module receiver to accept contract-valid Material of that foreign type, subject to the fixed `Privacy.MODULE` receiving boundary.

## Executable Module boundary

A Module is a complete application/domain boundary implemented with ordinary Java. An Operation is one bounded callable function of that application. Definitions contain no executable class names, scripts, prompts, provider payloads or arbitrary metadata bags.

Runtime behavior is bound through responsibility-specific SDK interfaces:

- `Operation<I,O>` implements one bounded declared Operation;
- `ModuleInstance` associates one `ModuleDefinition` with exact executable bindings;
- `ModuleProvider` declares one canonical installable `ModuleId` and materializes that executable Module from `ModuleContext` plus identity-scoped `ModuleProviderConfiguration`;
- `ModuleRegistration` registers executable Module behavior;
- `ModuleDirectory` exposes currently reachable `PUBLIC` declarations through a caller-bound facade;
- `ModuleInvoker` invokes an exact installed `PUBLIC` Operation with another installed Module as the receiver;
- `PublicModuleInvoker` is the host-side external/PUBLIC invocation port that requires semantic public transformation;
- `OwnerModuleInvoker` is the host/application owner-local invocation port over an exact installed externally callable Operation;
- `ReasoningService` is the Module-facing port only for reasoning work.

There is no universal `receive` method, Agent loop, assistant turn, planner, policy engine, workflow interpreter, interaction surface or generic Kernel action dispatcher.

Ordinary application I/O stays inside Module behavior unless a concrete shared-Kernel responsibility is established. In particular, search is not required to route through Kernel.

## Module installation and discovery

An installable Module is an ordinary JVM JAR containing a Java service provider for `ModuleProvider`. MADRE discovers JARs from the configured Module directory using JDK class-loading and `ServiceLoader` APIs.

Shipped and independently built Modules use the same provider/registration route. Bundling, process placement, class-loader placement and CORE assignment confer no privilege.

The independent verification Module is built in a separate Gradle build against published MADRE artifacts. CI installs its JAR into a built distribution, discovers it and invokes its public Operation on both Windows and Linux.

A separate `verification/module-interoperability` Gradle build produces an independently compiled caller JAR and callee JAR. Both depend only on the published SDK. CI installs both into the built distribution and exercises real in-process Module-to-Module composition on Windows and Linux.

## Module provider configuration contract

`ModuleProvider` has two installation responsibilities that are deliberately public and small:

```text
moduleId() -> canonical ModuleId
create(ModuleContext, ModuleProviderConfiguration) -> ModuleInstance
```

The declared identity is used before materialization. It is not derived from provider class name, JAR name, service discovery order, bundled/shipped status or CORE assignment. The returned `ModuleInstance.definition().id()` must equal the declared identity.

`ModuleProviderConfiguration` is immutable and carries exactly one `ModuleId` plus read-only string key/value settings. It is not a schema language, secret store, dependency-injection container, account/session model or dynamic configuration service. The SDK imposes no Module-specific key names.

Application properties use the generic form:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The exact identity appears between `[` and `]`. This delimiter is safe for every valid current `ModuleId`: the identity grammar permits letters, digits, dots, dashes and underscores but not square brackets. Consequently dotted identities are never split into guessed namespace segments, and identities that are prefixes of other identities remain independent.

`madre-app` extracts only this generic namespace and hands each installed provider the keys inside its exact identity scope. Each provider owns supported-key validation, value parsing, typed configuration and omitted-value defaults. A key for an uninstalled Module identity is a startup error rather than an ignored typo.

Provider identities are canonicalized before any materialization. Duplicate provider identities fail before provider code runs. For each canonical provider identity the application obtains a Module-specific `ModuleContext`; the runtime binds that exact identity into the supplied `ModuleDirectory` and `ModuleInvoker`. Providers are materialized in canonical identity order and every returned instance validates its bindings before registration begins. A materialized identity mismatch is rejected, so a provider cannot turn a context bound for one identity into authority for another installed Module. Materialization/configuration failure leaves no Module registered. If registration later fails, already-created registrations are closed in reverse order.

CORE and local interaction presentation are not inputs to this contract. Changing `roles.core` or adding/removing `interaction.*` cannot change which Module configuration is delivered or which caller identity is bound into a Module context.

The independently compiled `verification/sdk-consumer` consumes the contract directly and changes its observable `phd.module/inspect` output when `modules.config[phd.module].result-prefix` is present. With that configuration omitted it preserves the previous output exactly. It has no `madre-app` or Kernel implementation dependency.

## Canonical Operation execution

`OperationBinding.invoke` accepts only an `OperationCall` that references the exact installed canonical declaration. The Module-owned implementation executes, then the internal result is contract-validated before any receiver-specific boundary: output Material type must be declared, its owner must be the Operation's Module, and its Sensitivity must not exceed the declared maximum.

A real `OperationCall` structurally enforces accepted Material/Privacy, exact EffectProfile selection where applicable and causal Integrity composition before bounded behavior executes. Host adapters and Module invokers do not replace this call with a weaker convenience envelope.

## Module-to-Module receiver boundary

`ModuleContext` contains a caller-bound `ModuleDirectory` and `ModuleInvoker`. It contains neither `OwnerModuleInvoker` nor `PublicModuleInvoker`, and it never exposes the concrete live registry.

Caller identity is established by installation/runtime assembly. `ReachabilityQuery` contains only the Material type and Sensitivity being offered; it contains no `ModuleId` field. `ModuleInvoker.invoke` contains only the exact `OperationCall`; it contains no caller-identity or receiver-Privacy argument. The bound facade therefore supplies the actual calling Module identity structurally rather than trusting Module code to claim one.

Directory reachability still applies the target Operation's declared accepted-Material Privacy and only exposes `PUBLIC` Operations. A caller may offer one of its own Material types, or a foreign Material type already declared by its canonical `publicMaterialReferences` and able to reach the Module receiver boundary. `PRIVATE` Operations are never returned.

For invocation, the live registry resolves the exact installed target binding and rejects non-canonical or `PRIVATE` calls. The callee Operation executes through ordinary `OperationBinding.invoke`, not `invokePublic`, so its contract-valid internal Material is not semantically rewritten merely because another installed Module called it.

Before completing the caller-facing stage, the registry verifies that the calling Module's canonical `publicMaterialReferences` contains the returned foreign Material type and that the returned Sensitivity can reach the fixed Module receiver Privacy:

```text
returned type is declared by caller as a foreign reference
and returned Sensitivity <= Privacy.MODULE
```

The calling Module cannot supply or relax that Privacy value. If the check succeeds, the exact callee Material object crosses the receiver boundary with the same identity, owner and Sensitivity. If the type is undeclared or the value is S5, the stage fails before Material is exposed to caller code. The caller may then semantically interpret that foreign Material and create a new caller-owned Material with a new identity and an explicit Sensitivity.

This is ordinary installed application composition, not owner-local authority and not external/public disclosure. It introduces no new Operation visibility, Security Algebra value, policy evaluator, transport or CORE privilege.

## Owner-local invocation boundary

`OwnerModuleInvoker.invokeOwner` resolves the exact installed Module and canonical Operation binding. It is a host/application receiver boundary for the owner using the local MADRE installation.

Only Operations declared `PUBLIC` are owner-callable. `PRIVATE` remains Module-internal; owner locality does not confer implementation authority.

The owner-local route executes the canonical `OperationCall`, returns the contract-valid Material created by the Module and does not call `PublicResultTransformer`. Result Sensitivity is not lowered merely because the owner receives it locally. No Privacy value is inferred from localhost, same-process execution, class-loader placement, shipped placement or CORE assignment.

`OwnerModuleInvoker` is deliberately not present in `ModuleContext`. Installed Modules receive only their caller-bound Module receiver ports plus reasoning and state-directory access. Application assembly never hands Module code the owner-local host port.

The current application/console generic adapter decodes input through the exact installed `MaterialType` codec and constructs the canonical call. An Operation with no EffectProfile uses `OperationCall.withoutEffect`. A single declared EffectProfile can be selected without extra ceremony; if multiple variants exist the generic selector uses `<operation>@<effect-profile>`. The host supplies only actual non-user causal participants and does not ask a user to invent Integrity values.

## Application-local interaction presentation

Convenient local text interaction is deliberately not part of the SDK contract. `madre-app` owns an optional immutable presentation binding that maps console conventions onto the existing installed canonical Operation machinery.

The binding is configured with nominal installed identities in `interaction.*`. Resolution happens after Module discovery and reuses the generic operation/profile-selection rules. It validates that the configured Module exists; each configured Operation exists and is `PUBLIC`; each configured input Material type is Module-owned, declared and accepted; prompt/update Sensitivities are ordinary and reachable at the exact receiving Privacy; and all input and declared output Material used by the presentation are String/text types. A configured update path also requires its complete operation/material/payload/sensitivity tuple and validates that the payload decodes through the configured Material codec. Invalid bindings fail application startup.

This validation is intentionally local to one presentation binding. It is not Module certification, a role hierarchy or a new SDK base type. `madre-app` production Java contains no concrete owner-interaction implementation reference or hard-coded shipped owner-interaction Module, Operation, Material, EffectProfile identity or configuration field. The shipped example configuration may name those identities and Module-owned settings as installation policy.

When configured, ordinary non-command console text passes through the same canonical decode/call path and then `OwnerModuleInvoker`, so it remains owner-local. `/standard` is another configured owner-local mapping. `/updates` invokes only the configured Module-specific Operation; it does not expose Kernel result bytes or move interpretation into the application. The application performs no destructive update polling.

The current prompt Sensitivity is explicit console state initialized from configuration. `/sensitivity S1..S5` changes it explicitly; no automatic classifier is implied and `SYSTEM_RESERVED` remains rejected. Returned owner-local Material is rendered at its actual Sensitivity.

The generic `/invoke-owner` and `/invoke-public` paths remain distinct and available. Ordinary text does not alias PUBLIC. The legacy `/invoke` alias remains PUBLIC. With no configured interaction binding, the generic console remains the complete application surface.

`interaction.module` and `roles.core` are independent. CORE does not supply the owner-local port and is not consulted by binding resolution. A Module's use as the local presentation target therefore creates no Module-facing authority or subtype. Neither setting changes the provider configuration delivered to that Module.

## External/PUBLIC Operation boundary

`PublicModuleInvoker.invokePublic` is the host-side external/public receiver boundary. It resolves the installed Module and exact canonical Operation binding. Missing Modules, undeclared bindings, forged/non-canonical calls and private Operations are rejected.

A `PUBLIC` binding must provide a Module-owned `PublicResultTransformer`. Internal result Material is validated first; before crossing the external boundary, the transformer must create new declared Material with a new nominal identity and Sensitivity able to reach `Privacy.PUBLIC`.

The transformed Material must still satisfy declared output type, owner and maximum Sensitivity. Returning raw internal Material or a replacement that remains too sensitive is rejected.

Semantic transformation remains Module behavior. Runtime enforcement only prevents bypass of the external/public boundary. Module-to-Module and owner-local invocation never transform first and attempt to reconstruct sensitive information later; all three receiver boundaries execute the same bounded Operation contract but apply the receiver semantics that actually correspond to that call.

## Independent installed interoperability proof

`verification/module-interoperability/callee` and `verification/module-interoperability/caller` are separate Gradle subprojects built against `io.github.didacll:madre-sdk` from the isolated published repository. They import neither `madre-app` nor Kernel implementation classes and are installed as separate ServiceLoader JARs into the built distribution.

The caller owns `interop.caller/request` and passes that Material into the callee's exact installed `PUBLIC` `sensitive` Operation. The callee declares that foreign input type, creates `interop.callee/sensitive-result` at S4 and returns it internally. The caller canonically references that foreign result type, receives the same callee identity/owner/S4 Material through its bound `ModuleInvoker`, interprets `classified:hello`, and creates a new S4 `interop.caller/adapted-result` with a different caller-owned Material identity.

The same callee `sensitive` Operation invoked through the owner-local host path yields its raw S4 `classified:hello` result. Invoked through the external/PUBLIC host path, its mandatory transformer yields only new S1 `public:callee-summary` Material. The caller's own adapted S4 result likewise becomes only `public:caller-summary` on its external/PUBLIC path.

Negative installed cases are part of the same acceptance. A callee S5 result of a canonically referenced foreign type fails the fixed `Privacy.MODULE` reachability check before caller exposure. A lower-sensitivity result of an undeclared foreign type is also blocked. The caller's bound directory cannot discover the callee's `PRIVATE` Operation. Kernel tests additionally prove a canonical PRIVATE call is rejected at the Module receiver port and that two differently bound invokers cannot substitute one caller's declaration for another's. Because neither `ReachabilityQuery` nor `ModuleInvoker.invoke` contains a caller identity parameter, Module code has no public API surface on which to forge that identity.

The acceptance runs against the same built distribution on Windows and Linux together with the existing independent Module, reasoning, owner-local/PUBLIC, durable-restart and smoke acceptance.

## Structural algebra in definitions

An Operation directly declares accepted Material types and receiving Privacy. It directly declares produced Material types and maximum Sensitivity. An Agent derives effective Privacy from its exposed Operations. A Module derives effective Sensitivity for exact state from reachable Material and declared outputs.

A Workflow adds no independent security decision. Every Operation call composes from the actual Material entering that Operation.

For one consequential call, `OperationCall.withEffect` binds one exact EffectProfile and the Integrity values of actual non-user causal participants. The causal requirement is `min(Risk, Autonomy)`. If there are no non-user causal participants the existing algebra uses I5. EffectProfile Risk is not forwarded into reasoning work.

Reasoning computation alone does not justify an EffectProfile. The shipped owner-interaction Module therefore declares `standard-prompt` as no-effect. `fast-lane` has `WRITE/AUTONOMOUS` because it creates durable work plus Module-owned persistent pending state that continues after foreground interaction. Its Module-specific `collect-background` has `DELETE/LIVE_INTERACTION` because explicit owner collection acknowledges completed durable work and removes completed pending semantic state.

The presentation binding selects these profiles only through the same generic declared-profile rule. It has no EffectProfile identity knowledge of its own.

## Reasoning port

When bounded Module behavior needs reasoning, it constructs a nominal `ReasoningComputation<R>` and creates a `ReasoningRequest` from an existing valid `OperationCall`.

The request derives originating Module and carried Sensitivity from the call. Module code supplies only the reasoning computation plus ordinary execution controls such as priority, timeout, eligibility, cancellation, retry and typed location/latency preferences.

The request does not expose Material identities/types, semantic continuation, a concrete reasoning-mechanism identity or Operation Risk.

This compile-time restriction is intentional: the SDK does not present a generic command envelope that ordinary application effects can reuse accidentally.

## Durable Module interpretation

Kernel durable reasoning stores opaque runtime bytes and stable reasoning-contract/runtime state. Semantic meaning of a pending result remains Module-owned.

The shipped owner-interaction fast lane demonstrates the composition: the Module persists WorkId-to-semantic-state association, Kernel persists opaque reasoning work, process restart rebuilds Module/reasoning installations, a compatible independent reasoning mechanism resumes work, and the installed Module's `collect-background` Operation interprets the completed result into Module Material, acknowledges Kernel work and removes its pending state.

The application-level `/updates` presentation does not change that ownership. It constructs the configured bounded request, invokes the Module owner-locally and renders returned Material. There is no generic Kernel callback, continuation object, background result router or scheduler language.

## CORE assignment

CORE is an optional installation role containing one ordinary `ModuleId`. If configured and installed, the live registry resolves it. If absent or unresolved, MADRE still boots.

CORE changes no Module definition, visibility, Module/owner-local/external-PUBLIC invocation authority, local-presentation authority, Module configuration semantics, algebraic value, reasoning privilege, scheduling privilege or class hierarchy.

## Codecs

Explicit versioned codecs map declarative definitions to JSON boundary representations. Domain classes do not inherit from codec/HTTP framework classes and expose no `Map<String,Object>` extension bag. Executable behavior is never serialized.

Definition codec version 2 nests Workflow declarations inside their owning Agent and preserves Operation order. `publicMaterialReferences` remains a set of foreign public type identities in that format; Module receiver Privacy is an SDK/runtime rule and therefore is not caller-provided codec data. There is no installed-base compatibility obligation for discarded development formats.

## Search interoperability

`madre-web-search` is a reusable typed value library. `madre-adapter-searxng` is an ordinary Java SearXNG client over that value model.

Neither belongs to the Kernel reasoning SPI. The SearXNG client has no Kernel dependency and does not implement `ReasoningCapability`. The previous standalone WebSearch Module is intentionally removed.

A future domain Module may use the search client directly and own the semantics of search, synthesis, persistence and continuation itself.
