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
.\gradlew.bat --no-daemon --build-cache -p verification/module-interoperability :callee:jar :caller:jar
.\gradlew.bat --no-daemon --build-cache -p verification/reasoning-consumer jar
```

Linux:

```text
./gradlew --no-daemon --build-cache check javadoc publish installDist distZip
./gradlew --no-daemon --build-cache -p verification/sdk-consumer jar
./gradlew --no-daemon --build-cache -p verification/module-interoperability :callee:jar :caller:jar
./gradlew --no-daemon --build-cache -p verification/reasoning-consumer jar
```

Both hosts build the same Java sources and distribution. Gradle creates native launch shims for the same `MadreMain` application:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

CI verifies executable changes on both Windows and Linux. Production changes additionally run Javadocs, publication, packaging and installed-application smoke. The isolated Module, caller/callee interoperability and reasoning-adapter fixtures are built outside the normal application dependency graph and then installed into the built distribution for real discovery/execution checks.

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

`ModuleInstance` is one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Runtime discovery uses `modules.directory`; installed distributions default to their sibling `modules/` directory.

The shipped owner-interaction Module is packaged there but `madre-app` does not compile against its concrete class. Independently built Modules use the same discovery/registration path. Bundling, class-loader placement and CORE designation confer no privilege.

`verification/sdk-consumer` demonstrates the independent single-Module boundary. `verification/module-interoperability` separately compiles a caller and callee against the published SDK only and proves installed sensitive Module composition.

## Owner Module configuration

Owner-supplied installation configuration is scoped by the provider's exact canonical `ModuleId`:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

For example, the independent verification Module owns `result-prefix`:

```properties
modules.config[phd.module].result-prefix=configured-
```

With that property omitted, its installed PUBLIC `inspect` path returns `public:hello`; with it present, the same provider returns `public:configured-hello`.

`madre-app` does not know what `result-prefix` means. It only associates properties with the exact declared `ModuleId` and supplies immutable `ModuleProviderConfiguration`; the provider owns supported-key validation, parsing, typed settings and defaults.

Square brackets are structural delimiters. Valid Module identities may contain dots, dashes and underscores but cannot contain brackets, so identities are matched exactly without guessing dot-separated namespaces. Provider class names, JAR names, discovery order, shipped status and CORE assignment are not configuration identities.

An explicit setting for an uninstalled Module identity is rejected rather than ignored. Duplicate provider identities, provider/materialized-Module identity mismatches, malformed provider configuration and invalid executable bindings fail startup before any Module registration becomes reachable. If registration itself fails, already-created registrations are rolled back.

Module configuration is separate from `interaction.*` presentation policy and from `reasoning.*` adapter configuration.

## Three invocation receivers

MADRE has three deliberately distinct receiver boundaries over exact installed Operations declared `PUBLIC`.

### Installed Module receiver

Each `ModuleContext` receives a `ModuleDirectory` and `ModuleInvoker` bound by runtime assembly to that provider's canonical installed `ModuleId`.

```java
context.directory().reachable(new ReachabilityQuery(materialType, sensitivity));
context.invoker().invoke(operationCall);
```

Neither API accepts a caller `ModuleId`. `ModuleInvoker.invoke` also accepts no receiver Privacy. The calling identity and Module receiver boundary therefore come from installation/runtime structure rather than claims made by Module code.

A target Operation still declares the Privacy at which it accepts input. Only target `PUBLIC` Operations are discoverable/callable. PRIVATE Operations remain Module-internal.

For results, the calling Module's canonical `publicMaterialReferences` declares which foreign Material type identities it can receive. The receiving Privacy is fixed at `Privacy.MODULE`; the caller does not choose it. If a callee returns contract-valid S4 Material of a declared foreign type, the exact callee Material reaches the caller unchanged, preserving identity, owner and Sensitivity. S5 or an undeclared foreign type is rejected before caller exposure. The caller may interpret the value and create a new caller-owned Material with a new identity.

`PublicResultTransformer` is not run on this receiver path.

### Owner-local host receiver

`OwnerModuleInvoker.invokeOwner` is the host/application boundary for the local owner. It resolves the exact canonical installed `PUBLIC` Operation, accepts a real `OperationCall`, validates the declared output contract and returns the Module-created Material unchanged. It does not apply `PublicResultTransformer` and does not lower Sensitivity merely because the owner sees the result locally.

`OwnerModuleInvoker` is not part of `ModuleContext`.

### External/PUBLIC host receiver

`PublicModuleInvoker.invokePublic` is the external/public disclosure boundary. A public binding must apply its Module-owned semantic result transformer before Material leaves this boundary. The result must be new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`; raw internal Material cannot cross this path.

`PublicModuleInvoker` is also not part of `ModuleContext`.

The replaceable local console exposes the two host boundaries generically:

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

Both host routes decode input through the exact installed canonical `MaterialType` codec and construct the canonical `OperationCall`. For a consequential Operation with one EffectProfile the application selects that exact declared profile and supplies no invented causal participant. If an Operation has multiple profiles, the generic selector is `<operation>@<effect-profile>`.

Owner-local output prints its retained Sensitivity together with payload; external/PUBLIC output remains the minimized public payload. The legacy interactive `/invoke` alias remains PUBLIC.

## Independent installed interoperability proof

`verification/module-interoperability/callee` and `verification/module-interoperability/caller` are separate SDK-only artifacts. The built-distribution acceptance installs both JARs on Windows and Linux and proves:

- callee owner-local execution returns raw S4 `classified:hello`;
- caller invokes the same callee behavior through its Module receiver and receives the exact S4 callee Material with callee ownership/identity retained;
- caller interprets it and creates a different caller-owned S4 Material identity;
- caller owner-local behavior can expose its adapted Material without public minimization;
- callee and caller external/PUBLIC paths each apply their mandatory Module-owned S1 semantic transformer;
- S5 foreign Material is blocked at `Privacy.MODULE` before caller exposure;
- a lower-sensitivity foreign type absent from caller declarations is blocked before caller exposure;
- a PRIVATE callee Operation is not reachable through the caller's directory;
- no public Module API accepts a caller identity to forge.

## Local text interaction presentation

`madre-app` can optionally bind its replaceable local text console to one ordinary installed Module through the application-local `interaction.*` namespace. This is installation/presentation policy, not an SDK role, Module subtype, Kernel abstraction or CORE privilege.

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

If any `interaction.*` configuration is present, startup resolves it after Module discovery and validates it against exact installed declarations. The configured Module and Operations must exist; Operations must be `PUBLIC`; configured input Material must be declared and accepted; console input and every possible declared output must be text; EffectProfile selection must be unambiguous; and configured Sensitivities must be ordinary values able to reach the receiving Operation boundary. Invalid bindings fail startup rather than silently selecting another Module or semantic mode.

When valid, ordinary non-command text invokes `interaction.default-operation` through owner-local invocation. `/standard <text>` invokes the configured standard Operation owner-locally. `/updates` invokes only the configured Module collection Operation; the console does not poll destructively, interpret Kernel results or add callbacks. `/sensitivity S1..S5` explicitly changes current prompt Sensitivity; `SYSTEM_RESERVED` is rejected.

`roles.core`, `interaction.module` and `modules.config[...]` are independent facts.

## Reasoning-adapter installation

Reasoning mechanisms are independently installable from Modules and use a different directory and provider contract.

A reasoning adapter is an ordinary JVM JAR that provides:

```text
io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider
```

through the service-provider mechanism. The public adapter artifact is:

```text
io.github.didacll:madre-reasoning-spi:0.1.0-SNAPSHOT
```

For text inference an adapter additionally consumes:

```text
io.github.didacll:madre-text-inference:0.1.0-SNAPSHOT
```

An adapter does not depend on `madre-app`, `ReasoningCapabilityRegistry`, SQLite stores, schedulers or other Kernel implementation classes.

Installed distributions discover reasoning-adapter JARs from sibling `reasoning/` by default. Override with:

```text
reasoning.directory=/absolute/path/to/reasoning-jars
```

The reasoning directory may be absent or empty. Artifact presence does not enable a mechanism. Providers receive a read-only view of owner-supplied `reasoning.*` configuration and own provider-specific parsing/validation. One adapter can materialize multiple mechanism instances.

The shipped llama.cpp AF_UNIX, explicit loopback-HTTP compatibility and OpenAI-compatible adapter artifacts are discovered through the same path as independent adapters. Privacy is explicit provider configuration and is never inferred from endpoint, transport or location.

`verification/reasoning-consumer` proves independent adapter installation and requires no network/model/GPU/credentials.

## Shipped owner-interaction Module configuration

The shipped owner-interaction Module is ordinary installed Module behavior and may optionally be assigned CORE. Its provider consumes the same `ModuleProviderConfiguration` mechanism as any independent Module.

Its optional settings are:

```properties
modules.config[io.github.didacll.madre.owner-interaction].foreground-maximum-tokens=256
modules.config[io.github.didacll.madre.owner-interaction].background-maximum-tokens=512
modules.config[io.github.didacll.madre.owner-interaction].foreground-timeout-ms=90000
modules.config[io.github.didacll.madre.owner-interaction].background-timeout-ms=300000
modules.config[io.github.didacll.madre.owner-interaction].background-retry-attempts=3
modules.config[io.github.didacll.madre.owner-interaction].background-retry-delay-ms=5000
# optional selection constraints
# modules.config[io.github.didacll.madre.owner-interaction].foreground-location=LOCAL
# modules.config[io.github.didacll.madre.owner-interaction].foreground-maximum-latency-ms=30000
# modules.config[io.github.didacll.madre.owner-interaction].background-location=LOCAL
# modules.config[io.github.didacll.madre.owner-interaction].background-maximum-latency-ms=30000
```

All are optional. Omitting the scope preserves `OwnerInteractionSettings.defaults()`. The Module itself validates unknown/malformed values; `madre-app` contains no concrete knowledge of these fields.

Its EffectProfiles describe real consequences, not reasoning itself: `standard-prompt` has no EffectProfile, `fast-lane` retains `WRITE/AUTONOMOUS` `durable-background-write`, and `collect-background` retains `DELETE/LIVE_INTERACTION` `acknowledge-completed-background`.

## CORE, reasoning and search

`roles.core` is optional. CORE is an ordinary installed Module identity lookup; it creates no subtype, invocation authority, Module configuration authority, Security Algebra value, scheduling lane or required Operation names. MADRE boots with no CORE configured and tolerates a configured CORE that is not installed.

Reasoning mechanisms are optional at boot. An Operation that later needs reasoning may fail when invoked if no compatible mechanism is available, but mechanism absence is not a startup error. A Module Operation that uses no reasoning remains fully usable with an absent or empty reasoning directory.

Search is ordinary application/domain I/O, not a Kernel reasoning capability. `madre-web-search` provides reusable typed search values and `madre-adapter-searxng` provides an ordinary Java `SearxngClient` with no Kernel dependency.

## Start MADRE

Build the distribution, copy/review the example configuration and run the single Java application.

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

Additional owner-installed Module JARs belong in the configured Module directory. Configure each through its documented `modules.config[<canonical ModuleId>].*` keys. Additional reasoning adapter JARs belong in the configured reasoning directory and use provider-owned `reasoning.*` settings. Neither requires adding a concrete application compile-time dependency.

Reasoning failures remain reasoning-runtime failures. Kernel SQLite stores only durable reasoning work, attempts and retained opaque payloads; semantic interpretation belongs to the originating Module.

## Verification evidence

The mandatory CI matrix exercises `check`, Javadocs/publication/package verification, isolated SDK Module builds, isolated caller/callee interoperability builds, isolated reasoning-adapter builds, built-distribution installation/discovery, no-reasoning Module invocation, sensitive Module-to-Module composition, independent reasoning execution, owner-local and external/PUBLIC transformation behavior, durable restart and installed-application smoke on Linux and Windows.

Historical PR #47 runs additionally exercised live llama.cpp/model inference over the native AF_UNIX adapter. The current interoperability slice adds no external process transport, hosted model, network provider, GPU, credentials or provider account requirement.
