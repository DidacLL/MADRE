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

MADRE now has a native host foundation in addition to the Gradle developer distribution. The JDK 21 `jpackage` tool produces a Windows MSI and a Linux DEB. Both packages contain the MADRE application, shipped Module/reasoning artifacts and a bundled Java runtime, so an Owner does not install Java or Gradle to run the packaged product.

The native launcher accepts zero arguments. On the first normal launch MADRE creates stable per-user product locations and persists an inspectable owner configuration. A second launch reuses the same configuration without rewriting it.

Windows uses:

```text
configuration  %APPDATA%\MADRE\madre.properties
data           %LOCALAPPDATA%\MADRE
state          %LOCALAPPDATA%\MADRE\state
owner Modules  %LOCALAPPDATA%\MADRE\modules
owner reasoning %LOCALAPPDATA%\MADRE\reasoning
```

Linux follows the XDG base-directory convention:

```text
configuration  ${XDG_CONFIG_HOME:-~/.config}/madre/madre.properties
data           ${XDG_DATA_HOME:-~/.local/share}/madre
state          ${XDG_STATE_HOME:-~/.local/state}/madre
owner Modules  <data>/modules
owner reasoning <data>/reasoning
```

The Kernel durable database is written under the state location as `kernel-work.sqlite`; Module-owned state is rooted at `<state>/module-state`. Normal product state therefore does not depend on the current working directory or on a writable installation directory.

Shipped Module and reasoning-adapter JARs remain read-only program artifacts inside the application image. Normal packaged discovery scans those shipped directories plus the owner-writable Module/reasoning directories above. An explicit `modules.directory` or `reasoning.directory` still means exactly one explicitly selected directory, preserving deterministic developer/test isolation.

The first-run bootstrap deliberately contains no enabled reasoning mechanism and does not invent an endpoint, model, credential, Privacy value or provider-specific setting. The shipped reasoning adapter artifacts may be present while zero mechanisms materialize; that is a valid MADRE state. The bootstrap currently preserves the shipped `roles.core` and `interaction.*` installation policy needed for the existing console behavior, without changing CORE privilege or semantics.

Run host diagnostics with:

```text
madre doctor
```

`doctor` reports the MADRE version, bundled Java runtime, resolved configuration/data/state locations, Module and reasoning artifact locations, configuration/discovery health, installed Module identities, CORE resolution and materialized reasoning-mechanism identities/count. It does not dump provider configuration or secrets.

For advanced operation or isolated tests, override the persistent configuration explicitly:

```text
madre --config /absolute/path/madre.properties
```

The historical developer form remains accepted:

```text
madre /absolute/path/madre.properties
```

Native installation is the owner-facing packaging proof for this slice. It does not complete the entire owner-deployable product gate: provider-aware first-run configuration, Module/reasoning lifecycle management and the dedicated CORE-led owner-interaction UX remain separate product work.

## Developer build and package

Source development still uses JDK 21 and the checked-in Gradle wrapper. `installDist`/`distZip` remain developer packaging and intentionally continue to support explicit temporary properties files for isolated acceptance.

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

Current configuration remains provider-owned string configuration:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

`madre-app` only associates the exact canonical Module identity with an immutable string map. The Module provider owns supported-key validation, parsing, typed settings and defaults. An explicit setting for an uninstalled identity is rejected; duplicate providers, identity mismatch and invalid bindings fail before partial installation becomes reachable.

This is executable configuration truth, not the final owner configurator contract. The native bootstrap does not create generic provider metadata or hard-code Module-owned setting vocabularies.

## Three invocation receivers

MADRE has three distinct receiver boundaries over exact installed Operations declared `PUBLIC`.

An installed Module receives caller-bound `ModuleDirectory`/`ModuleInvoker`. It supplies neither caller identity nor receiver Privacy. A foreign result must use a type canonically referenced by the caller and its Sensitivity must reach fixed `Privacy.MODULE`. The exact callee Material crosses unchanged and `PublicResultTransformer` does not run.

`OwnerModuleInvoker.invokeOwner` is the local host receiver. It executes the canonical Operation and returns Module-created Material unchanged at its actual Sensitivity.

`PublicModuleInvoker.invokePublic` is the external/PUBLIC disclosure receiver. It requires the Module-owned semantic result transformer to create new declared Material able to reach `Privacy.PUBLIC`.

The developer console exposes these generic boundaries through `/invoke-owner` and `/invoke-public`, and non-interactively through:

```text
madre --config <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre --config <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

## Current local text interaction and CORE

The present console remains a transitional application adapter. `interaction.*` selects the installed Module/Operations/Material types used by ordinary text, `/standard`, `/updates` and explicit `/sensitivity`. The shipped owner-interaction Module owns semantic immediate/durable reasoning behavior and delayed-result interpretation; `MadreMain` still owns presentation and the command loop.

`roles.core` is optional and non-privileged. When resolved, it identifies the ordinary installed Module intended to provide the default owner-interaction/coordinator role. It changes no Security Algebra value, Operation visibility, invocation authority, reasoning selection, scheduling, class-loader treatment or installation authority.

The native packaging work does not redesign this interaction model. A later slice must make CORE lead ordinary owner interaction and natural delayed semantic follow-up without giving it host-only privileges.

## Reasoning-adapter installation

Reasoning mechanisms are independently installable from Modules. A reasoning adapter JAR provides `io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider` and depends on the public reasoning SPI plus the computation contracts it implements, not on `madre-app` or Kernel internals.

Providers receive a read-only view of current `reasoning.*` string settings and own provider-specific parsing/validation. Installation never implies enablement, Privacy is explicit rather than inferred, and an absent/empty installation or zero materialized mechanisms is valid at boot.

As with Module configuration, these strings remain developer-facing executable behavior rather than a completed generic configurator surface.

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

The example intentionally contains provider-specific disabled examples for development/reference. Native first-run bootstrap does not copy those provider values.

## Verification evidence

The existing Windows/Linux build workflow continues to exercise `check`, architecture guards, Javadocs/publication, developer packages, isolated SDK Module/reasoning builds, Module-to-Module interoperability, no-reasoning boot, owner-local versus external/PUBLIC semantics, Module configuration, CORE/interaction independence and durable restart/recovery.

A separate exact-head native-package workflow builds the platform-native package on each corresponding host, exercises the `jpackage` application image with the machine `java` removed from `PATH`, proves fresh zero-argument bootstrap/restart/`doctor`/clean shutdown, verifies shipped artifacts and zero enabled reasoning, then performs unattended native installer install/launch/uninstall on both hosts. The MSI/DEB is retained as a downloadable workflow artifact.

Historical PR #47 runs also exercised live llama.cpp/model inference over the native AF_UNIX adapter. Native packaging does not change those reasoning or security boundaries.
