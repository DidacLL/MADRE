# Implementation Baseline

This document records executable truth on the active Java 21 branch. It is intentionally narrower than durable product intent in `MADRE.md` and the architecture documents.

## Owner product baseline

MADRE has a native installed-host foundation. JDK 21 `jpackage` produces Windows MSI and Linux DEB packages containing MADRE, shipped Module/reasoning artifacts, and a bundled Java runtime.

Fresh packaged startup resolves conventional per-user locations, creates owner-writable Module/reasoning directories, persists `madre.properties`, and reuses it on subsequent launches. Windows uses `%APPDATA%\MADRE` for configuration and `%LOCALAPPDATA%\MADRE` for mutable data/state. Linux follows the XDG config/data/state roots. Kernel durable work is stored under state as `kernel-work.sqlite`; Module semantic state is rooted separately under `<state>/module-state`.

Default packaged discovery scans read-only shipped artifact directories plus the conventional owner-writable `<data>/modules` and `<data>/reasoning` directories. Explicit `modules.directory` or `reasoning.directory` still selects exactly one directory for deterministic developer/test isolation.

Installed reasoning providers are discoverable/configurable before mechanism materialization through `madre reasoning ...`. Installed Module providers are now likewise discoverable/configurable before Module materialization through `madre modules ...`.

`madre doctor` continues to start the normal application and reports host/runtime/discovery state without dumping Module or reasoning field values. Module configuration management instead uses provider-only discovery and does not require normal semantic startup.

## Stable SDK/developer baseline

`madre-sdk` remains the stable public Module foundation. It includes the canonical typed Material/Module/Agent/Skill/Workflow/Operation model, executable bindings/registration/provider contracts, caller-bound Module interoperability, Module-facing reasoning port, `MaterialCodecs.utf8String()`, `Operation.of(...)`, immutable `ModuleProviderConfiguration`, and the stable Module owner-configuration interoperability contract.

The new stable Module-configuration public types are:

```text
ModuleConfigurationFieldKind
ModuleConfigurationField
ModuleConfigurationDescriptor
```

and `ModuleProvider` now has source-compatible default methods:

```java
default ModuleConfigurationDescriptor configurationDescriptor();
default ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration);
```

The existing methods remain:

```java
ModuleId moduleId();
ModuleInstance create(ModuleContext context,
        ModuleProviderConfiguration configuration);
```

`ModuleConfigurationFieldKind` has exactly `TEXT`, `INTEGER`, and `CHOICE`. This is evidence-driven vocabulary only. `ModuleConfigurationField` carries nominal owner-facing name/display/help/kind/required/default information, finite allowed values for `CHOICE`, and optional minimum/maximum bounds for `INTEGER`. Descriptor/field collections are immutable; duplicate field names are rejected. No secret, floating-point, list, nested-object, arbitrary-JSON, dynamic-schema, visibility-condition, or generic validation-language feature exists.

`ModuleConfigurationDescriptor` owns the canonical `ModuleId`, display/help text, and immutable fields. The descriptor is installation metadata and is not part of `ModuleDefinition`, Material, Agent, Skill, Workflow, Operation, or Security Algebra semantics.

`ModuleProvider.validateConfiguration(...)` receives one complete immutable configuration candidate already scoped to that provider's exact canonical Module identity. The provider may reject it or return a canonicalized candidate for the same identity. A returned different Module identity is rejected by the host. Validation does not require `create(...)`; provider code remains the ultimate semantic validator.

Existing providers remain source-compatible through default empty metadata and identity validation. `madre-sdk-experimental` is unchanged; Module configuration is a stable installation/interoperability contract, not a semantic-experimentation abstraction.

`madre-sdk-testkit` remains a public deterministic semantic test artifact with no `madre-app` or Kernel implementation dependency. `madre-bom` still aligns public artifacts. No new dependency was added by the Module-configuration slice.

## Executable Module configuration baseline

The raw compatibility representation is unchanged:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Normal startup still discovers providers, scopes those strings into an immutable `ModuleProviderConfiguration`, then calls `ModuleProvider.create(...)`. Existing raw developer/advanced configuration therefore remains executable.

The host now offers:

```text
madre modules list
madre modules inspect <module-id>
madre modules configure <module-id> [--set <field>=<value>]...
```

`modules list` uses the canonical `InstalledModuleLoader` ServiceLoader path and lists installed providers without normal Module materialization. `modules inspect` shows provider-owned display/help/field metadata and current values for that exact identity. `modules configure` is deterministic with repeated `--set`; without `--set` it renders a simple interactive prompt from the same descriptor.

Module product-management commands are routed by `MadreLauncher` before delegation to the existing `MadreMain`. They load host configuration and installed providers but do not construct `MadreApplication`, Kernel, Module contexts, or Module instances. They therefore provide a recovery path when normal runtime materialization would fail because Module configuration is absent or invalid.

A configure operation:

1. resolves exactly one installed canonical Module provider;
2. reads the current values under only `modules.config[<that ModuleId>].`;
3. applies newly supplied declared field edits with generic field-shape validation;
4. builds a complete `ModuleProviderConfiguration` candidate;
5. asks that provider to validate/canonicalize the complete candidate without materialization;
6. rejects provider identity escape or undeclared returned keys;
7. copies the complete host properties, replaces only the exact target Module prefix, and calls `HostEnvironment.replaceConfiguration(...)`;
8. updates the in-memory `Properties` only after successful persistence.

Untouched legacy raw values are not reinterpreted by host metadata. They remain provider-interpreted so existing raw startup behavior and provider compatibility are preserved. The provider receives the complete candidate and remains authoritative over semantic parsing/validation.

`HostEnvironment.replaceConfiguration(...)` writes through a temporary file in the same directory, uses atomic replacement where supported, and safely falls back to replacement otherwise. Provider rejection occurs before persistence. Persistence failure cannot partially update the manager's in-memory configuration because mutation happens only after replacement succeeds. Unrelated host, reasoning, role, interaction, directory, and other Module settings are preserved.

A Module configuration edit cannot mutate another Module identity or any non-Module property because the host constructs persisted names from the selected canonical identity and only removes/replaces that exact prefix. Configuration for an uninstalled Module is rejected.

There is no Module enable/disable state. There is no Module configuration `remove` command in this slice. Installed-artifact lifecycle and configuration are intentionally separate responsibilities.

## Shipped owner-interaction adoption

`madre-module-owner-interaction` remains an ordinary installed Module. Its provider now exposes metadata for exactly its existing ten installation settings:

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

The metadata describes the existing integer/default/bound constraints and `LOCAL`/`REMOTE` choices; integer millisecond values remain integer milliseconds. No duration field kind was invented.

`OwnerInteractionSettings.fromInstallation(...)` remains the authoritative semantic parser/validator both for normal `create(...)` and configuration-edit validation. Its existing defaults, positive/nonnegative constraints, duration interpretation, case-insensitive raw location parsing, `ReasoningPreferences`, retry construction, and Module behavior remain unchanged. `madre-app` contains none of these setting names.

The Module's Operations, Agents, Skills, Workflows, `standard-prompt`, `fast-lane`, `collect-background`, pending-state persistence, reasoning behavior, public minimization, CORE role, and `interaction.*` behavior are unchanged by this slice.

## Independent Module proof

`verification/sdk-consumer` remains an independently buildable Java project that resolves published public MADRE artifacts rather than depending on `madre-app` or Kernel internals. Its real setting `result-prefix` is now declared through the stable Module configuration contract; no synthetic settings were added.

`IndependentModuleProvider` centralizes `result-prefix` parsing/canonicalization. The setting is optional, but when supplied it is stripped, must be nonblank, and must match the existing `[A-Za-z0-9._-]+` validation. Both `validateConfiguration(...)` and runtime `create(...)` use that path.

The cross-platform `SDK developer acceptance` workflow now proves, from a directory outside the MADRE checkout:

1. verification artifacts are published and the packaged host image is built;
2. the independent project builds its installable JAR;
3. only that JAR is copied to the ordinary owner-writable Module directory;
4. packaged `madre modules list` discovers `phd.module` without Module materialization;
5. `madre modules inspect phd.module` exposes the real `result-prefix` field;
6. `madre modules configure phd.module --set result-prefix=configured-` succeeds;
7. the owner configuration contains the exact raw `modules.config[phd.module].result-prefix` property;
8. an invalid prefix is rejected and the persisted configuration remains byte-for-byte unchanged;
9. a subsequent normal `doctor` startup materializes/discovers the configured Module;
10. owner-local invocation returns the configured sensitive semantic result `private:configured-hello`;
11. external/PUBLIC invocation returns the configured minimized result `public:configured-hello` without leaking the private result.

No new acceptance workflow was created; the existing SDK developer acceptance was extended because it already owns the independent external Module journey.

## Reasoning configuration remains a separate domain

The existing reasoning-provider configuration implementation remains unchanged. It has separate reasoning-specific public descriptors/configurator/update types because reasoning providers own repeatable named mechanism instances, enable/disable state, and provider-specific raw mapping. Those concepts were not imported into Module APIs.

Conversely, no cross-domain `SettingsSchema`, `ConfigurableProvider`, generic property tree, settings service, annotation/reflection configuration model, or common descriptor hierarchy was extracted. Some structural duplication (`TEXT`, `INTEGER`, `CHOICE`) is deliberate to preserve lifecycle/ownership boundaries.

Inference contracts, reasoning selection, provider-specific inference configuration, Kernel reasoning, llama.cpp/OpenAI-compatible adapters, and `madre-sdk-experimental` are unchanged.

## Discovery/startup invariants

Normal Module startup invariants remain:

- duplicate canonical Module provider identities fail before materialization;
- raw configuration for an uninstalled identity is rejected;
- provider/materialized identity mismatch fails;
- invalid executable bindings fail;
- zero reasoning mechanisms remain valid;
- Module and reasoning installation remain distinct.

Configuration-management commands do not weaken these normal startup checks. They avoid normal startup instead, using the same ServiceLoader provider discovery path only.

## Receiver and runtime boundaries

Module-to-Module, owner-local, and external/PUBLIC receiver semantics are unchanged. Module code still does not receive host-only owner/public invocation authority. CORE assignment grants no configuration privilege. Module settings are installation data, not Material, and are not assigned Security Algebra dimensions merely to fit the semantic model.

`MadreMain` remains the transitional local console/semantic application adapter. `MadreLauncher` is only a host-product command router so configuration repair can occur without semantic startup. It does not become a Module API or Kernel contract.

## Verification coverage

Focused tests now prove:

- Module descriptor field lists are immutable;
- duplicate field names fail;
- choice allowed values and integer bounds are enforced by the generic field metadata;
- provider-owned validation can reject a candidate;
- a provider cannot return a configuration for another Module identity;
- rejected validation leaves persisted and in-memory configuration unchanged;
- unrelated host/reasoning/other-Module properties survive successful persistence;
- configuration management never calls the target provider's `create(...)`;
- uninstalled Module identities cannot be configured.

Existing tests continue to prove exact raw Module scoping, prefix-collision safety, duplicate-provider rejection, uninstalled raw configuration rejection, provider/materialized identity matching, binding validation, owner-interaction semantics, Module-to-Module interoperability, owner-local versus external/PUBLIC behavior, and durable recovery.

Cross-platform PR workflows remain the authoritative installed/package evidence: Java 21 Windows/Ubuntu build, SDK developer acceptance, native owner package, and reasoning owner configuration. Existing acceptance inside the Java build continues to exercise Module interoperability, independent reasoning, owner-local/PUBLIC boundaries, and durable restart/recovery regressions.

## Current intentional exclusions

This slice does not implement Module JAR download/install/update/uninstall, marketplace discovery, credential storage, graphical settings, CORE redesign, memory/RAG/planning abstractions, multimodal/voice, MCP, inference evolution, or another SDK convenience layer.

Artifact lifecycle should be reassessed separately from the remaining real owner/developer friction after generic Module configuration is in use.
