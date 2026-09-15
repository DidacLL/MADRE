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

The host does not know that this provider uses `endpoint`, `model`, `privacy`, or `location`. Those owner-facing names, their metadata, validation/defaults and their mapping onto the provider's persisted representation are all owned by the provider artifact.

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

## Module installation and configuration

A MADRE Module is a complete executable application/domain boundary. Its JAR provides `io.github.didacll.madre.sdk.registration.ModuleProvider` through Java ServiceLoader metadata. The provider declares one canonical identity and materializes exactly that Module:

```java
ModuleId moduleId();
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration);
```

`ModuleInstance` is one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Shipped and owner-supplied Modules use the same discovery/registration path. Bundling, class-loader placement and CORE assignment confer no privilege.

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

When no reasoning mechanism is materialized, normal startup remains valid and now points the Owner toward `madre reasoning providers`/`configure`. It does not force a startup wizard.

`roles.core` is optional and non-privileged. When resolved, it identifies the ordinary installed Module intended to provide the default owner-interaction/coordinator role. It changes no Security Algebra value, Operation visibility, invocation authority, reasoning selection, scheduling, class-loader treatment or installation authority.

The provider-configuration work does not redesign this interaction model. A later slice must make CORE lead ordinary owner interaction and natural delayed semantic follow-up without giving it host-only privileges.

## Reasoning-adapter public installation contract

Reasoning mechanisms are independently installable from Modules. A reasoning adapter JAR provides `io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider` and depends on the public reasoning SPI plus the computation contracts it implements, not on `madre-app` or Kernel internals.

Each installed provider declares:

- a stable `ReasoningProviderId`;
- a `ReasoningProviderDescriptor` containing only owner-facing display/help information and the provider's minimal configuration fields;
- a `ReasoningProviderConfigurator` for listing configured named instances, configuring/enabling an instance, disabling/enabling it, and removing its configuration;
- `materialize(...)` for producing enabled `ReasoningMechanism` values from the same read-only configuration.

Configuration fields use only the value kinds needed by the shipped/independent providers today: `TEXT`, `INTEGER` and `CHOICE`, with required/default/allowed-value/help/display information and integer bounds where applicable. This is not JSON Schema, reflection, an annotation framework or a universal settings language.

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

The Windows/Linux `Java 21 cross-platform build` workflow exercises `check`, architecture guards, Javadocs/publication, developer packages, isolated SDK Module/reasoning builds, Module-to-Module interoperability, no-reasoning boot, owner-local versus external/PUBLIC semantics, Module configuration, CORE/interaction independence and durable restart/recovery.

The exact-head `Native owner package` workflow builds MSI/DEB on the corresponding host, exercises the `jpackage` application image with machine `java` removed from `PATH`, proves fresh zero-argument bootstrap/restart/`doctor`/clean shutdown, verifies shipped provider discovery, configures shipped providers through the generic CLI, verifies provider-owned validation leaves persisted configuration unchanged, proves disable/re-enable/remove across restart, and then performs unattended native installer install/launch/uninstall. The MSI/DEB is retained as a downloadable workflow artifact.

The exact-head `Reasoning owner configuration` workflow independently publishes the public artifacts, builds `verification/reasoning-consumer` in its isolated Gradle build, places that third-party provider JAR in the conventional owner reasoning directory of a packaged application image, and drives the same generic provider metadata/configure/restart/disable/enable/remove path on Windows and Linux. No cloud account or reachable model endpoint is needed.

Historical PR #47 runs also exercised live llama.cpp/model inference over the native AF_UNIX adapter. The owner configurator does not change reasoning selection, execution or Security Algebra boundaries.

## Remaining product gaps

This slice deliberately does not complete generic Module configuration, Module install/remove/update management, reasoning JAR download/install/update/remove management, marketplace discovery, credential management, graphical settings, community SDK tooling/testkit, or the CORE-led owner-interaction redesign. Those remain separate product work rather than being hidden behind the reasoning-provider configurator.
