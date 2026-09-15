# Implementation Baseline

This document records what actually executes on the active Java branch. It is intentionally narrower than the target product responsibilities in `MADRE.md` and `docs/architecture/`.

The active implementation is one Java 21 Gradle multi-project system. Windows and Linux run the same application, Kernel, SDK, persistence model, Module installation/configuration mechanism and reasoning-mechanism installation/configuration mechanism.

## Current owner-product transition

MADRE has a native installed-host foundation plus a generic owner-facing reasoning-provider configuration path.

- JDK 21 `jpackage` produces a Windows MSI and Linux DEB from the same `madre-app`, with a bundled Java runtime.
- A native packaged launch accepts zero arguments. First normal launch resolves conventional per-user host locations, creates them as needed and persists `madre.properties`; later launches reuse that file.
- Windows uses `%APPDATA%\MADRE` for configuration and `%LOCALAPPDATA%\MADRE` for mutable data/state. Linux follows `XDG_CONFIG_HOME`, `XDG_DATA_HOME` and `XDG_STATE_HOME`, with conventional home-directory fallbacks.
- Kernel durable data lives under the state root as `kernel-work.sqlite`; Module-owned state is rooted at `<state>/module-state`.
- Program/shipped artifacts remain inside the read-only application image. Owner-writable independent artifact roots are `<data>/modules` and `<data>/reasoning`.
- Packaged default discovery scans both shipped and owner-writable roots. Explicit `modules.directory` and `reasoning.directory` retain exact single-directory override semantics for deterministic development/tests.
- The first-run bootstrap copies only current host/application installation policy needed to preserve shipped interaction behavior, then writes host-resolved database/state paths. It contains no configured or enabled reasoning instance and invents no provider endpoint, model, credential, Privacy value or provider setting.
- Installed reasoning providers are discoverable and configurable before any mechanism is materialized through the generic `madre reasoning ...` host commands.
- `madre doctor` distinguishes installed provider types, configured provider instances and materialized reasoning mechanisms without printing provider field values or raw provider properties.
- `installDist`/`distZip` remain developer packaging. They intentionally retain explicit properties-file startup for existing isolated source/CI workflows.

This does not finish the whole owner product. Generic Module configuration metadata/UI, Module or reasoning-JAR install/remove/update management, credential management and the dedicated CORE-led owner interaction remain subsequent product work.

## Current semantic/runtime transition

Other transitional behavior remains unchanged:

- `MadreMain` owns the current local console loop and commands. `interaction.*` independently selects the Module used for ordinary console text; `roles.core` does not drive the binding.
- The shipped owner-interaction Module owns immediate reasoning, durable background reasoning, Module-owned pending semantic state, interpretation, acknowledgement and optional visible follow-up.
- `/updates` remains the explicit foreground mechanism that invokes the Module-specific collection Operation; natural delayed semantic re-entry is not implemented.
- Module settings remain immutable provider-owned strings supplied through `ModuleProviderConfiguration`; there is still no generic owner Module configurator metadata contract.
- Reasoning providers now have a small provider-owned typed configuration contract used by the real owner configurator: stable provider identity, display/help metadata, `TEXT`/`INTEGER`/`CHOICE` fields, repeatable configured-instance summaries and provider-produced configuration updates.
- `roles.core` still resolves an optional ordinary Module identity and creates no privilege or structural qualification.
- Independent SDK/reasoning fixtures prove strong public-contract isolation but not a complete community developer product.

## Artifact boundaries

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, immutable Module-provider configuration, codecs, caller-bound Module interoperability, host-only owner-local/external-PUBLIC invocation ports and the Module-facing reasoning port;
- `madre-reasoning-spi`: public typed reasoning-adapter execution and installation SPI, including stable reasoning-provider identity, owner-facing provider descriptors, the narrow provider configurator contract, provider-owned configuration updates and no Kernel implementation dependency;
- `madre-kernel`: live executable Module registry and receiver mechanics, reasoning-capability registry/selection, resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: typed nominal text-inference computation/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: independently discoverable/configurable reasoning-adapter artifacts;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: application assembly, host location/bootstrap/diagnostic mechanics, generic multi-root packaged Module/reasoning discovery, generic reasoning-provider configuration/persistence, native packaging and the current replaceable console.

`madre-app` has no concrete owner-interaction or reasoning-provider implementation dependency, no Module-specific configuration parser/table and no shipped-provider-specific reasoning configuration branch. Architecture checks reject concrete llama.cpp/OpenAI-compatible implementation imports or implementation dependencies from application code. Native packaging copies shipped artifacts as installation data; it does not compile those implementations into application semantics.

The removed generic Kernel `Capability<C,R>` SPI, generic `ExecutionService`/`WorkRequest`, SearXNG Kernel capability and standalone shipped WebSearch Module remain removed.

## Security Algebra baseline

The exact ordinary model is unchanged:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity by minimum; information reaches a receiver iff `Sensitivity <= Privacy`. For one EffectProfile, `min(Risk, Autonomy)` must be supported by actual non-user causal Integrity, or I5 when none exists. Risk is not propagated into reasoning work. `Privacy.MODULE` remains the fixed installed-Module receiver boundary. Native package origin, filesystem location, reasoning provider identity and CORE designation confer no algebraic privilege.

Privacy for one installed reasoning mechanism remains explicit provider-owned configuration. It is never inferred from endpoint, locality, transport, provider name or `ReasoningLocation`.

## Executable Module/configuration baseline

A running Module is one canonical `ModuleDefinition` plus exact `OperationBinding` values in a `ModuleInstance`. `ModuleProvider` declares its canonical `ModuleId` before materialization and receives identity-scoped immutable `ModuleProviderConfiguration` plus a caller-bound `ModuleContext`.

Current Module settings remain:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Each provider owns key vocabulary, validation, parsing and defaults. `madre-app` only scopes/delivers strings. Configuration for an uninstalled identity, duplicate providers, materialized identity mismatch or invalid executable bindings fail before partial reachability. Startup failure reporting preserves the provider-owned validation detail beneath the generic Module materialization context.

In the native package, default Module discovery loads JARs from the shipped application `modules` directory and the conventional owner data `modules` directory. Explicit `modules.directory` replaces that default pair with exactly the configured directory, preserving the previous developer contract.

No `ModuleProvider` configuration metadata was added by the reasoning configurator slice.

## Receiver boundaries

Module-to-Module, owner-local and external/PUBLIC behavior is unchanged.

`ModuleContext` contains caller-bound `ModuleDirectory` and `ModuleInvoker`; Module code supplies neither caller identity nor receiver Privacy. The runtime only exposes compatible target `PUBLIC` Operations. A foreign result is delivered only when the caller canonically references its Material type and the Sensitivity reaches fixed `Privacy.MODULE`; the exact callee Material is preserved and `PublicResultTransformer` is not run.

`OwnerModuleInvoker.invokeOwner` executes a canonical installed `PUBLIC` Operation and returns validated Module-created Material unchanged.

`PublicModuleInvoker.invokePublic` executes the same bounded Operation but requires Module-owned semantic public transformation into new declared Material able to reach `Privacy.PUBLIC`.

Both host ports remain absent from `ModuleContext`; packaging, shipped origin and CORE assignment grant no additional authority.

## Local interaction and CORE baseline

The application-local immutable `LocalInteractionBinding` remains optional and is configured through current `interaction.*` strings. `MadreMain` still owns the foreground loop. Ordinary text/default and `/standard` use owner-local invocation; `/updates` invokes only the configured Module collection Operation; `/sensitivity` modifies explicit console input classification.

If provider artifacts are installed but zero reasoning mechanisms materialize, normal console startup remains valid and emits only a setup hint pointing to `madre reasoning providers` and `madre reasoning configure ...`; startup is not a mandatory wizard.

`roles.core` remains optional and non-privileged. If the configured identity is installed, the live registry resolves it; absent/unresolved CORE does not prevent boot. Current interaction binding remains independent of CORE assignment. The native bootstrap packages the current shipped role/interaction installation policy only to preserve existing owner-visible behavior; it does not make those names a universal CORE API and does not add a privileged type/port.

## Shipped owner-interaction Module baseline

The shipped owner-interaction Module remains an ordinary installed Module using the same provider/configuration/discovery path as independent Modules. Its existing bounded Operations continue to prove immediate reasoning, durable background reasoning with Module-owned pending state, and Module interpretation/acknowledgement/cleanup after restart. Owner-local sensitive results remain sensitive; external/PUBLIC results require semantic minimization.

Kernel SQLite and Module semantic persistence remain separate domains.

## Reasoning provider installation/configuration baseline

Reasoning adapter JARs expose `ReasoningMechanismProvider` through the public reasoning SPI. Each provider now supplies three distinct concerns:

```text
descriptor()    -> stable ReasoningProviderId plus owner-facing field metadata
configurator()  -> repeatable named-instance configuration/list/enable/disable/remove semantics
materialize(...) -> zero or more enabled ReasoningMechanism values
```

Provider discovery does not require any configured instance and does not materialize mechanisms. The application uses one provider object lifecycle per loader and closes providers/loaders during shutdown or rollback.

The shipped stable provider identities are `llamacpp-unix`, `llamacpp-http` and `openai-compatible`. The two llama.cpp transports are intentionally distinct because their required settings differ. The independently compiled verification provider uses `independent-text` and consumes the same public SPI without `madre-app` or Kernel implementation dependencies.

The minimal owner-facing field kinds are exactly `TEXT`, `INTEGER` and `CHOICE`. Descriptors may expose display/help information, required/default values, allowed choices and integer bounds when those are needed by the current configurator. Providers—not the host—own parsing, semantic validation, defaults and the mapping to the existing raw property representation.

The current generic owner commands are:

```text
madre reasoning providers
madre reasoning list
madre reasoning inspect <provider>/<instance>
madre reasoning configure <provider> <instance> [--set <field>=<value>]...
madre reasoning enable <provider>/<instance>
madre reasoning disable <provider>/<instance>
madre reasoning remove <provider>/<instance>
```

`configure` without `--set` is interactive. Repeated `--set <field>=<value>` is non-interactive/scriptable. Configuration creates/enables the named instance after provider validation. `disable` preserves the instance configuration while preventing materialization after restart; `remove` deletes only configuration owned by that exact provider instance. Neither command deletes the provider JAR.

The existing `reasoning.*` string representation remains executable for compatibility and developer fixtures. It is no longer necessary for the ordinary owner setup path. `madre-app` generically applies `ReasoningProviderConfigurationUpdate` values; it does not know shipped provider field names or raw keys.

Configuration persistence copies all unrelated properties into a candidate, applies only the provider-produced update, writes a same-directory temporary file, and performs atomic replacement where supported with safe replace fallback. Provider validation is completed before persistence. An invalid change therefore leaves an established working configuration unchanged.

Configuration validation does not probe endpoints. A configured but unreachable local/external endpoint is valid configuration and later fails, if used, through the existing reasoning runtime failure categories.

In the native package, default reasoning discovery loads the shipped read-only reasoning directory plus the conventional owner data reasoning directory. Explicit `reasoning.directory` retains exact one-directory semantics. The native first-run configuration contains no provider instance configuration, so the shipped adapters legitimately materialize zero mechanisms.

`ReasoningService` remains the only Module-facing Kernel reasoning port. Requests carry nominal reasoning computation plus execution controls and derived Module/Sensitivity facts, not Material semantics, semantic continuation, provider configuration, concrete mechanism identity or Operation Risk. Kernel continues to own deterministic compatible selection, resources, immediate/durable execution, retry/cancellation, SQLite runtime persistence and opaque result delivery.

`ReasoningCapability` remains mechanism-only; it does not know Modules, Agents, Material, configuration UI, provider accounts or product lifecycle.

An empty reasoning registry is valid at boot.

## Search baseline

Search remains ordinary application/domain I/O, not Kernel reasoning. `madre-web-search` remains a reusable typed value library and `madre-adapter-searxng` an ordinary Java client with no Kernel dependency.

## Native packaging and developer distribution

`madre-app` uses the Gradle `application` plugin for developer `installDist`/`distZip` and direct JDK 21 `jpackage` tasks for owner packaging. No third-party packaging framework was introduced.

`jpackageAppImage` creates an isolated application image containing the application classpath, shipped Module/reasoning JARs, current first-run bootstrap policy and a linked Java runtime. `nativePackage` creates MSI on Windows and DEB on Linux from the same staged inputs. Windows uses per-user installer semantics; Linux uses the conventional `madre` package identity.

The native product launcher permits zero-argument startup only when the packaged bootstrap marker exists. The Gradle developer distribution deliberately lacks that marker and therefore retains the previous explicit-properties usage. `--config <path>` is the explicit modern override; the historical positional properties path remains accepted for tests/development.

Normal native startup never writes to the application image and does not use relative CWD state. The bootstrap creates owner configuration/data/state/artifact directories before application assembly and persists host-resolved absolute database/state paths. Generic reasoning configuration writes only the selected owner configuration file; shipped program artifacts remain read-only.

## Diagnostics baseline

`madre doctor` is intentionally a host diagnostic, not a telemetry/observability framework. On a healthy installation it reports:

- MADRE implementation version and actual bundled Java runtime path/version;
- resolved configuration, data and state locations;
- effective Module/reasoning artifact directories;
- successful Module/reasoning discovery initialization;
- installed Module count/identities and CORE configured/resolved state;
- installed/configurable reasoning-provider count/identities;
- configured provider-instance count, exact provider/instance identities and enabled/disabled state;
- successfully materialized reasoning mechanism count/identities.

It does not enumerate provider field values, credentials or arbitrary raw `reasoning.*` settings. Provider installed with zero instances, configured-but-disabled instances, and zero materialized mechanisms are all valid diagnostic states.

## Cross-platform acceptance

The `Java 21 cross-platform build` workflow remains mandatory on Windows and Ubuntu. It proves architecture guards, unit tests, Javadocs/publication, developer packages, independent Module/reasoning builds, Module-to-Module interoperability, owner-local/external-PUBLIC behavior, Module configuration, zero-reasoning operation, CORE/interaction independence, independent installed reasoning execution and durable restart/recovery.

The `Native owner package` exact-head workflow runs on both first-class hosts. In addition to the established bundled-runtime/bootstrap/MSI-DEB lifecycle proof, it now discovers the shipped provider types before configuration and exercises shipped provider metadata/configuration through the generic host path while keeping fresh first-run mechanism count at zero. Provider-owned validation is tested without requiring a reachable external service.

The `Reasoning owner configuration` exact-head workflow is the stronger independent-provider proof. Each Windows/Linux job:

1. publishes the public MADRE artifacts and builds a native application image;
2. builds `verification/reasoning-consumer` in its isolated Gradle build against those published artifacts;
3. places only that third-party provider JAR in the conventional owner-writable reasoning installation directory;
4. proves its stable provider identity/metadata are discoverable before any instance exists;
5. configures repeatable named instances through the generic CLI with no provider-specific host code;
6. proves an invalid provider-owned value leaves the persisted configuration byte-for-byte unchanged;
7. restarts and observes one materialized independent mechanism in `doctor`;
8. disables and restarts, observing zero mechanisms while retaining the provider artifact/configuration;
9. re-enables and observes materialization again;
10. removes one instance and proves another provider instance remains untouched;
11. uses no cloud account, credential, container or live model endpoint.

The existing independently installed reasoning execution acceptance remains separate and continues to prove that configured independent mechanisms execute through the normal selection/security/public-transformation path.

## Public development platform status and remaining product gates

The SDK/reasoning SPI have strong independent-consumer proofs, but external publication/discovery, developer-facing documentation/tooling/testkit and a complete unrelated-developer product journey remain unfinished.

The reasoning configurator does not imply generic settings infrastructure. Remaining owner-product gaps include generic Module configuration, Module/reasoning artifact download/install/update/remove management, marketplace/repository discovery, credential storage/account flows, the meaningful CORE-led owner-interaction redesign and natural delayed semantic follow-up.

`docs/master-development-plan.md` remains historical foundation-plan evidence, not the active roadmap.
