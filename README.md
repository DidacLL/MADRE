# MADRE

MADRE is personal, owner-sovereign software for modular applications that can use bounded reasoning while keeping information reach explicit. The active implementation is Java 21.

Windows and Linux run the same application, Kernel, SDK, Module installation and reasoning-mechanism installation architecture. There is no Windows compatibility product layered over a Unix implementation and no Linux-specific public runtime.

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

The provider constructs a `ModuleInstance`: one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Runtime discovery uses `modules.directory`; installed distributions default to their sibling `modules/` directory.

The shipped owner-interaction Module is packaged there but `madre-app` does not compile against its concrete class. Independently built Modules use the same discovery/registration path. Bundling, class-loader placement and CORE designation confer no privilege.

`verification/sdk-consumer` demonstrates the independent Module boundary. It depends only on:

```text
io.github.didacll:madre-sdk:0.1.0-SNAPSHOT
```

and produces `independent-module.jar`.

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

The shipped llama.cpp AF_UNIX, explicit loopback-HTTP compatibility and OpenAI-compatible adapters are copied into `reasoning/` and discovered through exactly the same path as independently supplied adapters. `madre-app` has no concrete provider dependency or provider-type switch/factory table.

Privacy is explicit provider configuration and is never inferred from endpoint, transport or location. Invalid enabled configuration fails startup instead of silently changing mechanism semantics. Prepared authentication/session state remains outside MADRE.

`verification/reasoning-consumer` proves independent adapter installation. It depends only on published `madre-reasoning-spi` and `madre-text-inference`, produces `independent-reasoning.jar`, is copied into the built distribution's `reasoning/` directory, and provides deterministic text inference requiring no network/model/GPU/credentials.

## Shipped owner-interaction Module

The shipped owner-interaction Module is ordinary installed Module behavior and may optionally be assigned CORE. Its EffectProfiles describe real consequences, not the fact that reasoning occurred.

`standard-prompt` has no EffectProfile: it performs immediate reasoning and creates response Material, but does not itself perform persistent domain mutation or an external action.

`fast-lane` retains one `WRITE/AUTONOMOUS` EffectProfile named `durable-background-write`. The consequential variant is the durable reasoning submission plus Module-owned pending-state persistence that continues beyond foreground interaction; reasoning computation itself is not the Risk.

`collect-background` is a Module-specific owner-facing Operation with a `DELETE/LIVE_INTERACTION` profile named `acknowledge-completed-background`. It interprets completed opaque reasoning output into Module Material, acknowledges durable Kernel work and removes the Module's completed pending semantic state. It does not create a generic callback, continuation or scheduler abstraction.

## CORE and reasoning independence

`roles.core` is optional. CORE is an ordinary installed Module identity lookup; it creates no subtype, invocation authority, Security Algebra value, scheduling lane or required Operation names. MADRE boots with no CORE configured and tolerates a configured CORE that is not installed.

Reasoning mechanisms are also optional at boot. An Operation that later needs reasoning may fail when invoked if no compatible mechanism is available, but mechanism absence is not a startup error. A Module Operation that uses no reasoning remains fully usable with an absent or empty reasoning directory.

CORE designation does not affect owner-local/public invocation semantics, reasoning installation, selection or preference.

## Reasoning execution

Kernel execution is reasoning-only. The Module-facing `ReasoningService` accepts only nominal `ReasoningComputation<R>` values through `ReasoningRequest`; it is not a generic action/tool/physical-work dispatcher.

Selection uses exact computation-contract compatibility, carried Sensitivity versus explicit receiving Privacy, observed availability, resource capacity, typed location/latency preferences, configured ordinary installation preference and deterministic identity ordering. Operation Risk is not part of reasoning selection and reasoning manifests carry no action-realizer Integrity.

Immediate and durable reasoning share the same mechanism registry/selection/resource/failure semantics. Durable SQLite work stores stable reasoning-contract identity plus opaque computation/result bytes, not adapter implementation class names. Work can therefore resume after restart when the compatible contract/mechanism is registered again.

The independent Module fixture contains two PUBLIC Operations used by CI:

```text
phd.module/inspect   # no reasoning; proves zero-ReasoningCapability execution
phd.module/reason    # real ReasoningRequest through independently installed adapter
```

The reasoning-backed path must return a Module-created PUBLIC result such as:

```text
public:reasoned:independent:hello
```

and CI rejects leakage of the Module's internal `private:reasoned:` Material.

## Search

Search is ordinary application/domain I/O, not a Kernel reasoning capability.

`madre-web-search` provides reusable typed search values and `madre-adapter-searxng` provides an ordinary Java `SearxngClient`. The SearXNG adapter has no Kernel dependency and does not implement `ReasoningCapability`.

The former standalone shipped WebSearch Module and generic SearXNG Kernel capability were removed. A future domain Module that genuinely owns research/search behavior may depend on the search library/client directly.

## Start MADRE

Build the distribution, copy/review the example configuration and run the single Java application. You may remove `roles.core`, leave all shipped reasoning instances disabled and even point `reasoning.directory` at an empty directory for a bare runtime.

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

Additional owner-installed Module JARs belong in the configured Module directory. Additional reasoning adapter JARs belong in the configured reasoning directory. Neither requires adding a concrete application compile-time dependency.

Reasoning failures remain reasoning-runtime failures. Kernel SQLite stores only durable reasoning work, attempts and retained opaque payloads; semantic interpretation belongs to the originating Module.

## Verification evidence

The mandatory CI matrix exercises `check`, Javadocs/publication/package verification, both isolated fixture builds, built-distribution installation/discovery, no-reasoning Module invocation, independent reasoning execution, PUBLIC semantic transformation and installed-application smoke on Linux and Windows.

It additionally discovers the shipped owner-interaction Module from `modules/` and the independent deterministic reasoning adapter from `reasoning/`, proves that S5 owner-local `standard-prompt` returns useful S5 Module Material, proves that the same sensitive call through PUBLIC is semantically minimized, repeats owner-local behavior with and without CORE assignment, and exercises `fast-lane` across a real process restart with the same Kernel SQLite database and Module state directory. The restart proof gates background completion deterministically, resumes durable work after restart, invokes Module-specific `collect-background`, verifies Module interpretation, acknowledgement and pending-state cleanup, and uses no external service.

Historical PR #47 runs additionally exercised live llama.cpp/model inference over the native AF_UNIX adapter. No new live external provider/model evidence is required for this deterministic owner-local slice.
