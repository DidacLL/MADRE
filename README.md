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

Shipped Module and reasoning-adapter JARs remain read-only program artifacts inside the application image. Normal packaged discovery scans those shipped directories plus the owner-writable Module/reasoning directories above. An explicit `modules.directory` or `reasoning.directory` still means exactly one explicitly selected directory, preserving deterministic developer/test isolation. Those explicit discovery overrides are not owner-managed installation roots: lifecycle commands always mutate the conventional owner-writable roots and never shipped package files or an arbitrary developer directory.

The first-run bootstrap deliberately contains no configured or enabled reasoning mechanism and does not invent an endpoint, model, credential, Privacy value or provider-specific setting. Installed provider types with zero configured instances are a valid MADRE state. The bootstrap currently preserves the shipped `roles.core` and `interaction.*` installation policy needed for the existing console behavior, without changing CORE privilege or semantics.

## Owner local artifact lifecycle

The packaged product installs owner-selected local JARs directly. Module and reasoning-provider artifacts remain separate installation domains:

```text
madre modules install <jar>
madre modules install <jar> --replace
madre modules uninstall <module-id> [--purge-configuration]

madre reasoning install <jar>
madre reasoning install <jar> --replace
madre reasoning uninstall <provider-id> [--purge-configuration]
```

`install` accepts a local filesystem JAR only. There is no URL download, Maven/GitHub resolution, marketplace, update feed or automatic upgrade. The current managed 0.x packaging contract is one ordinary JAR exposing exactly one canonical provider entrypoint for its installation domain. A managed Module JAR exposes one `ModuleProvider`; a managed reasoning JAR exposes one `ReasoningMechanismProvider`. A candidate with no provider, multiple providers, the wrong provider domain or both installation domains is rejected. Direct JAR placement remains supported as an advanced/developer discovery compatibility path, including in the conventional owner discovery directories, but directory membership alone does not grant lifecycle ownership. A manually placed JAR is reported as `manual` and managed replace/uninstall refuses it. MADRE-installed artifacts alone occupy the deterministic managed filename reserved for their installation domain and canonical identity.

Installation validates the provider before committing bytes. Module installation obtains the canonical `ModuleId`, validates its configuration descriptor and runs `ModuleProvider.validateConfiguration(...)` against any already-persisted identity-scoped configuration without calling `create(...)`. Reasoning installation obtains the stable provider descriptor/configurator and validates existing provider-owned configuration without materializing a mechanism or probing an endpoint/model/server. Installation therefore does not execute Operations, configure reasoning instances, enable mechanisms, assign CORE or create `interaction.*` bindings.

Canonical identities, not source filenames or class names, control collision and replacement. A shipped identity cannot be overridden. An existing lifecycle-managed owner identity fails by default; `--replace` explicitly replaces only the same canonical identity in its exact managed slot. A same-identity manual JAR also blocks managed installation but is never adopted, overwritten or deleted; remove that manual file explicitly first if intended. MADRE stages a managed replacement candidate in the owner directory, revalidates the staged bytes, closes temporary discovery classloaders, and then uses a same-filesystem atomic move where supported with the same safe replacement fallback used by configuration persistence. This close-before-mutation discipline is required for Windows JAR locks.

`madre modules list` and `madre reasoning providers` classify providers as `shipped`, lifecycle-managed `owner`, manually placed `manual` or external-override `development`; lifecycle-managed owner entries also show their managed filename. Install/replacement output includes a SHA-256 digest as a diagnostic/integrity identifier only. It is not a signature or trust decision. The host reconstructs provider-to-source-JAR ownership from ServiceLoader discovery and the provider class code source; there is no artifact registry database.

Uninstall is deliberately conservative. A Module with retained `modules.config[<id>].*` refuses plain uninstall; `--purge-configuration` removes only that exact Module prefix. A Module required by the current `interaction.module` binding also refuses uninstall until the binding is changed. The current unresolved-CORE behavior is preserved: `roles.core` is not silently rewritten or purged. Module semantic state under `<state>/module-state` is never deleted by artifact uninstall.

A reasoning provider with configured instances likewise refuses plain provider uninstall. `--purge-configuration` asks that provider's own configurator to remove each configured instance, rather than guessing provider raw-property namespaces. The existing `madre reasoning remove <provider>/<instance>` remains exactly one configured-instance removal and is not an artifact operation. Provider uninstall does not delete Kernel durable reasoning work.

Configuration and artifact files are different physical resources, so MADRE does not claim global atomicity. Purge-and-uninstall validates before mutation, persists the configuration candidate safely, closes provider/classloader resources before deleting the JAR, and restores the previous configuration if artifact deletion then fails. Replacement leaves the old artifact untouched until the new candidate has been staged and fully validated. Manual-artifact refusal happens before configuration purge, so `--purge-configuration` cannot turn a manual discovery artifact into a destructive lifecycle operation.

MADRE's existing trust model is unchanged: locally validating a JAR does not sandbox or make untrusted code safe. The Owner is explicitly choosing software to install.

## Owner reasoning configuration

Reasoning artifact installation and reasoning instance configuration are separate facts. Installing an adapter makes its provider discoverable but never configures or enables a mechanism.

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

Artifact lifecycle remains separate from configuration lifecycle. `reasoning install/uninstall` manages owner JARs; `reasoning configure/enable/disable/remove` manages provider-owned instances. There is still no credential vault, OAuth/account flow, graphical settings UI or remote provider catalog.

## Owner Module configuration

Module artifact installation and Module configuration are separate facts. `madre modules install <jar>` makes an owner-selected provider discoverable; configuration does not enable/disable or install/uninstall the artifact.

Discover installed Module providers without materializing Modules:

```text
madre modules list
```

Inspect one provider-owned configuration contract and its currently configured values:

```text
madre modules inspect io.github.didacll.madre.owner-interaction
madre modules inspect phd.module
```

Configure deterministically with repeated field assignments:

```text
madre modules configure io.github.didacll.madre.owner-interaction \
  --set foreground-maximum-tokens=256 \
  --set foreground-location=LOCAL

madre modules configure phd.module --set result-prefix=configured-
```

Calling `madre modules configure <module-id>` without `--set` enters the same simple descriptor-driven prompt style used by the reasoning configurator. The host knows only the stable Module configuration field kinds currently demonstrated by real Modules: `TEXT`, `INTEGER` and `CHOICE`, including integer bounds/defaults and finite choices where declared. It contains no owner-interaction-specific or verification-fixture-specific key branches.

The provider owns its display/help text, field vocabulary, parsing, defaults, semantic validation and optional canonicalization through the stable `madre-sdk` `ModuleProvider` configuration contract. Configuration discovery and validation do not call `ModuleProvider.create(...)` and do not start the semantic application, so the commands can be used to repair Module settings even when normal Module materialization would fail.

Successful writes preserve the raw compatibility representation:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The host replaces only the exact canonical Module prefix, preserves unrelated host/reasoning/other-Module settings, writes through the same safe same-directory replacement mechanism as reasoning configuration and updates in-memory configuration only after persistence succeeds. A rejected edit leaves the previous file unchanged. A subsequent normal startup receives the persisted values through the existing `ModuleProviderConfiguration` route.

There is intentionally no Module enable/disable command. Module artifact uninstall and configuration purge remain separate responsibilities, and uninstall never deletes Module semantic state.

## Diagnostics

Run host diagnostics with:

```text
madre doctor
```

`doctor` reports the MADRE version, bundled Java runtime, resolved configuration/data/state locations, Module and reasoning artifact locations, configuration/discovery health, installed Module identities, CORE resolution, installed reasoning provider identities, configured provider-instance identities/state and successfully materialized reasoning-mechanism identities/count. It does not dump Module/reasoning field values or raw provider configuration.

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

For deterministic source/developer execution, copy/review `config/madre.properties.example` and pass it explicitly. Its relative paths are developer conveniences; native first-run startup does not copy this template and instead persists conventional absolute host locations. Explicit `modules.directory`/`reasoning.directory` remain useful for direct-placement development compatibility, but they do not redirect the owner-managed lifecycle roots.

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

The current verification publication remains repository-local at `build/isolated-repository`. It is not yet a public remote 0.x release channel. The manually invoked cross-platform `Extended SDK developer acceptance` workflow publishes those artifacts, copies `verification/sdk-consumer` to a runner-temporary directory outside the checkout, runs its deterministic tests and JAR build there, passes only the resulting external JAR path to packaged MADRE, installs it with `madre modules install`, discovers its provider through the generic `madre modules` path, inspects/configures its real `result-prefix`, proves explicit staged replacement, rejected plain uninstall with retained configuration, configuration-purge uninstall, semantic-state retention, shipped-artifact protection, restart, configured owner-local plus external/PUBLIC receiver behavior, failed-replacement byte preservation, and non-destructive manual-placement compatibility for Module and reasoning artifacts.

The independent fixture uses the experimental builder only from test scope. The built Module JAR therefore proves that experimental authoring can aid experimentation without becoming a production runtime requirement. Its tests also resolve the newer computation-contract artifacts through the BOM without turning them into runtime dependencies of the installable fixture Module.

A MADRE Gradle Module plugin was deliberately not added in this slice. After version alignment, the demonstrated build-specific requirements are Java 21 and standard Java ServiceLoader metadata. A plugin remains a future tooling increment if repeated external projects demonstrate enough additional packaging/validation friction to justify another public build API.

See [docs/sdk-development.md](docs/sdk-development.md) for the runnable independent-project setup, Module anatomy, owner-configuration contract, reasoning/testing patterns, composition and installation guidance.

## Module installation and configuration

A MADRE Module is a complete executable application/domain boundary. Its JAR provides `io.github.didacll.madre.sdk.registration.ModuleProvider` through Java ServiceLoader metadata. The provider declares canonical identity and can expose owner-facing configuration metadata before materializing exactly that Module:

```java
ModuleId moduleId();
ModuleConfigurationDescriptor configurationDescriptor();
ModuleProviderConfiguration validateConfiguration(ModuleProviderConfiguration configuration);
ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration);
```

`ModuleInstance` is one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Shipped and owner-supplied Modules use the same discovery/registration path. Bundling, class-loader placement and CORE assignment confer no privilege.

A Module should exist because it owns coherent semantic/application behavior. Reusable technical facilities—search clients, embedding computations, storage libraries, transports, databases or model APIs—may be used by Modules without becoming Modules themselves.

The stable configuration descriptor is Module-specific, not a shared settings type system with reasoning providers. `ModuleConfigurationDescriptor` owns the canonical Module identity, display/help text and immutable fields. `ModuleConfigurationField` supports only `TEXT`, `INTEGER` and `CHOICE`, plus required/default information, finite allowed values for choices and optional integer bounds. `ModuleProvider.validateConfiguration(...)` receives one complete identity-scoped candidate and may reject or canonicalize it without materializing the Module.

For owner-managed installation, the provider is also the artifact identity proof. The current managed contract accepts a single JAR with exactly one `ModuleProvider` whose provider class is actually supplied by that JAR. Direct developer placement remains discoverable even where historical classloader packaging is less strict; the managed command deliberately does not broaden that legacy compatibility into the owner lifecycle contract.

The raw representation remains valid:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Normal startup still passes these strings through `ModuleProviderConfiguration` to `create(...)`. An explicit setting for an uninstalled identity is rejected; duplicate providers, descriptor/provider identity mismatch, provider/materialized identity mismatch and invalid executable bindings fail before partial installation becomes reachable.

The reasoning-provider metadata/configuration contract remains intentionally reasoning-specific. Reasoning providers have repeatable named mechanism instances and enable/disable state; Modules have one canonical installed identity. No cross-domain `SettingsSchema`, `ConfigurableProvider`, universal `Artifact` API or generic property-tree service was introduced.

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

The present console remains a transitional application adapter. `interaction.*` selects the installed Module/Operations/Material types used by ordinary text, `/standard`, `/updates` and explicit `/sensitivity`. The shipped owner-interaction Module owns semantic immediate/durable reasoning behavior and delayed-result interpretation; `MadreMain` still owns presentation and the command loop while `MadreLauncher` routes host-management commands that deliberately run before semantic application startup.

When no reasoning mechanism is materialized, normal startup remains valid and points the Owner toward `madre reasoning providers`/`configure`. It does not force a startup wizard.

`roles.core` is optional and non-privileged. When resolved, it identifies the ordinary installed Module intended to provide the default owner-interaction/coordinator role. It changes no Security Algebra value, Operation visibility, invocation authority, reasoning selection, scheduling, class-loader treatment or installation authority.

The current interaction model remains unfinished, but it is treated as a major SDK/inference experimentation consumer rather than the next universal API to freeze. CORE should eventually lead ordinary owner interaction and natural delayed semantic follow-up without host-only privilege; the stable structural contract should be recovered from repeated successful experiments rather than standardized directly from the current console/Operation names.

## Reasoning-adapter public installation contract

Reasoning mechanisms are independently installable from Modules. A reasoning adapter JAR provides `io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider` and depends on the public reasoning SPI plus the computation contracts it implements, not on `madre-app` or Kernel internals.

Each installed provider declares a stable `ReasoningProviderId`, a provider-owned descriptor, a configurator for named mechanism instances and `materialize(...)` for enabled `ReasoningMechanism` values. Providers own parsing, validation and raw persistence mapping. `madre-app` applies provider-produced updates generically and contains no concrete llama.cpp/OpenAI-compatible configuration-key branches.

The managed owner installer currently accepts one reasoning JAR containing exactly one reasoning-provider entrypoint and no Module provider. Installation only proves local package validity/configuration compatibility; it never materializes a mechanism. The existing raw `reasoning.*` representation remains executable for compatibility and advanced developer use; owners of the native product no longer need to know it for normal reasoning setup.

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

The example intentionally retains provider-specific disabled raw examples as compatibility/reference material. Native first-run bootstrap does not copy those provider values; the generic reasoning and Module commands are the owner-facing setup paths.

## Verification evidence

Automatic pull-request validation is intentionally lightweight: one Ubuntu job runs root `./gradlew --no-daemon --build-cache check`, with documentation-only changes skipped and newer pushes cancelling stale runs. It protects compilation, unit/contract tests and architecture guards without making hosted CI the development scheduler.

Expensive cross-platform product evidence remains available as explicit `workflow_dispatch` acceptance. `Extended SDK developer acceptance` builds the independent Module outside the checkout, installs it through the product lifecycle, exercises owner-local/external-PUBLIC behavior, replacement/uninstall semantics, failed-replacement preservation, and manual-placement non-destruction for Module and reasoning artifacts on Windows and Linux. `Extended reasoning owner configuration` exercises third-party reasoning install/configure/restart/disable/enable/remove/replacement/uninstall behavior. `Extended native owner package` builds and exercises the jpackage application/native owner journey on both supported hosts.

These extended workflows are run deliberately when the corresponding boundary changes, when platform-specific evidence is needed, or for release/Owner acceptance checkpoints. A queued or running hosted workflow is not itself a reason to stop development; genuine failures remain engineering evidence that must be understood and fixed.

Historical PR #47 runs also exercised live llama.cpp/model inference over the native AF_UNIX adapter. The owner configurator does not change reasoning selection, execution or Security Algebra boundaries.

## Remaining product gaps

The current work materially improves the public experimentation/developer environment, but MADRE is not yet a community-ready public SDK 0.x release.

The largest SDK release gaps are a real external artifact repository and release/version/signing mechanics, release-quality API compatibility policy, additional unrelated external-project feedback, and build/project-generation tooling if that feedback demonstrates enough remaining friction. The current verification repository and in-repository source fixture are acceptance infrastructure, not a distribution channel.

Remote artifact acquisition/catalogs, marketplace discovery, dependency/bundle packaging beyond the demonstrated single-JAR contract, automatic update policy, credential management, graphical settings, and the CORE-led owner-interaction evolution remain separate unfinished product work. Multimodal computation, semantic-memory/RAG/planning frameworks, generic tool calling, audio/voice and MCP are also intentionally not implied by the current foundation. They should be pursued only when real semantic or inference experiments establish a concrete need and correct responsibility boundary.
