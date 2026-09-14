# Implementation Baseline

This document records what actually executes on the active Java branch. It is intentionally narrower than the target product responsibilities in `MADRE.md` and `docs/architecture/`.

The active implementation is one Java 21 Gradle multi-project system. Windows and Linux run the same application, Kernel, SDK, persistence model, Module installation/configuration mechanism and reasoning-mechanism installation mechanism.

## Current owner-product transition

MADRE now has a native installed-host foundation in addition to the existing Gradle developer distribution.

- JDK 21 `jpackage` produces a Windows MSI and Linux DEB from the same `madre-app`, with a bundled Java runtime.
- A native packaged launch accepts zero arguments. First normal launch resolves conventional per-user host locations, creates them as needed and persists `madre.properties`; later launches reuse that file.
- Windows uses `%APPDATA%\MADRE` for configuration and `%LOCALAPPDATA%\MADRE` for mutable data/state. Linux follows `XDG_CONFIG_HOME`, `XDG_DATA_HOME` and `XDG_STATE_HOME`, with conventional home-directory fallbacks.
- Kernel durable data lives under the state root as `kernel-work.sqlite`; Module-owned state is rooted at `<state>/module-state`.
- Program/shipped artifacts remain inside the read-only application image. Owner-writable future/independent artifact roots are `<data>/modules` and `<data>/reasoning`.
- Packaged default discovery scans both shipped and owner-writable roots. Explicit `modules.directory` and `reasoning.directory` retain their current exact single-directory override semantics for deterministic development/tests.
- The first-run bootstrap copies only current host/application installation policy needed to preserve shipped interaction behavior, then writes host-resolved database/state paths. It contains no enabled reasoning instance and invents no provider endpoint, model, credential, Privacy value or provider setting.
- `madre doctor` reports host/package identity and resolved locations plus configuration/discovery status, installed Modules, resolved CORE and materialized reasoning-mechanism identities/count. It does not dump provider configuration.
- `installDist`/`distZip` remain developer packaging. They intentionally retain explicit properties-file startup, allowing existing temporary-path acceptance and source workflows to remain isolated.

This does not finish the full owner-deployable gate. Provider-aware first-run configuration, generic Module/reasoning lifecycle management and the dedicated CORE-led owner interaction remain subsequent product work. The current string configuration contracts are not promoted into a new generic configurator API by this slice.

## Current semantic/runtime transition

Other previously recorded transitional behavior is unchanged:

- `MadreMain` owns the current local console loop and commands. `interaction.*` independently selects the Module used for ordinary console text; `roles.core` does not drive the binding.
- The shipped owner-interaction Module owns immediate reasoning, durable background reasoning, Module-owned pending semantic state, interpretation, acknowledgement and optional visible follow-up.
- `/updates` remains the explicit foreground mechanism that invokes the Module-specific collection Operation; natural delayed semantic re-entry is not implemented.
- Module and reasoning-provider settings remain immutable/read-only provider-owned strings. No provider-owned typed configuration metadata contract exists yet.
- `roles.core` still resolves an optional ordinary Module identity and creates no privilege or structural qualification.
- Independent SDK/reasoning fixtures prove strong public-contract isolation but not a complete community developer product.

## Artifact boundaries

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, immutable Module-provider configuration, codecs, caller-bound Module interoperability, host-only owner-local/external-PUBLIC invocation ports and the Module-facing reasoning port;
- `madre-reasoning-spi`: public typed reasoning-adapter execution and installation SPI, with no dependency on Kernel implementation;
- `madre-kernel`: live executable Module registry and receiver mechanics, reasoning-capability registry/selection, resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: typed nominal text-inference computation/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: independently discoverable reasoning-adapter artifacts;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: application assembly, host location/bootstrap/diagnostic mechanics, generic multi-root packaged Module/reasoning discovery, exact identity-scoped configuration/context delivery, native packaging and the current replaceable console.

`madre-app` still has no concrete owner-interaction or reasoning-provider implementation dependency, no Module-specific configuration parser/table and no provider-specific reasoning configuration branch. Native packaging copies shipped artifacts as installation data; it does not compile those implementations into application semantics.

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

Sensitivity combines by maximum; Privacy and Integrity by minimum; information reaches a receiver iff `Sensitivity <= Privacy`. For one EffectProfile, `min(Risk, Autonomy)` must be supported by actual non-user causal Integrity, or I5 when none exists. Risk is not propagated into reasoning work. `Privacy.MODULE` remains the fixed installed-Module receiver boundary. Native package origin, filesystem location and CORE designation confer no algebraic privilege.

## Executable Module/configuration baseline

A running Module is one canonical `ModuleDefinition` plus exact `OperationBinding` values in a `ModuleInstance`. `ModuleProvider` declares its canonical `ModuleId` before materialization and receives identity-scoped immutable `ModuleProviderConfiguration` plus a caller-bound `ModuleContext`.

Current Module settings remain:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Each provider owns key vocabulary, validation, parsing and defaults. `madre-app` only scopes/delivers strings. Configuration for an uninstalled identity, duplicate providers, materialized identity mismatch or invalid executable bindings fail before partial reachability.

In the native package, default Module discovery loads JARs from the shipped application `modules` directory and the conventional owner data `modules` directory. Explicit `modules.directory` replaces that default pair with exactly the configured directory, preserving the previous developer contract.

No generic provider metadata/schema was added.

## Receiver boundaries

Module-to-Module, owner-local and external/PUBLIC behavior is unchanged by host packaging.

`ModuleContext` contains caller-bound `ModuleDirectory` and `ModuleInvoker`; Module code supplies neither caller identity nor receiver Privacy. The runtime only exposes compatible target `PUBLIC` Operations. A foreign result is delivered only when the caller canonically references its Material type and the Sensitivity reaches fixed `Privacy.MODULE`; the exact callee Material is preserved and `PublicResultTransformer` is not run.

`OwnerModuleInvoker.invokeOwner` executes a canonical installed `PUBLIC` Operation and returns validated Module-created Material unchanged.

`PublicModuleInvoker.invokePublic` executes the same bounded Operation but requires Module-owned semantic public transformation into new declared Material able to reach `Privacy.PUBLIC`.

Both host ports remain absent from `ModuleContext`; packaging, shipped origin and CORE assignment grant no additional authority.

## Local interaction and CORE baseline

The application-local immutable `LocalInteractionBinding` remains optional and is configured through current `interaction.*` strings. `MadreMain` still owns the foreground loop. Ordinary text/default and `/standard` use owner-local invocation; `/updates` invokes only the configured Module collection Operation; `/sensitivity` modifies explicit console input classification.

`roles.core` remains optional and non-privileged. If the configured identity is installed, the live registry resolves it; absent/unresolved CORE does not prevent boot. Current interaction binding remains independent of CORE assignment. The native bootstrap packages the current shipped role/interaction installation policy only to preserve the existing owner-visible behavior; it does not make those names a universal CORE API and does not add a privileged type/port.

## Shipped owner-interaction Module baseline

The shipped owner-interaction Module remains an ordinary installed Module using the same provider/configuration/discovery path as independent Modules. Its existing bounded Operations continue to prove immediate reasoning, durable background reasoning with Module-owned pending state, and Module interpretation/acknowledgement/cleanup after restart. Owner-local sensitive results remain sensitive; external/PUBLIC results require semantic minimization.

Kernel SQLite and Module semantic persistence remain separate domains.

## Reasoning installation/runtime baseline

Reasoning adapter JARs expose `ReasoningMechanismProvider` through the public reasoning SPI. Providers receive a read-only view of current `reasoning.*` strings and own provider-specific parsing. One provider may materialize zero, one or many mechanisms. Installing an adapter does not enable any mechanism and Privacy is never inferred from endpoint, transport or locality.

In the native package, default reasoning discovery loads the shipped read-only reasoning directory plus the conventional owner data reasoning directory. Explicit `reasoning.directory` retains the previous exact one-directory semantics. The native first-run configuration contains no provider instance configuration, so the shipped adapters legitimately materialize zero mechanisms.

`ReasoningService` remains the only Module-facing Kernel reasoning port. Requests carry nominal reasoning computation plus execution controls and derived Module/Sensitivity facts, not Material semantics, semantic continuation, concrete mechanism identity or Operation Risk. Kernel continues to own deterministic compatible selection, resources, immediate/durable execution, retry/cancellation, SQLite runtime persistence and opaque result delivery.

`ReasoningCapabilityRegistry` now exposes only a sorted identity snapshot of currently materialized capabilities for host diagnostics. This does not move provider parsing, installation lifecycle or semantic interpretation into Kernel.

An empty reasoning registry is valid at boot.

## Search baseline

Search remains ordinary application/domain I/O, not Kernel reasoning. `madre-web-search` remains a reusable typed value library and `madre-adapter-searxng` an ordinary Java client with no Kernel dependency.

## Native packaging and developer distribution

`madre-app` uses the Gradle `application` plugin for developer `installDist`/`distZip` and direct JDK 21 `jpackage` tasks for owner packaging. No third-party packaging framework was introduced.

`jpackageAppImage` creates an isolated application image containing the application classpath, shipped Module/reasoning JARs, current first-run bootstrap policy and a linked Java runtime. `nativePackage` creates MSI on Windows and DEB on Linux from the same staged inputs. Windows uses per-user installer semantics; Linux uses the conventional `madre` package identity.

The native product launcher permits zero-argument startup only when the packaged bootstrap marker exists. The Gradle developer distribution deliberately lacks that marker and therefore retains the previous explicit-properties usage. `--config <path>` is the explicit modern override; the historical positional properties path remains accepted for tests/development.

Normal native startup never writes to the application image and does not use relative CWD state. The bootstrap creates owner configuration/data/state/artifact directories before application assembly and persists host-resolved absolute database/state paths. Established configuration is not rewritten on restart.

## Diagnostics baseline

`madre doctor` is intentionally a host diagnostic, not a telemetry/observability framework. On a healthy installation it reports:

- MADRE implementation version and actual bundled Java runtime path/version;
- resolved configuration, data and state locations;
- effective Module/reasoning artifact directories;
- successful configuration load and discovery initialization;
- installed Module count/identities and CORE configured/resolved state;
- currently materialized reasoning mechanism count/identities.

It reports identities/paths needed for mechanical diagnosis and does not enumerate arbitrary `modules.config[...]` or `reasoning.*` values.

## Cross-platform acceptance

The original `Java 21 cross-platform build` workflow remains mandatory on Windows and Ubuntu and continues to prove architecture guards, unit tests, Javadocs/publication, developer packages, independent Module/reasoning builds, Module-to-Module interoperability, owner-local/external-PUBLIC behavior, Module configuration, zero-reasoning operation, CORE/interaction independence and durable restart/recovery.

The additional `Native owner package` workflow runs on the exact PR head on both first-class hosts. Each host:

1. builds the `jpackage` application image and native installer;
2. verifies a bundled runtime exists and removes machine `java` from `PATH` before running MADRE;
3. launches with zero arguments in a fresh per-user environment and confirms safe bootstrap outside both CWD and program files;
4. verifies Kernel database, Module state and owner-writable artifact roots in conventional persistent locations;
5. confirms shipped Module/reasoning artifacts are packaged and the shipped Module is discovered;
6. confirms zero reasoning mechanisms is a valid initial state;
7. runs `madre doctor` and checks coherent paths/discovery/CORE/mechanism information without provider-value dumping;
8. launches again and proves the persisted configuration is reused byte-for-byte;
9. verifies clean shutdown;
10. retains the MSI/DEB as a workflow artifact.

Windows additionally performs an unattended per-user MSI install, resolves the installed launcher, executes it in a fresh owner environment, and uninstalls the MSI. Linux performs the corresponding unattended DEB install, installed launch in a fresh XDG environment, and package removal.

No signing/notarization, provider account, external service, container or GPU is required by this packaging acceptance.

## Public development platform status and remaining product gates

The SDK/reasoning SPI still have strong independent-consumer proofs, but external publication/discovery, developer-facing documentation/tooling/testkit and a complete unrelated-developer product journey remain unfinished.

The next deployment-related gaps are deliberately not hidden by this slice: owner-facing provider configuration, Module/reasoning install/remove/update management, and the meaningful CORE-led interaction redesign are still required for the broader product gates in `MADRE.md`.

`docs/master-development-plan.md` remains historical foundation-plan evidence, not the active roadmap.
