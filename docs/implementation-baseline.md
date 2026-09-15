# Implementation Baseline

This document records executable truth on the active Java 21 branch. It is intentionally narrower than durable product intent in `MADRE.md` and the architecture documents.

## Owner product baseline

MADRE has a native installed-host foundation. JDK 21 `jpackage` produces Windows MSI and Linux DEB packages containing MADRE, shipped Module/reasoning artifacts, and a bundled Java runtime.

Fresh packaged startup resolves conventional per-user locations, creates owner-writable Module/reasoning directories, persists `madre.properties`, and reuses it on subsequent launches. Windows uses `%APPDATA%\MADRE` for configuration and `%LOCALAPPDATA%\MADRE` for mutable data/state. Linux follows the XDG config/data/state roots. Kernel durable work is stored under state as `kernel-work.sqlite`; Module semantic state is rooted separately under `<state>/module-state`.

Default packaged discovery scans read-only shipped artifact directories plus the conventional owner-writable `<data>/modules` and `<data>/reasoning` directories. Explicit `modules.directory` or `reasoning.directory` still selects exactly one directory for deterministic developer/test isolation. Those explicit discovery overrides are not owner-managed lifecycle roots.

Installed reasoning providers are discoverable/configurable before mechanism materialization through `madre reasoning ...`. Installed Module providers are likewise discoverable/configurable before Module materialization through `madre modules ...`. Both domains now also have local-file install/replace/uninstall commands routed before semantic application startup.

`madre doctor` continues to start the normal application and reports host/runtime/discovery state without dumping Module or reasoning field values. Artifact/configuration management instead uses provider-only discovery and does not require normal semantic startup.

## Stable SDK/developer baseline

`madre-sdk` remains the stable public Module foundation. It includes the canonical typed Material/Module/Agent/Skill/Workflow/Operation model, executable bindings/registration/provider contracts, caller-bound Module interoperability, Module-facing reasoning port, `MaterialCodecs.utf8String()`, `Operation.of(...)`, immutable `ModuleProviderConfiguration`, and the stable Module owner-configuration interoperability contract.

The stable Module-configuration public types remain:

```text
ModuleConfigurationFieldKind
ModuleConfigurationField
ModuleConfigurationDescriptor
```

and `ModuleProvider` exposes:

```java
ModuleId moduleId();
default ModuleConfigurationDescriptor configurationDescriptor();
default ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration);
ModuleInstance create(ModuleContext context,
        ModuleProviderConfiguration configuration);
```

`ModuleConfigurationFieldKind` has exactly `TEXT`, `INTEGER`, and `CHOICE`. This is evidence-driven vocabulary only. `ModuleConfigurationField` carries nominal owner-facing name/display/help/kind/required/default information, finite allowed values for `CHOICE`, and optional minimum/maximum bounds for `INTEGER`. Descriptor/field collections are immutable; duplicate field names are rejected.

`ModuleConfigurationDescriptor` owns the canonical `ModuleId`, display/help text, and immutable fields. The descriptor is installation/configuration metadata and is not part of `ModuleDefinition`, Material, Agent, Skill, Workflow, Operation, or Security Algebra semantics.

`ModuleProvider.validateConfiguration(...)` receives one complete immutable configuration candidate already scoped to that provider's exact canonical Module identity. The provider may reject it or return a canonicalized candidate for the same identity. A returned different Module identity is rejected by the host. Validation does not require `create(...)`; provider code remains the ultimate semantic validator.

`madre-sdk-testkit`, `madre-sdk-experimental`, the reasoning SPI and computation-contract artifacts are unchanged by local artifact lifecycle. No public universal artifact/plugin API was introduced. Filesystem/source-path information remains host-internal.

## Executable Module configuration baseline

The raw compatibility representation is unchanged:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Normal startup still discovers providers, scopes those strings into an immutable `ModuleProviderConfiguration`, then calls `ModuleProvider.create(...)`. Existing raw developer/advanced configuration therefore remains executable.

The host offers:

```text
madre modules list
madre modules inspect <module-id>
madre modules configure <module-id> [--set <field>=<value>]...
```

`modules list` uses canonical ServiceLoader discovery and lists installed providers without normal Module materialization. `modules inspect` shows provider-owned display/help/field metadata and current values for that exact identity. `modules configure` is deterministic with repeated `--set`; without `--set` it renders a simple interactive prompt from the same descriptor.

Module product-management commands are routed by `MadreLauncher` before delegation to the semantic application. They load host configuration and installed providers but do not construct `MadreApplication`, Kernel, Module contexts, or Module instances. They therefore provide a repair path when normal runtime materialization would fail.

A configure operation resolves exactly one canonical provider, edits only the exact `modules.config[<id>].` prefix, asks the provider to validate/canonicalize the complete candidate, rejects identity escape/undeclared returned fields, safely persists a copied properties set, and updates in-memory state only after persistence succeeds. Unrelated settings survive. Provider rejection happens before persistence.

There is no Module enable/disable state. Module artifact lifecycle, Module configuration, role/interaction configuration and Module semantic state remain separate responsibilities.

## Managed local artifact lifecycle

MADRE now exposes four owner lifecycle commands:

```text
madre modules install <jar> [--replace]
madre modules uninstall <module-id> [--purge-configuration]
madre reasoning install <jar> [--replace]
madre reasoning uninstall <provider-id> [--purge-configuration]
```

The existing `madre reasoning remove <provider>/<instance>` remains configured-instance removal. It was not overloaded into provider-artifact removal.

Lifecycle mutation always targets `HostEnvironment.ownerModuleDirectory()` or `HostEnvironment.ownerReasoningDirectory()`. Shipped application-image directories are read-only inputs to discovery/protection checks. An explicit `modules.directory` or `reasoning.directory` remains a discovery/development override and is never treated as a persistent owner-managed mutation root. If an identity exists only in such an external development directory, managed uninstall refuses and tells the operator to remove that developer artifact manually.

The filesystem remains the artifact registry. No SQLite/metadata registry was added. Loader discovery now retains host-internal provider-to-code-source information, allowing the host to classify a discovered provider as `shipped`, `owner`, or `development` and to identify the exact owner JAR to replace/delete. No filesystem path was added to the public SDK or reasoning SPI.

`modules list` and `reasoning providers` expose that concise source classification. Owner-managed providers also show the managed artifact filename. Successful install/replacement output includes a SHA-256 digest as a diagnostic/integrity identifier only; it has no signing/trust meaning.

### Managed 0.x packaging rule

Managed installation accepts one readable regular local `.jar` file. There is no URL fetch, Maven resolution, GitHub release lookup, marketplace, bundle/archive format, dependency resolver, update feed or automatic latest-version logic.

A managed Module candidate must expose exactly one `ModuleProvider` whose implementation class is loaded from the candidate JAR, and it must expose no `ReasoningMechanismProvider`. A managed reasoning candidate must expose exactly one `ReasoningMechanismProvider` from that JAR, and no `ModuleProvider`. No-provider, wrong-domain, cross-domain and multi-provider packages are rejected. Malformed ServiceLoader metadata fails discovery rather than being accepted partially.

Historical/direct directory discovery remains more permissive where the JVM parent classloader can satisfy unusual service metadata. That compatibility is intentionally not promoted into the managed installation contract.

### Candidate validation

Module candidate validation obtains the canonical `ModuleId`, requires a non-null configuration descriptor with the same identity, scopes any persisted `modules.config[<id>].*`, calls `ModuleProvider.validateConfiguration(...)`, requires the returned configuration to retain the same identity, and rejects undeclared returned keys. `ModuleProvider.create(...)` is not called. No Operation executes.

Reasoning candidate validation obtains a non-null `ReasoningProviderDescriptor`, stable provider identity and configurator, then asks the provider configurator to enumerate its configured instances against current persisted configuration. No `materialize(...)` call, model/server/endpoint availability check or mechanism enablement occurs.

Fresh reasoning installation therefore makes only the provider type discoverable. It creates/enables zero mechanisms unless already-persisted provider-owned configuration says otherwise.

### Collision and replacement rules

Canonical provider identity is authoritative. Filename, Java class name and discovery order never define replacement identity.

A candidate colliding with a shipped Module/reasoning identity is rejected. Shipped artifacts cannot be removed through owner lifecycle commands. An owner identity already present rejects normal install. `--replace` is required and succeeds only when the candidate's canonical identity exactly equals an already owner-installed identity.

The candidate is first copied to a temporary file inside the owner directory, so staging and the destination share a filesystem. Staged bytes are re-discovered/revalidated before mutation. All temporary discovery/provider classloaders are closed before the active JAR is replaced. Commit uses `ATOMIC_MOVE + REPLACE_EXISTING` when supported and a same-filesystem replacement fallback otherwise. This close-before-replace discipline is required for Windows file-lock correctness.

The prior active artifact is not mutated until staging and validation succeed. A validation/staging failure therefore leaves the previous artifact installed. The current implementation does not claim that non-atomic fallback can protect against every machine/process crash during the final filesystem replace; it provides the strongest ordinary same-filesystem fallback already used by host configuration persistence.

### Module uninstall/configuration safety

If any exact `modules.config[<id>].*` values exist, plain Module uninstall refuses. `--purge-configuration` removes only that exact prefix and preserves unrelated Module, reasoning, role, interaction and host properties.

If `interaction.module` references the exact Module identity, uninstall refuses with an actionable message rather than silently rewriting the binding. Current normal startup tolerates unresolved `roles.core`, so lifecycle does not invent a new CORE policy: `roles.core` is retained unchanged even when the referenced owner Module is uninstalled.

Module artifact uninstall never removes semantic Module state under `<state>/module-state`. There is no `--delete-data` behavior.

### Reasoning provider uninstall/configuration safety

If the provider reports configured instances, plain provider uninstall refuses. With `--purge-configuration`, the host opens the exact owner artifact, asks that provider's own configurator to remove each configured instance, applies the provider-produced updates, and verifies that the provider reports no remaining instances. The host does not guess a provider property namespace.

After the provider/classloader is closed, the configuration candidate is persisted safely and the artifact is deleted. Provider uninstall never deletes Kernel durable reasoning work.

### Rollback-oriented ordering

Configuration and artifact files are separate resources, so MADRE does not claim global atomicity. For purge-and-uninstall, validation/purge computation happens before mutation. The previous complete configuration is retained in memory. The new configuration is safely persisted, all loaders are already closed, and only then is the owner JAR deleted. If deletion fails, MADRE attempts to restore the previous complete configuration through `HostEnvironment.replaceConfiguration(...)` and reports the failure (including restore failure as a suppressed cause when necessary).

This is rollback-oriented fail-safe behavior, not a distributed transaction.

## Shipped owner-interaction adoption

`madre-module-owner-interaction` remains an ordinary shipped Module. Its provider exposes metadata for its existing installation settings; `OwnerInteractionSettings.fromInstallation(...)` remains the authoritative semantic parser/validator for normal creation and configuration validation. `madre-app` contains none of those setting names.

The Module's Operations, Agents, Skills, Workflows, pending-state persistence, reasoning behavior, public minimization, CORE role, and `interaction.*` behavior are unchanged by artifact lifecycle. Owner lifecycle commands confer no privilege on CORE and are not exposed through `ModuleContext`.

## Independent Module proof

`verification/sdk-consumer` remains an independently buildable Java project that resolves published public MADRE artifacts rather than depending on `madre-app` or Kernel internals. Its real `result-prefix` setting is declared through the stable Module configuration contract.

The cross-platform `SDK developer acceptance`/installed-owner journey now proves:

1. the independent project builds outside the MADRE checkout against published verification artifacts;
2. its JAR is initially absent from the isolated conventional owner Module directory;
3. packaged MADRE installs it with `modules install <external-jar>`;
4. `modules list` discovers `phd.module` and classifies it as `owner`;
5. `modules inspect` exposes `result-prefix`;
6. `modules configure` persists `result-prefix=configured-`;
7. normal owner/PUBLIC invocation uses the configured Module behavior;
8. a byte-different but semantically identical fixture JAR with the same provider identity replaces it through explicit `--replace`, preserving/validating configuration;
9. attempting to install/uninstall the shipped owner-interaction identity is rejected and the shipped JAR hash is unchanged;
10. plain uninstall refuses while `phd.module` configuration exists;
11. `--purge-configuration` removes only the exact independent Module configuration and owner artifact;
12. unrelated shipped-Module configuration and `roles.core` survive;
13. the Module semantic-state marker survives;
14. the provider is no longer discoverable.

The previous owner-local/PUBLIC, CORE/interaction independence, zero-reasoning and durable restart/recovery proofs remain in the same acceptance rather than being replaced by lifecycle-only checks.

## Independent reasoning-provider proof

`verification/reasoning-consumer` remains the existing independent public-SPI provider fixture. The reasoning owner-configuration acceptance now uses the packaged product command rather than copying its JAR into the owner directory.

On both Windows and Ubuntu it proves:

1. the owner reasoning directory starts without the independent JAR;
2. `reasoning install <jar>` installs `independent-text` into the conventional owner root;
3. `reasoning providers` classifies it as `owner`;
4. installation alone leaves configured-instance/mechanism counts at zero;
5. a byte-different same-identity JAR is accepted only with explicit `--replace`;
6. each shipped reasoning provider rejects owner uninstall and a shipped reasoning JAR cannot be installed as an owner override; shipped hashes are unchanged;
7. existing generic configure/inspect/enable/disable/remove commands keep their prior semantics;
8. provider-backed mechanism materialization/restart evidence remains intact;
9. plain provider uninstall refuses while a preserved configured instance remains;
10. `--purge-configuration` invokes provider-owned removal semantics, removes the owner JAR, and preserves unrelated Module configuration;
11. the provider becomes undiscoverable.

`reasoning remove <provider>/<instance>` and `reasoning uninstall <provider>` are therefore independently exercised as distinct product responsibilities.

## Discovery/startup invariants

Normal Module/reasoning startup invariants remain:

- duplicate canonical provider identities fail before semantic use;
- raw Module configuration for an uninstalled identity is rejected during normal Module startup;
- provider/materialized identity mismatch fails;
- invalid executable bindings fail;
- zero reasoning mechanisms remain valid;
- Module and reasoning installation remain distinct;
- owner-managed lifecycle does not weaken direct-placement discovery compatibility.

Configuration/lifecycle commands avoid normal semantic startup and use only provider discovery/validation appropriate to the command.

## Receiver, Kernel and security boundaries

Module-to-Module, owner-local, and external/PUBLIC receiver semantics are unchanged. Artifact administration is not Material flow and gains no fabricated Security Algebra values. Module code does not receive host-only lifecycle authority. CORE receives no lifecycle privilege.

Reasoning artifact lifecycle is outside Kernel. Kernel continues to see configured executable reasoning mechanisms and durable work only; it does not install provider JARs or own provider files.

## Verification coverage

Focused and cross-platform evidence now covers:

- ordinary ServiceLoader discovery compatibility plus strict managed one-JAR provider provenance;
- candidate-domain rejection through separate Module/reasoning managed loaders;
- canonical identity collision/replacement semantics;
- source classification (`shipped`, `owner`, `development`);
- staged same-filesystem replacement after classloader closure;
- Windows and Ubuntu replacement/deletion of discovered JARs;
- plain uninstall refusal when configuration remains;
- exact Module-prefix purge;
- reasoning provider-owned configuration purge;
- shipped artifact protection and unchanged shipped hashes;
- unrelated configuration survival;
- Module semantic-state retention;
- reasoning install creating no mechanism;
- existing invalid-configuration rollback, Module receiver boundaries, interoperability, zero-reasoning startup and durable recovery.

Cross-platform PR workflows remain the authoritative installed/package evidence: Java 21 Windows/Ubuntu build, SDK developer acceptance, native owner package, and reasoning owner configuration.

## Current intentional limitations

Local lifecycle is deliberately narrow. It does not implement remote URL install, Maven/GitHub resolution, marketplace/catalog search, dependency bundles, lockfiles, plugin descriptors, auto-update/version resolution, signatures/PKI, trust stores, malware scanning, sandboxing, permission manifests, credential management, Module enable/disable, semantic-state deletion, Kernel-work deletion, CORE/interaction redesign, public artifact repository publishing, or a universal artifact/plugin API.

The managed 0.x contract is a single independently built JAR with one canonical provider for exactly one installation domain. Richer dependency packaging should be introduced only if real independent projects demonstrate that the current public-artifact packaging is insufficient.

Direct JAR placement remains an advanced/developer compatibility path. MADRE intentionally will not delete arbitrary files outside its conventional owner-managed roots.
