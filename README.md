# MADRE

MADRE is personal, owner-sovereign software for modular applications that can use bounded reasoning while keeping information reach explicit. The active implementation is Java 21.

Windows and Linux run the same application, Kernel, SDK, Module installation/configuration and reasoning-mechanism installation architecture. There is no Windows compatibility product layered over a Unix implementation and no Linux-specific public runtime.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md) for the public/executable Module model;
- [Reasoning Execution Contract](docs/architecture/MADRE-execution-contract.md) for the reasoning SPI and Kernel path;
- [Implementation Baseline](docs/implementation-baseline.md) for executable truth.

## Build and package

Use JDK 21 and the checked-in Gradle wrapper.

Windows:

```text
.\gradlew.bat --no-daemon --build-cache check javadoc publish installDist distZip
.\gradlew.bat --no-daemon --build-cache -p verification/sdk-consumer jar
.\gradlew.bat --no-daemon --build-cache -p verification/reasoning-consumer jar
```

Linux:

```text
./gradlew --no-daemon --build-cache check javadoc publish installDist distZip
./gradlew --no-daemon --build-cache -p verification/sdk-consumer jar
./gradlew --no-daemon --build-cache -p verification/reasoning-consumer jar
```

Both hosts build the same Java sources and distribution. Gradle creates native launch shims for the same `MadreMain` application:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

CI verifies executable changes on both Windows and Linux. Production changes additionally run Javadocs, publication, packaging and installed-application smoke. The isolated Module and reasoning-adapter fixtures are built outside the normal application dependency graph and then installed into the built distribution for real discovery/execution checks.

No container runtime, VM layer, hosted provider or external account is required for the mandatory build/test path.

## Module installation

A MADRE Module is a complete executable application/domain boundary. Its JAR provides:

```text
io.github.didacll.madre.sdk.registration.ModuleProvider
```

through Java's standard service-provider resource:

```text
META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

The public provider declares one canonical identity and materializes exactly that Module:

```java
ModuleId moduleId();
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration);
```

`ModuleInstance` remains one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Runtime discovery uses `modules.directory`; installed distributions default to their sibling `modules/` directory.

The shipped owner-interaction Module is packaged there but `madre-app` does not compile against its concrete class. Independently built Modules use the same discovery/registration path. Bundling, class-loader placement and CORE designation confer no privilege.

`verification/sdk-consumer` demonstrates the independent Module boundary. It depends on published MADRE artifacts, not `madre-app` or Kernel implementation classes, and produces `independent-module.jar`.

## Owner Module configuration

Owner-supplied installation configuration is scoped by the provider's exact canonical `ModuleId`:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

For example, the independent verification Module owns `result-prefix`:

```properties
modules.config[phd.module].result-prefix=configured-
```

With that property omitted, its installed PUBLIC `inspect` path returns the existing result:

```text
public:hello
```

With the property present, the same independently compiled provider returns:

```text
public:configured-hello
```

`madre-app` does not know what `result-prefix` means. It only associates properties with the exact declared `ModuleId` and supplies immutable `ModuleProviderConfiguration`; the provider owns supported-key validation, parsing, typed settings and defaults.

Square brackets are structural delimiters. Valid Module identities may contain dots, dashes and underscores but cannot contain brackets, so `phd.module`, `phd.module.child` and other valid identities are matched exactly without guessing dot-separated namespace segments. Provider class names, JAR names, discovery order, shipped status and CORE assignment are not configuration identities.

An explicit setting for an uninstalled Module identity is rejected rather than ignored. Duplicate provider identities, provider/materialized-Module identity mismatches, malformed provider configuration and invalid executable bindings fail startup before any Module registration becomes reachable. If registration itself fails, already-created registrations are rolled back.

Module configuration is separate from `interaction.*` presentation policy and from `reasoning.*` adapter configuration.

## Owner-local and PUBLIC invocation

MADRE has two deliberately distinct host receiver boundaries over exact installed externally callable Operations.

`OwnerModuleInvoker.invokeOwner` is the owner-local host/application boundary. It resolves the exact canonical installed `PUBLIC` Operation, accepts a real `OperationCall`, executes the Module-owned binding, validates its declared output contract and returns the Module-created Material unchanged. It does not apply `PublicResultTransformer` and does not lower Sensitivity merely because the owner sees the result locally.

`OwnerModuleInvoker` is not part of `ModuleContext`. Installed Modules still receive only `ModuleInvoker`, whose reachable cross-Module path is `invokePublic`. The application supplies facade objects rather than its concrete live registry, so a Module cannot recover owner-local authority by downcasting a context port. CORE, shipped placement, class-loader placement and same-process execution do not alter that rule.

`ModuleInvoker.invokePublic` remains the external/public boundary. A public binding must apply its Module-owned semantic result transformer before Material leaves this boundary. The result must be new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`; raw internal Material cannot cross this path.

The replaceable local console exposes both generic boundaries:

```text
/modules
/invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
/invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

or non-interactively:

```text
madre <properties> --list-modules
madre <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

Both routes decode input through the exact installed canonical `MaterialType` codec and construct the canonical `OperationCall`. For a consequential Operation with one EffectProfile the application selects that exact declared profile and supplies no invented causal participant. If an Operation has multiple profiles, the generic selector is `<operation>@<effect-profile>`.

Owner-local output prints its retained Sensitivity together with payload; PUBLIC output remains the minimized public payload. The legacy interactive `/invoke` alias remains PUBLIC rather than silently changing receiver semantics.

## Local text interaction presentation

`madre-app` can optionally bind its replaceable local text console to one ordinary installed Module through the application-local `interaction.*` configuration namespace. This is installation/presentation policy, not an SDK role, Module subtype, Kernel abstraction or CORE privilege. The configured Module can be replaced by another structurally compatible installed Module without recompiling `madre-app`.

The shipped example configuration is:

```text
interaction.module=io.github.didacll.madre.owner-interaction
interaction.default-operation=fast-lane
interaction.standard-operation=standard-prompt
interaction.prompt-material-type=owner-prompt
interaction.default-sensitivity=S5
interaction.updates-operation=collect-background
interaction.updates-material-type=background-collection-request
interaction.updates-payload=collect
interaction.updates-sensitivity=S1
```

If any `interaction.*` configuration is present, startup resolves it only after Module discovery and validates it against the exact installed canonical declarations. The configured Module and Operations must exist; Operations must be `PUBLIC`; configured input Material must be declared and accepted; console input and every possible declared output must be text; EffectProfile selection must be unambiguous under the same generic rule used by low-level owner invocation; and configured Sensitivities must be ordinary values able to reach the receiving Operation boundary. An optional updates binding is all-or-nothing. Invalid bindings fail startup clearly instead of silently selecting another Module or semantic mode.

When the binding is valid, ordinary non-command text invokes `interaction.default-operation` through the existing owner-local invocation path. `/standard <text>` invokes the configured standard Operation owner-locally. `/updates` invokes only the explicitly configured Module collection Operation; the console does not poll destructively, interpret Kernel results or add callbacks. `/sensitivity S1..S5` explicitly changes the current prompt Sensitivity for the console session; `SYSTEM_RESERVED` is rejected. The configured default is not inferred from prompt text, endpoint, mechanism or CORE status.

Owner-local results are rendered with their actual Sensitivity, for example `S5<TAB>...`. Ordinary text never means PUBLIC invocation. `/invoke-public` and the legacy `/invoke` alias remain explicit external/public disclosure paths. `/invoke-owner`, `/modules`, `/exit` and `/quit` remain available. With no `interaction.*` configuration the same application boots into the generic low-level console.

`roles.core`, `interaction.module` and `modules.config[...]` are independent facts. The shipped example happens to name the same ordinary Module for CORE and interaction, but neither CORE assignment nor presentation binding changes which Module configuration is delivered.

## Reasoning-adapter installation

Reasoning mechanisms are independently installable from Modules and use a different directory and provider contract.

A reasoning adapter is an ordinary JVM JAR that provides:

```text
io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider
```

through:

```text
META-INF/services/io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider
```

The public adapter artifact is:

```text
io.github.didacll:madre-reasoning-spi:0.1.0-SNAPSHOT
```

An adapter also depends on whichever published computation-contract artifact it realizes. For the current text-inference family that is:

```text
io.github.didacll:madre-text-inference:0.1.0-SNAPSHOT
```

An adapter does not depend on `madre-app`, `ReasoningCapabilityRegistry`, SQLite stores, schedulers or other Kernel runtime implementation classes.

Installed distributions discover reasoning-adapter JARs from sibling `reasoning/` by default. Override with:

```text
reasoning.directory=/absolute/path/to/reasoning-jars
```

The reasoning directory may be absent or empty. Artifact presence does not enable a mechanism. Providers receive a read-only view of owner-supplied `reasoning.*` configuration and own their provider-specific parsing/validation. One adapter can materialize multiple named mechanism instances.

The shipped llama.cpp AF_UNIX, explicit loopback-HTTP compatibility and OpenAI-compatible adapter artifacts are copied into `reasoning/` and discovered through exactly the same path as independently supplied adapters. `madre-app` has no concrete provider dependency or provider-type switch/factory table.

Privacy is explicit provider configuration and is never inferred from endpoint, transport or location. Invalid enabled configuration fails startup instead of silently changing mechanism semantics. Prepared authentication/session state remains outside MADRE.

`verification/reasoning-consumer` proves independent adapter installation. It depends only on published `madre-reasoning-spi` and `madre-text-inference`, produces `independent-reasoning.jar`, is copied into the built distribution's `reasoning/` directory, and provides deterministic text inference requiring no network/model/GPU/credentials.

## Shipped owner-interaction Module configuration

The shipped owner-interaction Module is ordinary installed Module behavior and may optionally be assigned CORE. Its provider consumes the same `ModuleProviderConfiguration` mechanism as any independent Module.

Its Module-owned optional settings are:

```properties
modules.config[io.github.didacll.madre.owner-interaction].foreground-maximum-tokens=256
modules.config[io.github.didacll.madre.owner-interaction].background-maximum-tokens=512
modules.config[io.github.didacll.madre.owner-interaction].foreground-timeout-ms=90000
modules.config[io.github.didacll.madre.owner-interaction].background-timeout-ms=300000
modules.config[io.github.didacll.madre.owner-interaction].background-retry-attempts=3
modules.config[io.github.didacll.madre.owner-interaction].background-retry-delay-ms=5000
# optional selection constraints; omit for unconstrained reasoning
# modules.config[io.github.didacll.madre.owner-interaction].foreground-location=LOCAL
# modules.config[io.github.didacll.madre.owner-interaction].foreground-maximum-latency-ms=30000
# modules.config[io.github.didacll.madre.owner-interaction].background-location=LOCAL
# modules.config[io.github.didacll.madre.owner-interaction].background-maximum-latency-ms=30000
```

All lines are optional. Omitting the owner-interaction scope preserves the existing `OwnerInteractionSettings.defaults()` exactly. The example distribution leaves these lines commented. The Module itself validates unknown/malformed values; `madre-app` contains no concrete knowledge of these fields.

Its EffectProfiles still describe real consequences, not the fact that reasoning occurred. `standard-prompt` has no EffectProfile. `fast-lane` retains `WRITE/AUTONOMOUS` `durable-background-write`. `collect-background` retains `DELETE/LIVE_INTERACTION` `acknowledge-completed-background`.

The installed acceptance configures foreground maximum tokens to `37` and background maximum tokens to `41`. The independently installed deterministic reasoning mechanism reports the `TextInferenceCommand.maximumGeneratedTokens()` it actually executed. This proves the configured values reach the real foreground and durable reasoning paths, while a separate omitted-configuration run proves the foreground default remains `256`.

## CORE and reasoning independence

`roles.core` is optional. CORE is an ordinary installed Module identity lookup; it creates no subtype, invocation authority, Module configuration authority, Security Algebra value, scheduling lane or required Operation names. MADRE boots with no CORE configured and tolerates a configured CORE that is not installed.

Reasoning mechanisms are also optional at boot. An Operation that later needs reasoning may fail when invoked if no compatible mechanism is available, but mechanism absence is not a startup error. A Module Operation that uses no reasoning remains fully usable with an absent or empty reasoning directory.

CORE designation does not affect owner-local/public invocation semantics, local interaction presentation, Module configuration, reasoning installation, selection or preference.

## Reasoning execution

Kernel execution is reasoning-only. The Module-facing `ReasoningService` accepts only nominal `ReasoningComputation<R>` values through `ReasoningRequest`; it is not a generic action/tool/physical-work dispatcher.

Selection uses exact computation-contract compatibility, carried Sensitivity versus explicit receiving Privacy, observed availability, resource capacity, typed location/latency preferences, configured ordinary installation preference and deterministic identity ordering. Operation Risk is not part of reasoning selection and reasoning manifests carry no action-realizer Integrity.

Immediate and durable reasoning share the same mechanism registry/selection/resource/failure semantics. Durable SQLite work stores stable reasoning-contract identity plus opaque computation/result bytes, not adapter implementation class names. Work can therefore resume after restart when the compatible contract/mechanism is registered again.

The independent Module fixture contains two PUBLIC Operations used by CI:

```text
phd.module/inspect   # no reasoning; proves zero-ReasoningCapability execution/configuration
phd.module/reason    # real ReasoningRequest through independently installed adapter
```

The reasoning-backed path still returns Module-created PUBLIC Material and CI rejects leakage of the Module's internal `private:reasoned:` Material.

## Search

Search is ordinary application/domain I/O, not a Kernel reasoning capability.

`madre-web-search` provides reusable typed search values and `madre-adapter-searxng` provides an ordinary Java `SearxngClient`. The SearXNG adapter has no Kernel dependency and does not implement `ReasoningCapability`.

The former standalone shipped WebSearch Module and generic SearXNG Kernel capability remain removed. A future domain Module that genuinely owns research/search behavior may depend on the search library/client directly.

## Start MADRE

Build the distribution, copy/review the example configuration and run the single Java application. You may remove `roles.core`, independently remove every `interaction.*` line to use only the generic console, leave all Module configuration omitted to use provider defaults, leave all shipped reasoning instances disabled and even point `reasoning.directory` at an empty directory for a bare runtime.

Windows PowerShell:

```text
.\gradlew.bat --no-daemon installDist
Copy-Item config\madre.properties.example C:\path\to\madre.properties
.\madre-app\build\install\madre\bin\madre.bat C:\path\to\madre.properties
```

Linux:

```text
./gradlew --no-daemon installDist
cp config/madre.properties.example /absolute/path/madre.properties
madre-app/build/install/madre/bin/madre /absolute/path/madre.properties
```

Additional owner-installed Module JARs belong in the configured Module directory. Configure each through its documented `modules.config[<canonical ModuleId>].*` keys. Additional reasoning adapter JARs belong in the configured reasoning directory and use their provider-owned `reasoning.*` settings. Neither requires adding a concrete application compile-time dependency.

Reasoning failures remain reasoning-runtime failures. With an interaction binding configured, a prompt Operation that requires reasoning reports an operation failure if no compatible mechanism exists; the application itself still boots and its generic commands remain usable. Kernel SQLite stores only durable reasoning work, attempts and retained opaque payloads; semantic interpretation belongs to the originating Module.

## Verification evidence

The mandatory CI matrix exercises `check`, Javadocs/publication/package verification, both isolated fixture builds, built-distribution installation/discovery, no-reasoning Module invocation, independent reasoning execution, PUBLIC semantic transformation and installed-application smoke on Linux and Windows.

For Module configuration it additionally proves, through the built distribution and real service-provider path:

- independent SDK consumer omitted configuration -> `public:hello`;
- independent SDK consumer non-default `result-prefix` -> `public:configured-hello`;
- owner-interaction omitted configuration -> actual reasoning execution reports default foreground maximum tokens `256`;
- owner-interaction non-default foreground setting -> actual installed reasoning execution reports `37` through low-level owner invocation, ordinary console text and `/standard`;
- owner-interaction non-default background setting -> durable execution reports `41` after restart and `/updates`;
- malformed explicit Module configuration fails startup;
- CORE absent/same/different/unresolved leaves Module configuration unchanged;
- removing `interaction.*` leaves Module configuration unchanged.

The existing matrix also preserves separate owner-local/PUBLIC boundary checks, convenient interaction, explicit Sensitivity, no-interaction generic console, configured interaction with zero reasoning mechanisms, deterministic durable restart, Module interpretation/acknowledgement/cleanup, clean console shutdown and installed smoke.

Historical PR #47 runs additionally exercised live llama.cpp/model inference over the native AF_UNIX adapter. No new external model, network provider, GPU, credentials or provider account is required for this Module-configuration slice.
