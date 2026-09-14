# Implementation Baseline

The active implementation is one Java 21 Gradle multi-project system. Windows and Linux run the same application, Kernel, SDK, persistence model, Module installation/configuration mechanism and reasoning-mechanism installation mechanism. There is no separate Windows compatibility implementation and no Linux-specific public runtime.

The current artifact boundaries are:

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, immutable Module-provider installation configuration, codecs, caller-bound Module interoperability, host-only owner-local/external-PUBLIC invocation ports and the Module-facing reasoning port;
- `madre-reasoning-spi`: published typed reasoning-adapter execution and installation SPI, with no dependency on Kernel runtime implementation;
- `madre-kernel`: live executable Module registry and receiver-boundary mechanics, reasoning-capability registry/selection, reasoning resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: published typed nominal text-inference computation/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: independently discoverable reasoning-adapter artifacts;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: installable assembly, generic Module/reasoning-artifact discovery, exact identity-scoped Module-configuration/context delivery and replaceable local console. It has no concrete owner-interaction or reasoning-provider implementation dependency, no Module-specific configuration parser/table, and no provider-specific reasoning configuration branch. Its optional `interaction.*` namespace is application-local installation/presentation policy resolved generically against installed Module declarations.

The former generic Kernel `Capability<C,R>` SPI, generic `ExecutionService`/`WorkRequest`, SearXNG Kernel capability and standalone shipped WebSearch Module remain removed.

## Security Algebra baseline

The exact carrier values are:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity by minimum; information reaches a receiver iff `Sensitivity <= Privacy`.

For one exact EffectProfile, `min(Risk, Autonomy)` must be supported by the combined Integrity of actual non-user causal participants. `OperationCall` enforces that causal composition. If there are no such participants, the existing algebra uses I5; the host does not invent user-supplied Integrity values.

Risk is not propagated into reasoning work. `ReasoningCapabilityManifest` contains receiving Privacy but no action-realizer Integrity. Reasoning computation does not itself realize the external effect represented by a Module's Risk.

No generic policy evaluator exists. No OWNER Privacy value, trusted-user Integrity level or policy-result carrier exists. `Privacy.MODULE` is the fixed receiver contract for declared foreign Material crossing from one installed Module to another; Module code does not supply a Privacy value for that connection.

## Executable Module and installation-configuration baseline

A running Module is registered as `ModuleInstance`: one canonical `ModuleDefinition` and an exact `OperationBinding` for every declared Operation. Registration rejects incomplete, undeclared, foreign and non-canonical executable surfaces.

`ModuleProvider` is the standard public Java service-provider installation entrypoint. Its current contract is intentionally small:

```text
ModuleId moduleId()
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration)
```

The provider declares its canonical Module identity before materialization. Application discovery uses `modules.directory` (or the distribution sibling `modules/` directory) and JDK APIs. Shipped Modules are copied into that directory during packaging but are not concrete `madre-app` compile dependencies.

Owner Module configuration is associated with exact canonical identity using:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Square brackets are structural delimiters, not part of the Module identity. The current public `ModuleId` grammar allows letters, digits, dots, dashes and underscores but not square brackets, so values such as `fixture.module` and `fixture.module.child` are scoped independently without splitting on dots or relying on accidental parsing. Provider class name, JAR name, discovery order, shipped status and CORE assignment never participate in association.

`ModuleProviderConfiguration` is an immutable public SDK value containing the exact target `ModuleId` and read-only string settings. `madre-app` only extracts/scopes/delivers those values. The installed provider owns supported-key validation, parsing, typed settings and omitted-value defaults. Configuration is not a schema framework, DI system, secret store, account/session model, permission system or dynamic configuration service.

An omitted Module configuration produces an empty `ModuleProviderConfiguration` for that exact installed identity. Explicit malformed settings fail provider materialization rather than falling back. A `modules.config[...]` property targeting an identity with no installed provider is rejected rather than silently ignored.

Installation invariants are deterministic. Duplicate `ModuleProvider.moduleId()` values fail before provider code materializes anything. Providers are processed by canonical Module identity. For each provider, `madre-app` creates a `ModuleContext` whose directory and invoker are bound to that exact canonical identity; `ReachabilityQuery` and `ModuleInvoker.invoke` expose no caller-identity field. Each returned instance must have the same canonical identity and every instance validates its executable bindings before registration begins. All provider materialization/validation completes before any Module is registered, so a configuration/materialization failure cannot expose an earlier Module during a startup that will fail. If registration itself later fails, previously created registrations close in reverse order. The outer `MadreApplication` startup rollback still closes Module/reasoning registrations, loaders and Kernel resources on failure.

`OperationBinding.invoke` executes the exact canonical call and validates the Module-created internal output against its declared type, owner and maximum Sensitivity. Receiver-specific boundaries cannot bypass this validation.

### Module-to-Module receiver boundary

`ModuleContext` supplies a caller-bound `ModuleDirectory` and `ModuleInvoker`. It does not supply the live registry, `OwnerModuleInvoker` or `PublicModuleInvoker`.

`ModuleDirectory.reachable(new ReachabilityQuery(type, sensitivity))` uses the runtime-bound caller identity. A caller may offer its own Material type, or a foreign type already present in its canonical `publicMaterialReferences` and able to reach `Privacy.MODULE`. Reachability exposes only exact target Operations declared `PUBLIC` whose accepted-Material Privacy can receive that offered information. PRIVATE Operations are not returned.

`ModuleInvoker.invoke(call)` also uses the runtime-bound caller identity. The registry verifies the caller can structurally offer the input, resolves the exact canonical installed target binding, rejects PRIVATE/non-canonical calls and executes `OperationBinding.invoke` rather than `invokePublic`. Consequently a Module-to-Module call does not run `PublicResultTransformer`.

Before completing the caller-facing stage, the registry verifies that the caller's canonical `publicMaterialReferences` contains the returned foreign Material type and that the result Sensitivity can reach `Privacy.MODULE`. On success, the exact callee Material object crosses unchanged with the same identity, owner, type and Sensitivity. An undeclared foreign type or S5 result is rejected before Material reaches caller code. The caller may interpret that foreign Material and create a new caller-owned Material with a new nominal identity.

`publicMaterialReferences` therefore means references to another Module's publicly declared Material type identity; it does not classify every value of that type as S1/publicly disclosable. Receiver Privacy is structural SDK/runtime behavior, not a caller-provided field.

### Host receiver boundaries

`OwnerModuleInvoker.invokeOwner` is a distinct SDK host/application port implemented by the live registry. It resolves the same exact canonical installed externally callable Operation and accepts the same real `OperationCall`, but returns the validated Module-created Material without applying the public transformer or changing Sensitivity. It rejects PRIVATE Operations.

`PublicModuleInvoker.invokePublic` is the distinct host external/public receiver port. Every public binding owns a `PublicResultTransformer`; before internal Material crosses this boundary, the transformer must create new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`. Output type/owner/maximum-Sensitivity declarations remain enforced.

Both host ports are deliberately absent from `ModuleContext`. CORE assignment, same-process placement, shipped origin and class-loader placement do not change this accessibility boundary.

The generic application/console decodes input through the exact installed canonical `MaterialType` codec and builds the exact canonical `OperationCall`. No-effect Operations use `withoutEffect`. A single declared EffectProfile is selected exactly; multiple profiles require generic `<operation>@<effect-profile>` selection. Only actual non-user causal participants are supplied; current local console calls have none.

### Independent Module proofs

`verification/sdk-consumer` remains an executable independent Module built outside the root dependency graph against published MADRE artifacts. Its provider implements the public Module configuration contract without any `madre-app` or Kernel implementation dependency.

The fixture owns one optional setting, `result-prefix`. With configuration omitted, `phd.module/inspect` preserves its existing PUBLIC result `public:hello`. With:

```text
modules.config[phd.module].result-prefix=configured-
```

the same installed provider returns `public:configured-hello` through the real discovery/materialization/registration/external-PUBLIC path. The provider, not `madre-app`, validates that key and value. `phd.module/reason` still submits a real text-inference `ReasoningRequest` through the supplied `ReasoningService`; both Operations retain the same Module-owned PUBLIC semantic transformation.

`verification/module-interoperability` is a second isolated Gradle build with separate `callee` and `caller` JARs. Each depends only on `io.github.didacll:madre-sdk` from the isolated published repository. CI installs both into the built distribution on Windows and Linux.

The caller passes caller-owned `interop.caller/request` Material into the callee's exact PUBLIC `sensitive` Operation. The callee returns `interop.callee/sensitive-result` at S4. Because the caller canonically references that foreign type, the Module receiver path delivers the exact callee-owned S4 Material without public transformation. The caller records the received owner/id/Sensitivity, interprets the value and creates a distinct S4 caller-owned `adapted-result` identity.

The same callee Operation through owner-local returns raw S4 `classified:hello`; through the external/PUBLIC host path it returns only new S1 `public:callee-summary`. The caller's own external/PUBLIC path similarly returns only `public:caller-summary`.

Negative installed acceptance proves an S5 callee result is blocked by fixed `Privacy.MODULE`, a lower-Sensitivity undeclared foreign type is blocked before caller exposure, and a PRIVATE callee Operation is not discoverable. Kernel tests additionally reject PRIVATE/non-canonical Module receiver calls and prove differently bound caller facades enforce their respective declarations. Because the public Module API contains no caller identity argument, caller identity cannot be forged through `ReachabilityQuery` or `ModuleInvoker.invoke`.

## Local interaction presentation baseline

`madre-app` owns one small immutable `LocalInteractionBinding`. It is not an SDK type and does not enter Kernel or Module context. The binding is absent unless at least one `interaction.*` property is configured; no-interaction installation is therefore a normal supported state.

The current application-local configuration contract is:

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

This presentation policy is separate from `modules.config[...]`. `interaction.module` neither determines nor overrides a Module's installation configuration. The shipped example points presentation identities at owner-interaction, but no concrete owner-interaction identity or setting name occurs in `madre-app` production Java.

Binding resolution runs after ordinary Module discovery. It requires the configured Module to be installed; resolves each configured operation through the same canonical operation/profile-selection rules used by generic invocation; requires `PUBLIC` visibility; requires the configured Module-owned input Material type to be accepted; requires String/text content for the input and every declared possible output; verifies configured ordinary Sensitivity can reach the exact accepted Privacy; and validates that the bounded updates payload can be decoded by its configured text Material type. Unknown `interaction.*` keys, partial updates configuration, malformed identities, `SYSTEM_RESERVED` Sensitivity and structurally incompatible declarations fail startup instead of choosing a fallback.

When present, ordinary non-command console text invokes the configured default Operation through `invokeOwnerText`, so it is owner-local rather than external/PUBLIC. `/standard <text>` uses the configured standard Operation through the same owner-local path. `/updates` uses the configured updates Operation and fixed bounded request; the console neither polls it automatically nor interprets reasoning output. `/sensitivity S1..S5` changes only the current prompt classification explicitly. Owner-local results print their actual Material Sensitivity.

The generic `/modules`, `/invoke-owner`, `/invoke-public`, legacy PUBLIC `/invoke`, `/exit` and `/quit` remain additive and independent of the convenient presentation. Interactive operation failures are reported and return control to the console where practical; non-interactive invocation retains fail-fast behavior.

`interaction.module` and `roles.core` are resolved independently. No CORE lookup participates in binding validation or invocation. Acceptance covers CORE absent, interaction Module assigned CORE, another installed CORE and unresolved CORE with unchanged interaction and Module-configuration behavior.

## Shipped owner-interaction Module baseline

The shipped owner-interaction Module remains an ordinary installed Module and may optionally be assigned CORE. Its provider receives behavioral settings through exactly the same public `ModuleProviderConfiguration` boundary as an independent Module.

The owner-interaction Module owns and validates these optional keys:

```text
foreground-maximum-tokens
background-maximum-tokens
foreground-timeout-ms
background-timeout-ms
background-retry-attempts
background-retry-delay-ms
foreground-location
foreground-maximum-latency-ms
background-location
background-maximum-latency-ms
```

Omitting every scoped setting preserves `OwnerInteractionSettings.defaults()` exactly: foreground tokens 256, background tokens 512, foreground timeout 90 seconds, background timeout 5 minutes, durable background retry 3 attempts with 5-second delay, and unconstrained foreground/background reasoning preferences. Unknown keys, malformed numbers, non-positive required values or invalid location names fail startup during Module materialization.

The installed acceptance configures foreground tokens to 37 and background tokens to 41. The independently installed deterministic reasoning mechanism reports the actual `TextInferenceCommand.maximumGeneratedTokens()` it executes. Owner-local `standard-prompt`, ordinary console text and `/standard` therefore prove `37` reached real immediate reasoning. Durable restart `/updates` proves `41` reached real independently installed durable reasoning execution. A separate omitted-configuration invocation proves the existing foreground default `256` remains active.

The Module's EffectProfile audit is otherwise unchanged:

- `standard-prompt` has no EffectProfile. Immediate reasoning and Material creation are not themselves consequential external/domain effects.
- `fast-lane` declares `durable-background-write` with `Risk.WRITE` and `Autonomy.AUTONOMOUS`. The consequential behavior is durable reasoning submission plus Module-owned pending-state persistence that continues beyond foreground interaction; reasoning itself is not the Risk.
- `collect-background` declares `acknowledge-completed-background` with `Risk.DELETE` and `Autonomy.LIVE_INTERACTION`. The owner explicitly requests collection; the Module interprets completed reasoning, acknowledges Kernel durable work and removes completed pending semantic state.

`collect-background` is a Module-specific bounded Operation, not a Kernel callback, generic continuation, background-result router or scheduler language.

Owner-local `standard-prompt` with sensitive prompt Material returns the useful Module-created answer at the same S2-S5 Sensitivity. The same exact installed Operation through external/PUBLIC still applies the Module's semantic minimizer and cannot expose the private answer unchanged.

Fast lane stores only its semantic pending association in Module-owned state. Kernel SQLite independently stores opaque reasoning runtime state. Across restart the Module reloads its semantic pending state, Kernel recovers the durable work, a compatible independently installed reasoning mechanism resumes it, and `collect-background` performs Module interpretation, acknowledgement and cleanup. The convenient `/updates` command only invokes that installed Operation and renders its Material.

## Public reasoning-adapter SPI baseline

`madre-reasoning-spi` remains the public adapter boundary. It contains the reasoning-specific execution contract plus the minimal installation boundary:

- `ReasoningCapability`;
- `ReasoningCapabilityId` and `ReasoningCapabilityManifest`;
- `ReasoningContract` and `ReasoningCodec`;
- `ReasoningAvailability`;
- `ReasoningExecutionContext`;
- `ResourceClaim` and `ResourceId`;
- typed `ReasoningException` failure reporting;
- immutable `ReasoningProviderConfiguration`;
- `ReasoningMechanism` materialization with ordinary installation preference;
- `ReasoningMechanismProvider` service-provider entrypoint.

The SPI depends on the public SDK. It does not expose `ReasoningCapabilityRegistry`, SQLite stores, schedulers, application assembly, Module/Material semantics, generic tools/actions or semantic continuation.

Both `madre-reasoning-spi` and `madre-text-inference` are published with source/Javadoc artifacts so an adapter can compile independently against the public boundary and the computation contract it implements.

Module configuration is separate from reasoning-adapter configuration. `modules.config[...]` is delivered to one canonical Module provider; `reasoning.*` remains the separate generic reasoning-provider configuration namespace.

## Reasoning installation baseline

Reasoning JARs are discovered independently from Modules. Installed distributions use sibling `reasoning/` by default; `reasoning.directory` overrides it. Discovery uses `Path`, `Files`, `URLClassLoader` and `ServiceLoader`.

Missing and empty reasoning directories are valid. A provider may materialize zero, one or many mechanisms. Disabled/unconfigured instances register nothing. Adapter installation by itself never enables a mechanism.

`madre-app` passes an immutable read-only view of `reasoning.*` owner configuration to discovered providers and performs only generic registration. Provider-specific parsing, including endpoint/model fields and explicit Privacy, is owned by each adapter. Privacy is not inferred from locality, endpoint or transport.

The shipped llama.cpp AF_UNIX, explicit llama.cpp loopback-HTTP compatibility and OpenAI-compatible artifacts are copied into `reasoning/` and discovered with the same `ReasoningMechanismProvider` path as external adapters. Their explicit-enable behavior remains unchanged in meaning: no configured enabled instance means no registered mechanism.

Provider/classloader resources are closed at shutdown. Startup failures roll back already-created mechanism registrations and close loaders/providers. Null provider materialization and null mechanism values are rejected.

## Reasoning runtime baseline

The Module-facing port remains `ReasoningService`. `ReasoningRequest<R,C>` requires `C extends ReasoningComputation<R>`, preventing the reasoning runtime from becoming a generic command/action envelope by structural accident.

A request derives originating Module and carried Sensitivity from a valid bounded `OperationCall`. It carries the reasoning computation plus execution controls: mode, priority, eligibility, timeout, cancellation, retry and typed location/latency preferences.

It carries no Material identity/type, semantic continuation, concrete reasoning-mechanism identity or Operation Risk.

Kernel's `ReasoningCapabilityRegistry` selects only compatible reasoning contracts. Selection composes carried Sensitivity with manifest receiving Privacy, then applies observed availability, resource capacity, typed location/latency preferences, configured installation preference and deterministic identity ordering.

Duplicate capability identity is rejected. The registry also rejects computation-contract declarations where nominal contract identity and computation/result Java types conflict.

Immediate and durable reasoning share selection/resource/failure semantics. An unavailable mechanism is not selected; an immediate request with no reachable available mechanism fails with the typed unavailable category.

Durable work is stored in `SQLiteReasoningWorkStore`; queued input, attempt state and pending output survive restart. Persistence uses stable reasoning-contract identity and opaque encoded computation/result bytes, not adapter implementation class names. After restart, queued work becomes runnable when a compatible contract/mechanism is registered again.

An empty reasoning registry is valid at boot.

## Independent reasoning-adapter proof

`verification/reasoning-consumer` remains a separate Gradle build whose only MADRE dependencies are:

```text
io.github.didacll:madre-reasoning-spi:0.1.0-SNAPSHOT
io.github.didacll:madre-text-inference:0.1.0-SNAPSHOT
```

It produces `independent-reasoning.jar`, exposes `ReasoningMechanismProvider`, and materializes deterministic text inference from ordinary owner configuration. Its deterministic result includes the maximum generated-token value actually received, e.g.:

```text
independent:<prompt>|maximum-generated-tokens=<N>
```

This is acceptance instrumentation inside the independent fixture, not a production reasoning API change. It lets the installed tests prove Module configuration reached real reasoning execution. The fixture still requires no provider service, network, model, native binary, GPU or credentials.

For restart acceptance only, the same independent fixture supports optional file-based background gating and a completion marker. These are fixture controls, not production reasoning architecture. They let CI prove that fast-lane background work cannot finish before shutdown, then allow the same persisted work to complete after restart without an arbitrary sleep.

The independent Module reasoning acceptance still requires the PUBLIC result to contain `public:reasoned:independent:hello` and rejects leakage of the internal `private:reasoned:` Material.

## Search baseline

Search is ordinary application/domain I/O, not Kernel reasoning.

`madre-web-search` remains a reusable typed search-value module. `madre-adapter-searxng` provides `SearxngClient` over ordinary JDK/Jackson HTTP/JSON code and depends on `madre-web-search`, not on `madre-kernel`.

The former `SearxngCapability`, standalone `madre-module-web-search`, service-provider registration and deep-search product path remain removed from the active application architecture.

Build-time architecture checks reject SearXNG-to-Kernel coupling, SearXNG implementing `ReasoningCapability`, restoration of the standalone WebSearch Module and restoration of the generic `ExecutionService`/`WorkRequest` production API. They also reject concrete llama.cpp/OpenAI-compatible adapter knowledge or normal concrete-adapter implementation dependencies in `madre-app`, Kernel-runtime dependencies from the public reasoning SPI/shipped reasoning adapters, concrete owner-interaction dependencies/identities in `madre-app` production Java, and known Module-specific configuration setting names in application production Java.

## CORE and boot baseline

CORE is optional. `roles.core` may be absent; a configured-but-absent CORE remains unresolved and does not prevent boot. CORE changes no Module/owner-local/external-PUBLIC invocation authority, Module configuration delivery, Security Algebra value, visibility, local interaction authority, reasoning installation, reasoning selection or scheduling privilege.

The runtime supports an absent reasoning directory, an empty reasoning directory and installed providers that produce zero mechanisms. The independent Module no-reasoning verification demonstrates real installed external/PUBLIC invocation in that state. A configured interaction binding is also validated and bootable with zero reasoning mechanisms; invoking a reasoning-backed Operation reports a reasoning/operation failure while the console remains usable.

## Cross-platform acceptance matrix

The GitHub Actions matrix runs the same source/distribution acceptance path on `ubuntu-latest` and `windows-latest`:

- ordinary `check`, including SDK/runtime receiver invariants, public configuration and Module installer lifecycle tests;
- Javadocs/publication/package verification;
- built distribution;
- isolated independent Module build;
- isolated caller/callee Module interoperability build using only the published SDK;
- isolated independent reasoning-adapter build;
- no-reasoning Module installation/external-PUBLIC invocation;
- independently installed SDK-only Module with omitted configuration preserving `public:hello`;
- the same installed independent Module with non-default `result-prefix` producing `public:configured-hello`;
- callee owner-local invocation returning raw S4 Material;
- installed caller-to-callee invocation preserving callee identity/ownership/S4 at the Module receiver boundary;
- caller semantic adaptation producing a different caller-owned Material identity;
- S5 foreign Material rejection at fixed `Privacy.MODULE` before caller exposure;
- undeclared foreign Material type rejection before caller exposure;
- PRIVATE callee Operation invisibility to the Module directory;
- the same callee behavior through external/PUBLIC invocation with mandatory S1 semantic minimization;
- caller external/PUBLIC output with its own mandatory semantic minimization;
- independent reasoning-adapter installation/discovery and actual reasoning execution;
- Module interpretation plus PUBLIC semantic transformation;
- shipped owner-interaction discovery from `modules/`;
- omitted owner-interaction Module configuration preserving actual default foreground reasoning budget 256;
- non-default owner-interaction foreground reasoning budget 37 reaching the independently installed reasoning mechanism through generic owner-local, ordinary-console and `/standard` execution;
- non-default owner-interaction background reasoning budget 41 reaching durable reasoning and returning through `/updates` after restart;
- equivalent PUBLIC standard-prompt with mandatory public minimization and no sensitive answer leakage;
- malformed explicit Module configuration failing startup clearly instead of falling back;
- configured interaction binding validation against installed canonical declarations;
- actual installed console ordinary text through the configured fast/default owner-local Operation;
- `/standard`, explicit `/sensitivity`, and owner-local result Sensitivity rendering;
- Module configuration behavior unchanged with CORE absent, owner-interaction as CORE, a different installed CORE and unresolved CORE;
- Module configuration behavior unchanged with `interaction.*` absent and the generic console active;
- configured interaction boot with zero reasoning mechanisms plus truthful invocation failure and continued console usability;
- presentation-level deterministic fast-lane foreground response, process shutdown, SQLite durable recovery, reasoning-adapter rediscovery, `/updates` Module interpretation/acknowledgement/cleanup and a second empty `/updates`;
- installed-application smoke.

No container runtime, VM layer, orchestration system, hosted provider account or external service is part of the mandatory build/test path.

## Live integration evidence

Earlier PR #47 acceptance runs exercised real llama.cpp/model inference over the native AF_UNIX transport. That remains relevant evidence for the shipped llama.cpp reasoning implementation.

Earlier live SearXNG evidence demonstrates the ordinary client/provider integration historically, but it is not evidence that search belongs in Kernel or that the removed WebSearch Module remains shipped.

The Module-interoperability, Module-configuration, local-interaction, owner-local invocation and restart acceptance use SDK-only/deterministic independent fixtures; they do not claim new live external-provider/model evidence.

## Plan status

`docs/master-development-plan.md` is the completed foundation-plan record and contains historical descriptions of earlier implementation stages. Those descriptions are superseded where they conflict with the active architecture above.

Current product meaning is in `MADRE.md`; active focused boundaries are in `docs/architecture/`; this document is the concise executable truth.
