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

## Current SDK/developer transition

The first SDK-first experimentation slice is now executable rather than only architectural intent.

- `madre-bom` aligns compatible versions of the public algebra, stable SDK, public testkit, experimental SDK, reasoning SPI and stable computation-contract artifacts, including text inference v1, text generation v2 and text embeddings v1.
- `madre-sdk` remains the stable ownership-demonstrated public foundation; it did not acquire experimental semantic helpers.
- `madre-sdk-testkit` is a separately consumable public semantic/Module test artifact. It depends on public SDK contracts and has no `madre-app` or Kernel implementation dependency.
- `madre-sdk-experimental` is a separately consumable 0.x incubation artifact. It depends on stable SDK; stable SDK, testkit, Kernel, application and reasoning SPI do not depend on it.
- The experimental artifact is non-empty and currently contains one `ModuleDefinitionBuilder`. It only assembles the existing stable `ModuleDefinition`; it does not introduce another runtime/domain model. Workflows remain Agent-owned exactly as in the stable SDK.
- Architecture checks reject dependency inversion from stable/runtime surfaces into the experimental artifact.
- The external `verification/sdk-consumer` build uses the BOM, stable SDK and `madre-text-inference` at production scope, public testkit plus text-generation/embeddings at test scope, and the experimental builder only at test scope. The resulting Module JAR therefore has no experimental or new inference-contract runtime requirement merely because its tests exercise them.
- Its normal `jar` task executes deterministic SDK tests and verifies the Java ServiceLoader provider descriptor before producing `independent-module.jar`.
- The public testkit includes `ModuleTestContext`, `ModuleTestHarness` and `ProgrammableReasoningService`. The latter is generic over arbitrary `ReasoningComputation<R>` and exposes deterministic immediate reasoning plus explicitly controlled queued/running/succeeded/failed/cancelled durable-work states.
- The testkit deliberately does not simulate Kernel scheduling, mechanism selection, resource coordination, retry timing, receiver-boundary Security Algebra or SQLite durability. Installed/integration acceptance remains authoritative for those claims.
- A test-only integer-valued `ReasoningComputation<Integer>` proves the testkit is not text-inference-specific. The same external test project also drives real `TextGenerationCommand` and `TextEmbeddingCommand` values through `ProgrammableReasoningService` using only published artifacts.
- The dedicated cross-platform `SDK developer acceptance` workflow publishes the verification artifacts, copies the independent consumer to a runner-temporary directory outside the checkout, builds/tests it there, installs only its produced JAR into the normal owner-writable Module directory of a packaged MADRE image, proves ServiceLoader discovery with `doctor`, and invokes its owner-local and external/PUBLIC behavior.

The current publication target is still `build/isolated-repository`. This is verification infrastructure, not a public remote artifact repository or released SDK 0.x distribution channel.

A MADRE-specific Gradle Module plugin is not implemented. After BOM alignment, current demonstrated build-specific requirements are Java 21 and standard ServiceLoader metadata; another versioned build API was not justified by the remaining boilerplate in this slice.

## Current semantic/runtime transition

Other transitional behavior remains unchanged:

- `MadreMain` owns the current local console loop and commands. `interaction.*` independently selects the Module used for ordinary console text; `roles.core` does not drive the binding.
- The shipped owner-interaction Module owns immediate reasoning, durable background reasoning, Module-owned pending semantic state, interpretation, acknowledgement and optional visible follow-up.
- `/updates` remains the explicit foreground mechanism that invokes the Module-specific collection Operation; natural delayed semantic re-entry is not implemented.
- Module settings remain immutable provider-owned strings supplied through `ModuleProviderConfiguration`; there is still no generic owner Module configurator metadata contract.
- Reasoning providers have a small provider-owned typed configuration contract used by the real owner configurator: stable provider identity, display/help metadata, `TEXT`/`INTEGER`/`CHOICE` fields, repeatable configured-instance summaries and provider-produced configuration updates.
- `roles.core` still resolves an optional ordinary Module identity and creates no privilege or structural qualification.
- SDK/reasoning fixtures now prove a stronger independent developer path, but this still is not a claim that MADRE has a community-ready public 0.x release channel.

## Artifact boundaries

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: stable typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, immutable Module-provider configuration, codecs, caller-bound Module interoperability, host-only owner-local/external-PUBLIC invocation ports and the Module-facing reasoning port;
- `madre-sdk-testkit`: deterministic public semantic Module-testing utilities over stable SDK contracts, with no Kernel/application implementation dependency;
- `madre-sdk-experimental`: 0.x incubation artifact for higher-level authoring facilities; currently only typed `ModuleDefinitionBuilder`, which produces the stable domain object;
- `madre-bom`: Java-platform version alignment for public MADRE artifacts; it does not make adapters, Kernel or application mandatory SDK dependencies;
- `madre-reasoning-spi`: public typed reasoning-adapter execution and installation SPI, including stable reasoning-provider identity, value-level mechanism compatibility, owner-facing provider descriptors, the narrow provider configurator contract, provider-owned configuration updates and no Kernel implementation dependency;
- `madre-kernel`: live executable Module registry and receiver mechanics, model-agnostic reasoning-capability registry/selection, resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: unchanged durable `madre.text-inference.v1` prompt-based text-inference computation/result contract;
- `madre-text-generation`: richer durable `madre.text-generation.v2` ordered-message text-generation computation/result contract;
- `madre-embeddings`: durable `madre.text-embedding.v1` text-embedding computation/result contract with explicit embedding-space identity and dimensionality;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: independently discoverable/configurable reasoning-adapter artifacts; their HTTP mechanisms can materialize the stable text-inference, text-generation or embedding contract selected by provider-owned instance configuration;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: application assembly, host location/bootstrap/diagnostic mechanics, generic multi-root packaged Module/reasoning discovery, generic reasoning-provider configuration/persistence, native packaging and the current replaceable console.

`madre-app` has no concrete owner-interaction or reasoning-provider implementation dependency, no Module-specific configuration parser/table, no shipped-provider-specific reasoning configuration branch and no experimental-SDK dependency. Architecture checks reject concrete llama.cpp/OpenAI-compatible implementation imports or implementation dependencies from application code. Native packaging copies shipped artifacts as installation data; it does not compile those implementations into application semantics.

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

The SDK testkit does not duplicate this receiver-boundary enforcement. `ModuleTestHarness` direct invocation is explicitly a semantic test surface; the installed-host acceptance remains the proof of actual owner-local, Module-to-Module and external/PUBLIC boundaries.

## Executable Module/configuration baseline

A running Module is one canonical `ModuleDefinition` plus exact `OperationBinding` values in a `ModuleInstance`. `ModuleProvider` declares its canonical `ModuleId` before materialization and receives identity-scoped immutable `ModuleProviderConfiguration` plus a caller-bound `ModuleContext`.

Current Module settings remain:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

Each provider owns key vocabulary, validation, parsing and defaults. `madre-app` only scopes/delivers strings. Configuration for an uninstalled identity, duplicate providers, materialized identity mismatch or invalid executable bindings fail before partial reachability. Startup failure reporting preserves the provider-owned validation detail beneath the generic Module materialization context.

In the native package, default Module discovery loads JARs from the shipped application `modules` directory and the conventional owner data `modules` directory. Explicit `modules.directory` replaces that default pair with exactly the configured directory, preserving the previous developer contract.

No `ModuleProvider` configuration metadata was added by the SDK/testkit slice.

## Receiver boundaries

Module-to-Module, owner-local and external/PUBLIC behavior is unchanged.

`ModuleContext` contains caller-bound `ModuleDirectory` and `ModuleInvoker`; Module code supplies neither caller identity nor receiver Privacy. The runtime only exposes compatible target `PUBLIC` Operations. A foreign result is delivered only when the caller canonically references its Material type and the Sensitivity reaches fixed `Privacy.MODULE`; the exact callee Material is preserved and `PublicResultTransformer` is not run.

`OwnerModuleInvoker.invokeOwner` executes a canonical installed `PUBLIC` Operation and returns validated Module-created Material unchanged.

`PublicModuleInvoker.invokePublic` executes the same bounded Operation but requires Module-owned semantic public transformation into new declared Material able to reach `Privacy.PUBLIC`.

Both host ports remain absent from `ModuleContext`; packaging, shipped origin and CORE assignment grant no additional authority.

The external SDK acceptance installs the independent JAR into the conventional owner-writable Module directory and exercises both host receiver paths. Its owner-local result remains S4/private material; its PUBLIC path returns the Module's S1 semantic public transformation.

## Local interaction and CORE baseline

The application-local immutable `LocalInteractionBinding` remains optional and is configured through current `interaction.*` strings. `MadreMain` still owns the foreground loop. Ordinary text/default and `/standard` use owner-local invocation; `/updates` invokes only the configured Module collection Operation; `/sensitivity` modifies explicit console input classification.

If provider artifacts are installed but zero reasoning mechanisms materialize, normal console startup remains valid and emits only a setup hint pointing to `madre reasoning providers` and `madre reasoning configure ...`; startup is not a mandatory wizard.

`roles.core` remains optional and non-privileged. If the configured identity is installed, the live registry resolves it; absent/unresolved CORE does not prevent boot. Current interaction binding remains independent of CORE assignment. The native bootstrap packages the current shipped role/interaction installation policy only to preserve existing owner-visible behavior; it does not make those names a universal CORE API and does not add a privileged type/port.

The current CORE/console shape is now an experimentation target rather than the public interaction abstraction to freeze next. No CORE selection commands, natural delayed follow-up or standardized `fast-lane`/`standard-prompt`/`collect-background` contract were added in this SDK slice.

## Shipped owner-interaction Module baseline

The shipped owner-interaction Module remains an ordinary installed Module using the same provider/configuration/discovery path as independent Modules. Its existing bounded Operations continue to prove immediate reasoning, durable background reasoning with Module-owned pending state, and Module interpretation/acknowledgement/cleanup after restart. Owner-local sensitive results remain sensitive; external/PUBLIC results require semantic minimization.

Kernel SQLite and Module semantic persistence remain separate domains.

## Module-facing reasoning and testkit baseline

`ReasoningService` remains the only Module-facing Kernel reasoning port. Requests carry nominal reasoning computation plus execution controls and derived Module/Sensitivity facts, not Material semantics, semantic continuation, provider configuration, concrete mechanism identity or Operation Risk. Kernel continues to own deterministic compatible selection, resources, immediate/durable execution, retry/cancellation, SQLite runtime persistence and opaque result delivery.

The public testkit implements that same Module-facing interface as a deterministic in-memory semantic double. A test registers behavior by nominal computation class:

```text
Class<C extends ReasoningComputation<R>> -> Function<C,R>
```

Immediate work completes through the programmed function. Durable work begins `QUEUED`; the test explicitly starts/completes/fails/cancels it and then uses normal `inspect`, `collect` and `acknowledge`. No scheduler thread, SQLite store, retry timer or mechanism-selection algorithm is copied into the testkit.

The fixture's `ScoreComputation implements ReasoningComputation<Integer>` demonstrates that the facility is generic and not bound to a built-in inference family. The external consumer now additionally exercises stable text-generation and text-embedding computations through the same testkit API.

Portable request semantics remain owned by common computation contracts. Mechanism/model/runtime-specific tuning remains provider/adapter-owned. Shared execution mechanics remain Kernel-owned. The heterogeneous-inference slice adds production text generation and embeddings without adding multimodal inference or higher-level semantic memory/RAG/vector-database behavior.

## Heterogeneous inference baseline

The first heterogeneous-inference slice now has three stable public reasoning contracts with separate durable identities:

```text
madre.text-inference.v1   TextInferenceCommand  -> TextInferenceResult
madre.text-generation.v2  TextGenerationCommand -> TextGenerationResult
madre.text-embedding.v1   TextEmbeddingCommand   -> TextEmbeddingResult
```

`madre.text-inference.v1` is unchanged. Existing queued durable text-inference work therefore retains the same Java types, codec and persisted contract identity.

`TextGenerationCommand` carries ordered portable messages with `SYSTEM`, `USER` and `ASSISTANT` roles, maximum generated-token budget and stop sequences. Its result carries generated text, portable completion reason and optional input/generated token counts. It does not contain provider/runtime tuning.

`TextEmbeddingCommand` carries one text input plus an `EmbeddingSpace`. The space contains a stable identity and positive dimensionality. `TextEmbeddingResult` repeats the space and requires exactly that many finite coordinates. Two same-dimensional vectors from different space identities are not declared compatible merely because their lengths match.

`ReasoningCapability` now has a default `supports(C computation)` value predicate. Kernel evaluates it generically after nominal contract matching and before availability/preference selection. Existing providers remain source-compatible because the default returns `true`. The Kernel contains no embedding-specific type check. HTTP embedding capabilities override the predicate to require exact equality with their configured embedding space.

The shipped OpenAI-compatible and llama.cpp loopback-HTTP providers expose a provider-owned `computation` choice. One configured instance materializes exactly one reasoning capability. Absence of that property defaults to `madre.text-inference.v1`, preserving established raw configuration. Embedding instances additionally require provider-owned `embedding-space-id` and `embedding-dimensions` fields. The host remains generic and does not parse or branch on these fields.

The OpenAI-compatible adapter executes generation through `chat/completions` and embeddings through `embeddings`. The llama.cpp loopback-HTTP adapter executes generation through `/v1/chat/completions` and embeddings through `/v1/embeddings`. Both embedding adapters require numeric output with exact configured dimensionality before producing a public result. The llama.cpp AF_UNIX provider remains text-inference v1 only in this slice.

No vector store, similarity index, semantic memory, retrieval workflow, RAG system or knowledge graph is introduced. Those are possible later consumers of embedding results, not responsibilities of this inference substrate.

## Reasoning provider installation/configuration baseline

Reasoning adapter JARs expose `ReasoningMechanismProvider` through the public reasoning SPI. Each provider supplies three distinct concerns:

```text
descriptor()     -> stable ReasoningProviderId plus owner-facing field metadata
configurator()   -> repeatable named-instance configuration/list/enable/disable/remove semantics
materialize(...) -> zero or more enabled ReasoningMechanism values
```

Provider discovery does not require any configured instance and does not materialize mechanisms. The application uses one provider object lifecycle per loader and closes providers/loaders during shutdown or rollback.

The shipped stable provider identities are `llamacpp-unix`, `llamacpp-http` and `openai-compatible`. The two llama.cpp transports are intentionally distinct because their required settings differ. The independently compiled verification provider uses `independent-text` and consumes the same public SPI without `madre-app` or Kernel implementation dependencies.

The minimal owner-facing field kinds are exactly `TEXT`, `INTEGER` and `CHOICE`. Descriptors may expose display/help information, required/default values, allowed choices and integer bounds when those are needed by the current configurator. Providers—not the host—own parsing, semantic validation, defaults and the mapping to the existing raw property representation. The new computation-family and embedding-space settings are expressed with those already-demonstrated field kinds; no metadata vocabulary expansion was required.

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

`ReasoningCapability` remains mechanism-only; it does not know Modules, Agents, Material, configuration UI, provider accounts or product lifecycle. Its value-level `supports` predicate is mechanism compatibility only and does not grant semantic authority. An empty reasoning registry is valid at boot.

## Search baseline

Search remains ordinary application/domain I/O, not Kernel reasoning. `madre-web-search` remains a reusable typed value library and `madre-adapter-searxng` an ordinary Java client with no Kernel dependency.

## Native packaging and developer distribution

`madre-app` uses the Gradle `application` plugin for developer `installDist`/`distZip` and direct JDK 21 `jpackage` tasks for owner packaging. No third-party packaging framework was introduced.

`jpackageAppImage` creates an isolated application image containing the application classpath, shipped Module/reasoning JARs, current first-run bootstrap policy and a linked Java runtime. `nativePackage` creates MSI on Windows and DEB on Linux from the same staged inputs. Windows uses per-user installer semantics; Linux uses the conventional `madre` package identity.

The native product launcher permits zero-argument startup only when the packaged bootstrap marker exists. The Gradle developer distribution deliberately lacks that marker and therefore retains the previous explicit-properties usage. `--config <path>` is the explicit modern override; the historical positional properties path remains accepted for tests/development.

Normal native startup never writes to the application image and does not use relative CWD state. The bootstrap creates owner configuration/data/state/artifact directories before application assembly and persists host-resolved absolute database/state paths. Generic reasoning configuration writes only the selected owner configuration file; shipped program artifacts remain read-only.

The external SDK acceptance uses this same application-image bootstrap to create the ordinary owner data `modules` directory before copying in the independently built JAR. It does not use a private test-only Module loader.

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

The exact-head `SDK developer acceptance` workflow adds a dedicated two-host public-developer proof. Each Windows/Linux job:

1. checks out the exact PR head;
2. runs root checks, publishes the current public artifacts to the verification Maven repository and builds a packaged application image;
3. copies `verification/sdk-consumer` to a runner-temporary directory outside the MADRE checkout;
4. resolves the BOM/stable SDK/testkit/experimental/text-inference/text-generation/embedding artifacts from the absolute verification repository path;
5. executes deterministic Module tests, including reasoning-backed semantic behavior, arbitrary non-text durable reasoning and the stable generation/embedding contracts, without Kernel/application implementation dependencies;
6. builds a normal JAR and verifies its `ModuleProvider` ServiceLoader descriptor;
7. bootstraps the packaged host in an isolated owner home so MADRE creates its normal owner-writable Module directory;
8. copies only the independent Module JAR into that owner directory;
9. proves `doctor` discovers its canonical identity;
10. invokes its bounded Operation through the owner-local receiver and observes the raw S4 Module result;
11. invokes the same bounded Operation through the external/PUBLIC receiver and observes only the Module's S1 semantic public transformation.

The `Native owner package` exact-head workflow runs on both first-class hosts. It builds MSI/DEB, exercises the application image with machine `java` removed from `PATH`, validates fresh bootstrap/restart/diagnostics/provider configuration, and performs native installer install/launch/uninstall lifecycle acceptance.

The `Reasoning owner configuration` exact-head workflow independently publishes the public artifacts, builds `verification/reasoning-consumer` against them, places that third-party provider JAR in the conventional owner-writable reasoning directory and drives the generic provider metadata/configure/restart/disable/enable/remove path on Windows and Linux without a cloud account or live model endpoint.

The existing independently installed reasoning execution and Module-to-Module interoperability acceptances remain separate, preserving the real mechanism/security/runtime proofs that the semantic testkit intentionally does not reproduce.

## Public development platform status and remaining product gates

The first low-friction SDK experimentation slice now exists: dependency alignment, deterministic public testkit, explicit experimental incubation, developer documentation, standard ServiceLoader JAR convention and a complete cross-platform independent-build/install/invoke acceptance journey. The heterogeneous-inference slice now gives that platform multiple stable inference families and two independently installed HTTP mechanism origins without expanding Kernel into an inference-specific framework.

MADRE must still not be described as a community-ready public SDK 0.x release. The main remaining SDK-release gaps are:

- an actual external artifact repository and release/signing/versioning process rather than the verification-local Maven repository;
- explicit release/API compatibility policy for public 0.x artifacts;
- feedback from multiple truly unrelated Module projects rather than one maintained acceptance fixture;
- a Gradle Module plugin/project generator only if that evidence shows enough repeated build friction to justify another public tooling API;
- polished release packaging/examples beyond repository documentation and CI fixtures.

The reasoning configurator does not imply generic settings infrastructure. Remaining owner-product gaps include generic Module configuration, Module/reasoning artifact download/install/update/remove management, marketplace/repository discovery, credential storage/account flows, and the meaningful CORE-led owner-interaction redesign/natural delayed semantic follow-up.

Multimodal contracts, generic tool calling, semantic database/knowledge-graph/RAG/memory/planning frameworks, audio/voice, MCP and external-process Module transport remain intentionally outside this slice. Embeddings here are only a typed inference substrate; no semantic-memory or retrieval architecture is implied.

`docs/master-development-plan.md` remains historical foundation-plan evidence, not the active roadmap.
