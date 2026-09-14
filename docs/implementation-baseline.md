# Implementation Baseline

This document records what actually executes on the active Java branch. It is intentionally narrower than the target product responsibilities in `MADRE.md` and `docs/architecture/`.

The active implementation is one Java 21 Gradle multi-project system. Windows and Linux run the same application, Kernel, SDK, persistence model, Module installation/configuration mechanism and reasoning-mechanism installation mechanism. There is no separate Windows compatibility implementation and no Linux-specific public runtime.

## Current product transition

Several current behaviors are proven infrastructure but are not yet the completed owner/developer product:

- The runnable distribution is a **developer distribution**. Building/running it currently requires JDK 21, the Gradle wrapper, a copied/reviewed properties file and an explicit properties-file argument to the generated launcher. There is no owner-ready native installer or first-run product flow yet.
- `MadreMain` currently owns the local console read/evaluate loop and commands. Its optional application-local `interaction.*` binding independently selects the Module used for ordinary console text. `roles.core` does not currently drive that binding.
- The shipped owner-interaction Module owns semantic fast-lane behavior: immediate reasoning, durable background reasoning, Module-owned pending state, interpretation, acknowledgement and optional visible follow-up. It does not own the actual console surface/loop.
- `/updates` is currently the primary foreground mechanism for exposing completed delayed reasoning. The console invokes a bounded Module collection Operation; it does not interpret Kernel results. Natural delayed semantic re-entry into owner interaction is not implemented yet.
- Module and reasoning-provider configuration are currently immutable/read-only string settings delivered generically to the owning provider. There is no public provider-owned typed configuration metadata contract suitable for a generic owner configurator yet.
- `roles.core` currently resolves an optional installed Module identity and creates no privilege or structural qualification. Its target semantic meaning as the default owner-interaction/coordinator role is documented in `MADRE.md`; that qualification/interaction contract is not implemented yet.
- The SDK and reasoning SPI have strong independent-consumer proofs, but the repository has not yet completed the public developer product: stable external publication/discovery, developer-facing documentation/tooling/testkit and a complete unrelated-developer packaging/install journey are still product work.

These are transitional implementation facts. They must not be elevated into permanent architectural responsibilities merely because the current acceptance suite proves them.

## Current artifact boundaries

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, immutable Module-provider configuration, codecs, caller-bound Module interoperability, host-only owner-local/external-PUBLIC invocation ports and the Module-facing reasoning port;
- `madre-reasoning-spi`: public typed reasoning-adapter execution and installation SPI, with no dependency on Kernel implementation;
- `madre-kernel`: live executable Module registry and receiver mechanics, reasoning-capability registry/selection, resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: typed nominal text-inference computation/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: independently discoverable reasoning-adapter artifacts;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: application assembly, generic Module/reasoning artifact discovery, exact identity-scoped configuration/context delivery and the current replaceable local console.

`madre-app` has no concrete owner-interaction or reasoning-provider implementation dependency, no Module-specific configuration parser/table and no provider-specific reasoning configuration branch.

The former generic Kernel `Capability<C,R>` SPI, generic `ExecutionService`/`WorkRequest`, SearXNG Kernel capability and standalone shipped WebSearch Module remain removed.

## Security Algebra baseline

The exact values are:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity by minimum; information reaches a receiver iff `Sensitivity <= Privacy`.

For one exact EffectProfile, `min(Risk, Autonomy)` must be supported by the combined Integrity of actual non-user causal participants, or I5 when there are none. Risk is not propagated into reasoning work. `Privacy.MODULE` is the fixed receiver contract for structurally declared foreign Material crossing between installed Modules.

No generic policy evaluator, OWNER Privacy value, trusted-user Integrity shortcut or persistent security-result object exists.

## Executable Module and configuration baseline

A running Module is registered as a `ModuleInstance`: one canonical `ModuleDefinition` plus an exact `OperationBinding` for every declared Operation. Registration rejects incomplete, undeclared, foreign and non-canonical executable surfaces.

`ModuleProvider` is the public installation entrypoint:

```text
ModuleId moduleId()
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration)
```

Providers declare canonical identity before materialization. Application discovery uses `modules.directory` or the distribution sibling `modules/` directory and JDK class-loading/service-provider APIs. Shipped Modules are copied there but are not concrete `madre-app` compile dependencies.

Current Module settings use:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

`ModuleProviderConfiguration` contains the exact target `ModuleId` and read-only string settings. `madre-app` only scopes/delivers them; the provider owns supported-key validation, parsing, typed settings and defaults. An explicit setting for an uninstalled identity is rejected. Duplicate providers, identity mismatch and invalid bindings fail startup before a partial Module installation becomes reachable.

This string configuration is current executable truth. It is not a completed generic configuration-schema product and exposes no typed metadata for an owner configurator today.

## Receiver boundaries

### Module-to-Module

`ModuleContext` supplies caller-bound `ModuleDirectory` and `ModuleInvoker` facades. Module code supplies neither caller identity nor receiver Privacy.

Reachability exposes only target `PUBLIC` Operations compatible with the offered Material. Invocation resolves the exact installed binding and executes ordinary `OperationBinding.invoke`; `PublicResultTransformer` does not run.

Before result exposure the runtime requires the caller to canonically reference the foreign Material type and requires returned Sensitivity to reach fixed `Privacy.MODULE`. Successful delivery preserves the exact callee Material identity, owner, type and Sensitivity. S5 or undeclared foreign Material is rejected before caller code receives it.

The independent caller/callee verification build proves this path on Windows and Linux. This is proven infrastructure, not a prototype slated for removal.

### Owner-local host receiver

`OwnerModuleInvoker.invokeOwner` resolves an exact installed `PUBLIC` Operation, executes the canonical `OperationCall` and returns the validated Module-created Material unchanged. It does not apply public transformation or lower Sensitivity.

### External/PUBLIC host receiver

`PublicModuleInvoker.invokePublic` resolves the same bounded behavior but requires the binding's `PublicResultTransformer` before disclosure. The transformed output must be new declared Material with Sensitivity able to reach `Privacy.PUBLIC`.

Both host ports are absent from `ModuleContext`. CORE assignment, bundled origin, process placement and class-loader placement do not change receiver authority.

## Independent Module proofs

`verification/sdk-consumer` is a separate Gradle build compiled against published MADRE artifacts, not application/Kernel implementation classes. It proves independent Module provider materialization, configuration delivery, real installed discovery and external/PUBLIC behavior.

`verification/module-interoperability` is a second isolated build containing independent caller and callee JARs. It proves:

- owner-local raw S4 result delivery;
- caller-to-callee S4 Material delivery through fixed `Privacy.MODULE` while preserving callee identity/ownership;
- caller semantic adaptation to a distinct caller-owned Material identity;
- mandatory S1 semantic minimization on external/PUBLIC paths;
- rejection of S5 foreign results, undeclared foreign result types and PRIVATE target Operations;
- caller identity cannot be forged through the public directory/invoker API.

These fixtures prove the current SDK contract foundation. They are not by themselves evidence that external publication, documentation, testkit/tooling or developer packaging is community-ready.

## Current local interaction baseline

`madre-app` owns an immutable `LocalInteractionBinding`. It is application-local and absent unless `interaction.*` is configured.

The current configuration contract is:

```text
interaction.module=<installed ModuleId>
interaction.default-operation=<operation or operation@effect-profile>
interaction.standard-operation=<operation or operation@effect-profile>
interaction.prompt-material-type=<Module-owned MaterialType name>
interaction.default-sensitivity=<S1..S5>
interaction.updates-operation=<optional operation or operation@effect-profile>
interaction.updates-material-type=<required with updates-operation>
interaction.updates-payload=<required with updates-operation>
interaction.updates-sensitivity=<required with updates-operation, S1..S5>
```

Resolution occurs after Module discovery and validates the exact installed Module, PUBLIC Operations, accepted Module-owned text Material, output text types, EffectProfile selection, ordinary Sensitivities and optional bounded update payload.

`MadreMain` currently owns the interaction loop. Ordinary non-command text invokes the configured default Operation through owner-local invocation. `/standard` uses the configured standard Operation. `/updates` invokes the configured collection Operation and renders returned Material. `/sensitivity` changes only the current explicit prompt classification. Generic `/modules`, `/invoke-owner`, `/invoke-public`, legacy PUBLIC `/invoke`, `/exit` and `/quit` remain available.

`interaction.module` and `roles.core` are resolved independently in the current code. Acceptance covers CORE absent, interaction target assigned CORE, another Module assigned CORE and unresolved CORE with unchanged current interaction/configuration behavior.

This independence is intentionally recorded as current behavior, not target product architecture.

## Shipped owner-interaction Module baseline

The shipped owner-interaction Module is an ordinary installed Module and may be assigned CORE. Its provider receives settings through the same public `ModuleProviderConfiguration` path as an independent Module.

Current settings include foreground/background token limits, timeouts, retry controls and optional reasoning location/latency preferences. Omitting the scope preserves `OwnerInteractionSettings.defaults()`.

Its current bounded Operations demonstrate:

- `standard-prompt`: immediate reasoning and Module-created response Material, with no EffectProfile;
- `fast-lane`: foreground reasoning plus durable background reasoning and Module-owned pending-state persistence; its consequential profile is `WRITE/AUTONOMOUS`;
- `collect-background`: Module interpretation of completed durable reasoning, optional visible follow-up, acknowledgement and cleanup; its profile is `DELETE/LIVE_INTERACTION`.

Owner-local sensitive results preserve Module-created Sensitivity. External/PUBLIC invocation applies the Module's semantic minimizer.

The Module stores only its semantic pending association. Kernel SQLite independently stores opaque durable reasoning state. Across restart the two domains recover independently; the Module later interprets completed work and acknowledges/cleans it through `ReasoningService`.

The current `/updates` command merely invokes `collect-background`; no natural delayed conversation re-entry exists yet.

## Public reasoning-adapter SPI baseline

`madre-reasoning-spi` exposes the reasoning-specific public adapter boundary, including `ReasoningCapability`, exact capability identity/manifest, reasoning contracts/codecs, availability, execution context, resource claims, typed failure reporting, immutable provider configuration, `ReasoningMechanism` materialization and `ReasoningMechanismProvider`.

The SPI depends on the public SDK but not Kernel runtime implementation. It exposes no Module/Material semantics, generic tools/actions or semantic continuation.

Both the reasoning SPI and text-inference contract publish source/Javadoc artifacts in the repository's current publication verification.

Reasoning JARs are discovered independently from sibling `reasoning/` by default; the directory may be absent or empty. One provider may materialize zero, one or many mechanisms. Installation alone never enables a mechanism.

`madre-app` passes a read-only view of current `reasoning.*` string configuration to discovered providers and performs generic registration. Provider-specific parsing remains provider-owned. No public typed provider metadata/configurator contract exists yet.

## Reasoning runtime baseline

The Module-facing port is `ReasoningService`. `ReasoningRequest<R,C>` requires `C extends ReasoningComputation<R>`, preventing the reasoning path from becoming a generic command/action envelope.

A request derives originating Module and carried Sensitivity from a valid `OperationCall` and carries the reasoning computation plus execution controls: mode, priority, eligibility, timeout, cancellation, retry and typed location/latency preferences. It carries no Material identity/type, semantic continuation, concrete mechanism identity or Operation Risk.

Kernel selects compatible reasoning contracts using information reach, observed availability, resources, typed preferences, installation preference and deterministic identity ordering. Immediate and durable reasoning share selection/resource/failure semantics.

Durable work is stored in SQLite as opaque encoded computation/result bytes plus stable reasoning-contract identity and runtime state. Queued work survives restart and becomes runnable when a compatible mechanism is registered again.

An empty reasoning registry is valid at boot.

`verification/reasoning-consumer` is an isolated build depending only on the public reasoning SPI and text-inference contract. It provides deterministic installed reasoning used by acceptance without network/model/native/GPU/credential dependencies.

## Search baseline

Search is ordinary application/domain I/O, not Kernel reasoning. `madre-web-search` remains a reusable typed value library and `madre-adapter-searxng` an ordinary Java client with no Kernel dependency. The former Kernel SearXNG capability and shipped standalone WebSearch Module remain removed.

## CORE and boot baseline

Today, `roles.core` is optional. If configured and installed, the registry resolves that ordinary Module identity; if absent or configured-but-uninstalled, boot still succeeds. Current code performs no CORE structural qualification and uses CORE for no special interaction dispatch.

CORE changes no invocation authority, Module configuration delivery, Security Algebra value, visibility, reasoning installation, reasoning selection or scheduling privilege.

The target semantic meaning of CORE as MADRE's default owner-interaction/coordinator Module is a product responsibility documented in `MADRE.md` and `MADRE-platform-architecture.md`; it is not claimed as already implemented here.

The application also boots with absent/empty reasoning installation. A configured interaction binding can validate and boot with zero mechanisms; an Operation that later needs reasoning reports failure while the console remains usable.

## Developer packaging baseline

The current build uses JDK 21 and the checked-in Gradle wrapper. `installDist`/`distZip` create the Java distribution and launch scripts for the same `MadreMain` application on Windows and Linux. Running MADRE currently requires an explicit properties-file path.

This path is the truthful current developer packaging and remains the README run path. It does **not** satisfy the owner-deployable product gate: there is no native owner installer, first-run configuration experience or artifact/configuration management surface yet.

## Cross-platform acceptance

GitHub Actions runs the same source/distribution acceptance path on `ubuntu-latest` and `windows-latest`, including:

- `check`, architecture guards, Javadocs/publication/package verification;
- built distribution and installed-application smoke;
- isolated SDK Module, caller/callee interoperability and reasoning-adapter builds;
- no-reasoning boot/invocation;
- owner-local and external/PUBLIC receiver behavior;
- sensitive Module-to-Module composition and negative boundary cases;
- independent reasoning mechanism discovery/execution;
- shipped owner-interaction discovery/configuration;
- local interaction binding validation and console behavior;
- durable reasoning restart/recovery plus Module interpretation/acknowledgement through `/updates`.

No container runtime, VM layer, hosted provider account or external service is part of the mandatory CI path.

Earlier PR #47 acceptance also exercised real llama.cpp/model inference over the native AF_UNIX transport. That remains integration evidence for the shipped adapter but does not change the responsibilities above.

## Plan status

`docs/master-development-plan.md` is the completed foundation-plan record. It is not the active roadmap and its historical sequencing or obsolete intermediate types must not override the current product contract.

Current product meaning is in `MADRE.md`; focused boundaries are in `docs/architecture/`; this document is executable truth; `README.md` is the current runnable developer path.