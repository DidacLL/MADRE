# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It is being built both as an owner-installed product and as a public Module-development platform. The active implementation is Java 21.

Windows and Linux run the same application, Kernel, SDK, Module installation/configuration and reasoning-mechanism installation architecture.

Start with:

- [MADRE.md](MADRE.md) for durable product meaning and product gates;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for responsibility boundaries;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md) for the public executable Module model;
- [Reasoning Execution Contract](docs/architecture/MADRE-execution-contract.md) for the reasoning SPI and Kernel path;
- [Implementation Baseline](docs/implementation-baseline.md) for executable truth.

## Current distribution status

The repository currently produces a **developer distribution**, not the completed owner-deployable product.

Today, building and starting MADRE requires JDK 21, the checked-in Gradle wrapper, a reviewed properties file and an explicit properties-file argument to the generated launcher. These instructions are intentionally truthful about the current implementation; they are not the target first-run owner experience.

The owner-product release gate is a Windows/Linux installation/configuration/launch path that does not require the owner to understand Gradle, Java classpaths, ServiceLoader or internal MADRE property namespaces.

Likewise, the current independently compiled Module and reasoning fixtures prove a strong public contract foundation, but do not by themselves mean the public SDK is community-ready. Stable consumable publication, developer documentation, tooling/testkit and the full independent build/package/install journey remain product work.

## Developer build and package

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

Both hosts build the same Java sources and distribution. Gradle creates launch shims for the same `MadreMain` application:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

No container runtime, VM layer, hosted provider or external account is required for the mandatory build/test path.

## Module installation

A MADRE Module is a complete executable application/domain boundary. Its JAR provides:

```text
io.github.didacll.madre.sdk.registration.ModuleProvider
```

through:

```text
META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

The public provider declares one canonical identity and materializes exactly that Module:

```java
ModuleId moduleId();
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration);
```

`ModuleInstance` is one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Runtime discovery uses `modules.directory`; installed distributions default to their sibling `modules/` directory.

The shipped owner-interaction Module is packaged there, but `madre-app` does not compile against its concrete class. Independently built Modules use the same discovery/registration path. Bundling, class-loader placement and CORE assignment confer no privilege.

`verification/sdk-consumer` demonstrates the independent single-Module boundary. `verification/module-interoperability` separately compiles a caller and callee against the published SDK only and proves installed sensitive Module composition.

## Current Module configuration

Current owner/developer configuration is scoped by exact canonical Module identity:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

For example:

```properties
modules.config[phd.module].result-prefix=configured-
```

`madre-app` does not know what `result-prefix` means. It associates settings with the exact declared `ModuleId` and supplies immutable `ModuleProviderConfiguration`; the provider owns supported-key validation, parsing, typed settings and defaults.

An explicit setting for an uninstalled identity is rejected. Duplicate provider identities, provider/materialized-Module identity mismatch, malformed provider configuration and invalid executable bindings fail startup before a partial Module installation becomes reachable.

This generic string-delivery contract is the current executable path. It is not yet the owner-facing configurator. A future generic configurator must obtain the minimum provider-owned typed metadata it needs from installed artifacts rather than hard-code independently installed Module settings.

## Three invocation receivers

MADRE has three distinct receiver boundaries over exact installed Operations declared `PUBLIC`.

### Installed Module receiver

Each `ModuleContext` receives `ModuleDirectory` and `ModuleInvoker` facades bound by runtime assembly to that provider's canonical installed identity.

```java
context.directory().reachable(new ReachabilityQuery(materialType, sensitivity));
context.invoker().invoke(operationCall);
```

Neither API accepts a caller `ModuleId`; `ModuleInvoker.invoke` also accepts no receiver Privacy.

The caller must canonically reference foreign result Material types. The receiving boundary is fixed at `Privacy.MODULE`. Contract-valid S1-S4 foreign Material can cross unchanged when structurally declared; S5 or an undeclared foreign result type is rejected before caller exposure.

`PublicResultTransformer` is not run on this path.

### Owner-local host receiver

`OwnerModuleInvoker.invokeOwner` is the host/application path for the local owner. It executes a canonical installed `PUBLIC` Operation and returns Module-created Material unchanged, retaining its Sensitivity. It does not run the public transformer.

### External/PUBLIC host receiver

`PublicModuleInvoker.invokePublic` is the actual external/public disclosure path. A public binding must apply its Module-owned semantic result transformer before Material leaves this boundary. The result must be new declared Material with Sensitivity able to reach `Privacy.PUBLIC`.

The current console exposes the host boundaries generically:

```text
/modules
/invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
/invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

and non-interactively:

```text
madre <properties> --list-modules
madre <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

The legacy interactive `/invoke` alias remains external/PUBLIC.

## Current local text interaction

The present console is a transitional application adapter, not the final CORE interaction architecture.

`madre-app` can bind ordinary local text to one installed Module through `interaction.*`:

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

When valid, ordinary non-command text invokes the configured default Operation through owner-local invocation. `/standard <text>` invokes the configured standard Operation. `/updates` invokes only the configured Module collection Operation. `/sensitivity S1..S5` changes the current prompt Sensitivity explicitly.

`MadreMain` currently owns the console loop and commands. The shipped owner-interaction Module owns the semantic fast-lane behavior and delayed-result interpretation, but no actual interaction surface. `/updates` is currently how the foreground UX explicitly asks the Module to expose completed delayed work.

`roles.core` and `interaction.module` are independent in the current code. That is present executable behavior, not the target product relationship.

## CORE

`roles.core` is optional in the current implementation. If the configured Module is installed, the runtime resolves its identity; if it is absent or unresolved, MADRE still boots.

Durably, CORE means MADRE's default owner-interaction/coordinator Module. It is meaningful but non-privileged: CORE changes no type, Security Algebra value, Operation visibility, Module/owner-local/external-PUBLIC invocation authority, reasoning selection, scheduling, class-loader treatment, installation authority or host invocation authority.

The current runtime does not yet structurally qualify CORE or use it to drive the console. The next CORE interaction implementation must recover the smallest actual public contract from the shipped behavior rather than freeze the current `standard-prompt`, `fast-lane` or `collect-background` names as universal APIs.

The host product—not CORE—owns product-management mechanics such as artifact installation/uninstallation, persistent product configuration, CORE selection, startup/shutdown, diagnostics and health.

## Reasoning-adapter installation

Reasoning mechanisms are independently installable from Modules.

A reasoning adapter JAR provides:

```text
io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider
```

For text inference an adapter consumes the public reasoning SPI plus:

```text
io.github.didacll:madre-text-inference:0.1.0-SNAPSHOT
```

An adapter does not depend on `madre-app`, the Kernel reasoning registry, SQLite stores or schedulers.

Installed distributions discover reasoning-adapter JARs from sibling `reasoning/` by default. Override with:

```text
reasoning.directory=/absolute/path/to/reasoning-jars
```

The directory may be absent or empty. Artifact presence does not enable a mechanism. Providers receive a read-only view of current `reasoning.*` settings and own provider-specific parsing/validation. Privacy is explicit and is never inferred from endpoint, transport or location.

As with Modules, this string configuration is current developer-facing behavior rather than a completed generic configurator contract.

## Shipped owner-interaction Module settings

The shipped owner-interaction Module consumes the same `ModuleProviderConfiguration` mechanism as any independent Module. Current optional settings include:

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

Omitting them preserves `OwnerInteractionSettings.defaults()`. The Module validates its own keys.

Its current semantic behavior proves immediate reasoning, durable background reasoning, Module-owned pending state and Module interpretation/optional follow-up after restart. Kernel stores only opaque durable reasoning-runtime state.

## Search

Search is ordinary application/domain I/O, not a Kernel reasoning capability. `madre-web-search` provides reusable typed values and `madre-adapter-searxng` an ordinary Java client with no Kernel dependency.

## Start the current developer distribution

Build the distribution, copy/review the example configuration and run the Java application.

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

Additional Module JARs belong in the configured Module directory. Additional reasoning adapter JARs belong in the configured reasoning directory. Neither requires adding a concrete application compile-time dependency.

These commands are the current truthful run path. They should not be presented as satisfying the owner-deployable installation gate.

## Verification evidence

The mandatory CI matrix exercises ordinary checks, Javadocs/publication/package verification, isolated SDK Module builds, caller/callee interoperability, isolated reasoning-adapter builds, built-distribution installation/discovery, no-reasoning Module invocation, sensitive Module composition, owner-local and external/PUBLIC behavior, durable restart and installed-application smoke on Windows and Linux.

Historical PR #47 runs additionally exercised live llama.cpp/model inference over the native AF_UNIX adapter. The current mandatory acceptance does not require a container, hosted provider account, external service, GPU or credentials.