# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It is being built both as an owner-installed product and as a public Module-development and experimentation platform. The active implementation is Java 21.

Windows and Linux run the same application, Kernel, SDK, Module installation/configuration and reasoning-mechanism installation architecture.

Start with:

- [MADRE.md](MADRE.md) for durable product meaning, Owner reasoning and current development posture;
- [SDK developer guide](docs/sdk-development.md) for the current independent Module developer journey;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for responsibility boundaries;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md) for the public executable Module model;
- [Reasoning Execution Contract](docs/architecture/MADRE-execution-contract.md) for the reasoning SPI and Kernel path;
- [Implementation Baseline](docs/implementation-baseline.md) for executable truth.

## Native owner installation

MADRE has a native host foundation in addition to the Gradle developer distribution. The JDK 21 `jpackage` tool produces a Windows MSI and a Linux DEB. Both packages contain the MADRE application, shipped Module/reasoning artifacts and a bundled Java runtime, so an Owner does not install Java or Gradle to run the packaged product.

The native launcher accepts zero arguments. On the first normal launch MADRE creates stable per-user product locations and persists an inspectable owner configuration. A second launch reuses the same configuration.

Windows uses:

```text
configuration   %APPDATA%\MADRE\madre.properties
data            %LOCALAPPDATA%\MADRE
state           %LOCALAPPDATA%\MADRE\state
owner Modules   %LOCALAPPDATA%\MADRE\modules
owner reasoning %LOCALAPPDATA%\MADRE\reasoning
```

Linux follows the XDG base-directory convention:

```text
configuration   ${XDG_CONFIG_HOME:-~/.config}/madre/madre.properties
data            ${XDG_DATA_HOME:-~/.local/share}/madre
state           ${XDG_STATE_HOME:-~/.local/state}/madre
owner Modules   <data>/modules
owner reasoning <data>/reasoning
```

The Kernel durable database is written under the state location as `kernel-work.sqlite`; Module-owned state is rooted at `<state>/module-state`. Normal product state therefore does not depend on the current working directory or on a writable installation directory.

Shipped Module and reasoning-adapter JARs remain read-only program artifacts inside the application image. Normal packaged discovery scans those shipped directories plus the owner-writable Module/reasoning directories above. An explicit `modules.directory` or `reasoning.directory` still means exactly one explicitly selected directory, preserving deterministic developer/test isolation.

The first-run bootstrap deliberately contains no configured or enabled reasoning mechanism and does not invent an endpoint, model, credential, Privacy value or provider-specific setting. Installed provider types with zero configured instances are a valid MADRE state. The bootstrap currently preserves the shipped `roles.core` and `interaction.*` installation policy needed for the existing console behavior, without changing CORE privilege or semantics.

## Owner reasoning configuration

Reasoning adapter installation and reasoning instance configuration are separate facts. Merely placing an adapter JAR in a discovered reasoning directory never enables a mechanism.

Discover installed/configurable provider types:

```text
madre reasoning providers
```

This command works before any mechanism is configured. It displays each provider's stable provider-owned identity, display/help text and the small provider-owned field metadata needed by the generic host configurator. It does not derive identity from Java class name, JAR name, discovery order or shipped status.

Configure a repeatable named instance non-interactively:

```text
madre reasoning configure openai-compatible local-compatible \
  --set capability-id=compatible-text \
  --set endpoint=http://127.0.0.1:8081/v1/ \
  --set model=compatible-model \
  --set privacy=SECRET \
  --set location=LOCAL
```

The same command without `--set` arguments enters a simple interactive prompt rendered from the provider descriptor:

```text
madre reasoning configure openai-compatible local-compatible
```

The host does not know that this provider uses `endpoint`, `model`, `privacy`, `location`, `computation`, or any embedding-space field. Those owner-facing names, their metadata, validation/defaults and their mapping onto the provider's persisted representation are owned by the provider artifact.

Current HTTP providers can materialize different reasoning computation families from provider-owned configuration. Omitting the computation choice preserves the historical text-inference-v1 behavior. Not every transport must implement every family; the llama.cpp AF_UNIX provider remains text-inference-v1-only.

Inspect configured state without using raw internal property names:

```text
madre reasoning list
madre reasoning inspect openai-compatible/local-compatible
```

Disable without deleting the instance configuration, re-enable it later, or remove exactly that instance's configuration:

```text
madre reasoning disable openai-compatible/local-compatible
madre reasoning enable openai-compatible/local-compatible
madre reasoning remove openai-compatible/local-compatible
```

Configuration writes preserve unrelated host, Module and other provider settings. The host writes through a same-directory temporary file and atomically replaces the configuration where the platform supports it, with a safe replacement fallback. Provider validation runs before persistence; a rejected change leaves the previously persisted configuration intact. Configuration validation is local and does not require an endpoint to be reachable.

Privacy remains explicit. The shipped configurators never infer Privacy from endpoint, socket transport, provider identity or `ReasoningLocation`.

Current shipped provider identities are:

```text
llamacpp-http
llamacpp-unix
openai-compatible
```

The two llama.cpp transports remain distinct provider types because their configuration requirements differ. OpenAI-compatible means an explicitly configured compatible HTTP endpoint; it is not an OpenAI account integration.

This slice configures installed provider artifacts; it does not download, install, update or remove reasoning JARs. There is no credential vault, OAuth/account flow, graphical settings UI or generic Module configurator yet.

## Diagnostics

Run host diagnostics with:

```text
madre doctor
```

`doctor` reports the MADRE version, bundled Java runtime, resolved configuration/data/state locations, Module and reasoning artifact locations, configuration/discovery health, installed Module identities, CORE resolution, installed reasoning provider identities, configured provider-instance identities/state and successfully materialized reasoning-mechanism identities/count. It does not dump provider field values or raw provider configuration.

For advanced operation or isolated tests, override the persistent configuration explicitly:

```text
madre --config /absolute/path/madre.properties
```

The historical developer form remains accepted:

```text
madre /absolute/path/madre.properties
```

## Developer build and package

Source development uses JDK 21 and the checked-in Gradle wrapper. `installDist`/`distZip` remain developer packaging and intentionally continue to support explicit temporary properties files for isolated acceptance.

Windows:

```text
.\gradlew.bat --no-daemon --build-cache check javadoc publish installDist distZip
.\gradlew.bat --no-daemon --build-cache :madre-app:jpackageAppImage :madre-app:nativePackage
.\gradlew.bat --no-daemon --build-cache -p verification/sdk-consumer jar
.\gradlew.bat --no-daemon --build-cache -p verification/module-interoperability :callee:jar :caller:jar
.\gradlew.bat --no-daemon --build-cache -p verification/reasoning-consumer jar
```

Linux:

```text
./gradlew --no-daemon --build-cache check javadoc publish installDist distZip
./gradlew --no-daemon --build-cache :madre-app:jpackageAppImage :madre-app:nativePackage
./gradlew --no-daemon --build-cache -p verification/sdk-consumer jar
./gradlew --no-daemon --build-cache -p verification/module-interoperability :callee:jar :caller:jar
./gradlew --no-daemon --build-cache -p verification/reasoning-consumer jar
```

The developer launchers remain:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

For deterministic source/developer execution, copy/review `config/madre.properties.example` and pass it explicitly. Its relative paths are developer conveniences; native first-run startup does not copy this template and instead persists conventional absolute host locations.

No container runtime, VM layer, hosted provider or external account is required for the mandatory build/test/package path.

## SDK developer experimentation

The SDK experimentation foundation is executable. The public artifact set includes:

- `madre-bom` for dependency alignment;
- stable `madre-sdk` as the ownership-demonstrated Module foundation;
- `madre-sdk-testkit` for deterministic Module semantic tests over public contracts;
- `madre-sdk-experimental` as an explicit 0.x incubation artifact;
- `madre-reasoning-spi` as the independent reasoning-provider boundary;
- stable computation-contract artifacts `madre-text-inference`, `madre-text-generation` and `madre-embeddings` as separate lower-layer inference surfaces.

`madre-sdk-testkit` has no `madre-app` or Kernel implementation dependency. Its programmable reasoning support is generic over arbitrary `ReasoningComputation<R>` and deliberately does not simulate Kernel scheduling, mechanism selection, Security Algebra receiver enforcement, resource coordination or SQLite durability.

`madre-sdk-experimental` currently contains one typed `ModuleDefinitionBuilder`. It reduces repetitive collection assembly while producing the stable `ModuleDefinition` and preserving the stable containment model, including Agent-owned Workflows. Stable SDK, testkit, Kernel, application and reasoning SPI do not depend on it. Experimental APIs may change or disappear during 0.x; graduation is explicit and evidence-driven.

The BOM aligns `madre-algebra`, `madre-sdk`, `madre-sdk-testkit`, `madre-sdk-experimental`, `madre-reasoning-spi`, `madre-text-inference`, `madre-text-generation` and `madre-embeddings`. It does not make adapters, Kernel or the installed application transitive Module dependencies.

Computation-contract artifacts do not define Module domains. A Module depends on one only when its own semantic behavior genuinely requires that inference computation. Adding an inference family is not an instruction to create a corresponding Module, Material model or stable high-level SDK abstraction.

The current verification publication remains repository-local at `build/isolated-repository`. It is not yet a public remote 0.x release channel. The cross-platform `SDK developer acceptance` workflow publishes those artifacts, copies `verification/sdk-consumer` to a runner-temporary directory outside the checkout, runs its deterministic tests and JAR build there, installs only the resulting JAR in the normal owner-writable Module directory of a packaged MADRE application image, proves ServiceLoader discovery with `doctor`, and invokes it through owner-local and external/PUBLIC receiver paths.

The independent fixture uses the experimental builder only from test scope. The built Module JAR therefore proves that experimental authoring can aid experimentation without becoming a production runtime requirement. Its tests also resolve the newer computation-contract artifacts through the BOM without turning them into runtime dependencies of the installable fixture Module.

A MADRE Gradle Module plugin was deliberately not added in this slice. After version alignment, the demonstrated build-specific requirements are Java 21 and standard Java ServiceLoader metadata. A plugin remains a future tooling increment if repeated external projects demonstrate enough additional packaging/validation friction to justify another public build API.

See [docs/sdk-development.md](docs/sdk-development.md) for the runnable independent-project setup, Module anatomy, reasoning/testing patterns, composition and installation guidance.

## Module installation and configuration

A MADRE Module is a complete executable application/domain boundary. Its JAR provides `io.github.didacll.madre.sdk.registration.ModuleProvider` through Java ServiceLoader metadata. The provider declares one canonical identity and materializes exactly that Module:

```java
ModuleId moduleId();
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration);
```

`ModuleInstance` is one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Shipped and owner-supplied Modules use the same discovery/registration path. Bundling, class-loader placement and CORE assignment confer no privilege.

A Module should exist because it owns coherent semantic/application behavior. Reusable technical facilities—search clients, embedding computations, storage libraries, transports, databases or model APIs—may be used by Modules without becoming Modules themselves.

Current Module configuration remains provider-owned string configuration:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

`madre-app` only associates the exact canonical Module identity with an immutable string map. The Module provider owns supported-key validation, parsing, typed settings and defaults. An explicit setting for an uninstalled identity is rejected; duplicate providers, identity mismatch and invalid bindings fail before partial installation becomes reachable.

The reasoning-provider metadata/configuration contract described above is intentionally reasoning-specific. This slice does not add `ModuleProvider` configuration metadata or a generic settings framework.

## Three invocation receivers

MADRE has three distinct receiver boundaries over exact installed Operations declared `PUBLIC`.

An installed Module receives caller-bound `ModuleDirectory`/`ModuleInvoker`. It supplies neither caller identity nor receiver Privacy. A foreign result must use a type canonically referenced by the caller and its Sensitivity must reach fixed `Privacy.MODULE`. The exact callee Material crosses unchanged and `PublicResultTransformer` does not run.

`OwnerModuleInvoker.invokeOwner` is the local host receiver. It executes the canonical Operation and returns Module-created Material unchanged at its actual Sensitivity.

`PublicModuleInvoker.invokePublic` is the external/PUBLIC disclosure receiver. It requires the Module-owned semantic result transformer to create new declared Material whose Sensitivity can reach `Privacy.PUBLIC`.

The developer console exposes these generic boundaries through `/invoke-owner` and `/invoke-public`, and non-interactively through:

```text
madre --config <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre --config <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

## Current local text interaction and CORE

The present console remains a transitional application adapter. `interaction.*` selects the installed Module/Operations/Material types used by ordinary text, `/standard`, `/updates` and explicit `/sensitivity`. The shipped owner-interaction Module owns semantic immediate/durable reasoning behavior and delayed-result interpretation; `MadreMain` still owns presentation and the command loop.

When no reasoning mechanism is materialized, normal startup remains valid and points the Owner toward `madre reasoning providers`/`configure`. It does not force a startup wizard.

`roles.core` is optional and non-privileged. When resolved, it identifies the ordinary installed Module intended to provide the default owner-interaction/coordinator role. It changes no Security Algebra value, Operation visibility, invocation authority, reasoning selection, scheduling, class-loader treatment or installation authority.

The current interaction model remains unfinished, but it is treated as a major SDK/inference experimentation consumer rather than the next universal API to freeze. CORE should eventually lead ordinary owner interaction and natural delayed semantic follow-up without host-only privilege; the stable structural contract should be recovered from repeated successful experiments rather than standardized directly from the current console/Operation names.

## Reasoning-adapter public installation contract

Reasoning mechanisms are independently installable from Modules. A reasoning adapter JAR provides `io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider` and depends on the public reasoning SPI plus the computation contracts it implements, not on `madre-app` or Kernel internals.

Each installed provider declares:

- a stable `ReasoningProviderId`;
- a `ReasoningProviderDescriptor` containing only owner-facing display/help information and the provider's minimal configuration fields;
- a `ReasoningProviderConfigurator` for listing configured named instances, configuring/enabling an instance, disabling/enabling it, and removing its configuration;
- `materialize(...)` for producing enabled `ReasoningMechanism` values from the same read-only configuration.

Configuration fields use only the value kinds needed by the shipped/independent providers today: `TEXT`, `INTEGER` and `CHOICE`, with required/default/allowed-value/help/display information and integer bounds where applicable. This is the current executable baseline, not a universal settings language or a claim that all future engine/model controls fit these kinds.

`ReasoningCapability.supports(C computation)` is the generic local compatibility hook for value-level mechanism constraints. Existing implementations remain compatible through the default accepting behavior. Kernel may use it when filtering candidates, but Kernel does not interpret why a mechanism rejects a computation; for example, embedding-space compatibility remains mechanism-owned rather than becoming Kernel embedding logic.

Providers own parsing, validation and raw persistence mapping. `madre-app` applies a provider-produced `ReasoningProviderConfigurationUpdate` generically and contains no concrete llama.cpp/OpenAI-compatible configuration key branches. Architecture checks reject compile-time app dependencies/imports on the shipped adapter implementations.

The existing raw `reasoning.*` representation remains executable for compatibility and advanced developer use; owners of the native product no longer need to know it for normal reasoning setup.

## Developer configuration example

For source/developer isolation:

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

The example intentionally retains provider-specific disabled raw examples as compatibility/reference material. Native first-run bootstrap does not copy those provider values; the generic reasoning commands are the owner-facing setup path.

## Verification evidence

The Windows/Linux `Java 21 cross-platform build` workflow exercises `check`, architecture guards, Javadocs/publication, developer packages, isolated SDK Module/reasoning builds, Module-to-Module interoperability, no-reasoning boot, owner-local versus external/PUBLIC semantics, Module configuration, CORE/interaction independence, durable restart/recovery, and the shipped heterogeneous generation/embedding protocol and configuration tests.

The exact-head `SDK developer acceptance` workflow separately builds the independent Module project from a runner-temporary directory against the verification publication, executes its public-testkit tests including arbitrary non-text reasoning and current computation-contract consumption, verifies its ServiceLoader packaging, installs the JAR in the packaged product's normal owner-writable Module directory, proves discovery and invokes both owner-local and external/PUBLIC receiver paths on Windows and Linux.

The exact-head `Native owner package` workflow builds MSI/DEB on the corresponding host, exercises the `jpackage` application image with machine `java` removed from `PATH`, proves fresh zero-argument bootstrap/restart/`doctor`/clean shutdown, verifies shipped provider discovery, configures shipped providers through the generic CLI, verifies provider-owned validation leaves persisted configuration unchanged, proves disable/re-enable/remove across restart, and then performs unattended native installer install/launch/uninstall. The MSI/DEB is retained as a downloadable workflow artifact.

The exact-head `Reasoning owner configuration` workflow independently publishes the public artifacts, builds `verification/reasoning-consumer` in its isolated Gradle build, places that third-party provider JAR in the conventional owner reasoning directory of a packaged application image, and drives the same generic provider metadata/configure/restart/disable/enable/remove path on Windows and Linux. No cloud account or reachable model endpoint is needed.

Historical PR #47 runs also exercised live llama.cpp/model inference over the native AF_UNIX adapter. The owner configurator does not change reasoning selection, execution or Security Algebra boundaries.

## Remaining product gaps

The current work materially improves the public experimentation/developer environment, but MADRE is not yet a community-ready public SDK 0.x release.

The largest SDK release gaps are a real external artifact repository and release/version/signing mechanics, release-quality API compatibility policy, additional unrelated external-project feedback, and build/project-generation tooling if that feedback demonstrates enough remaining friction. The current verification repository and in-repository source fixture are acceptance infrastructure, not a distribution channel.

Generic Module configuration, Module install/remove/update management, reasoning JAR download/install/update/remove management, marketplace discovery, credential management, graphical settings, and the CORE-led owner-interaction evolution remain separate unfinished product work. Multimodal computation, semantic-memory/RAG/planning frameworks, generic tool calling, audio/voice and MCP are also intentionally not implied by the current foundation. They should be pursued only when real semantic or inference experiments establish a concrete need and correct responsibility boundary.