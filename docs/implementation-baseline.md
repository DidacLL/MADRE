# Implementation Baseline

The active implementation is one Java 21 Gradle multi-project system. Windows and Linux run the same application, Kernel, SDK, persistence model, Module installation mechanism and reasoning-mechanism installation mechanism. There is no separate Windows compatibility implementation and no Linux-specific public runtime.

The current artifact boundaries are:

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, codecs, public Module invocation ports and the Module-facing reasoning port;
- `madre-reasoning-spi`: published typed reasoning-adapter execution and installation SPI, with no dependency on Kernel runtime implementation;
- `madre-kernel`: live executable Module registry/public invocation, reasoning-capability registry/selection, reasoning resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: published typed nominal text-inference computation/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: independently discoverable reasoning-adapter artifacts;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: installable assembly, generic Module/reasoning-artifact discovery and replaceable local console. It has no concrete reasoning-provider implementation dependency or provider-specific configuration branch.

The former generic Kernel `Capability<C,R>` SPI, generic `ExecutionService`/`WorkRequest`, SearXNG Kernel capability and standalone shipped WebSearch Module are removed.

## Security Algebra baseline

The exact carrier values are:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity by minimum; information reaches a receiver iff `Sensitivity <= Privacy`.

For one exact EffectProfile, `min(Risk, Autonomy)` must be supported by the combined Integrity of actual non-user causal participants. `OperationCall` enforces that causal composition.

Risk is not propagated into reasoning work. `ReasoningCapabilityManifest` contains receiving Privacy but no action-realizer Integrity. Reasoning computation does not itself realize the external effect represented by a Module's Risk.

No generic policy evaluator exists.

## Executable Module baseline

A running Module is registered as `ModuleInstance`: one canonical `ModuleDefinition` and an exact `OperationBinding` for every declared Operation. Registration rejects incomplete, undeclared, foreign and non-canonical executable surfaces.

`ModuleProvider` is the standard Java service-provider installation entrypoint. Application discovery uses `modules.directory` (or the distribution sibling `modules/` directory) and JDK APIs. Shipped Modules are copied into that directory during packaging but are not concrete `madre-app` compile dependencies.

`ModuleInvoker.invokePublic` resolves the installed Module and exact canonical public Operation. Private Operations and forged/mismatched calls are rejected.

Every public binding owns a `PublicResultTransformer`. Before internal Material crosses the external boundary, the transformer must create new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`. Output type/owner/maximum-Sensitivity declarations remain enforced.

`verification/sdk-consumer` is an executable independent Module built outside the root dependency graph against published MADRE artifacts. It retains `phd.module/inspect`, which performs no reasoning, and adds `phd.module/reason`, which submits a real text-inference `ReasoningRequest` through the supplied `ReasoningService`. Both results pass through the same Module-owned PUBLIC semantic transformation.

## Public reasoning-adapter SPI baseline

`madre-reasoning-spi` is the public adapter boundary. It contains the existing reasoning-specific execution contract plus the minimal installation boundary:

- `ReasoningCapability`;
- `ReasoningCapabilityId` and `ReasoningCapabilityManifest`;
- `ReasoningContract` and `ReasoningCodec`;
- `ReasoningAvailability`;
- `ReasoningExecutionContext`;
- `ResourceClaim` and `ResourceId`;
- typed `ReasoningException` failure reporting;
- immutable `ReasoningProviderConfiguration`;
- `ReasoningMechanism` materialization with ordinary installation preference;
- `ReasoningMechanismProvider` service-provider entrypoint.

The SPI depends on the public SDK. It does not expose `ReasoningCapabilityRegistry`, SQLite stores, schedulers, application assembly, Module/Material semantics, generic tools/actions or semantic continuation.

Both `madre-reasoning-spi` and `madre-text-inference` are published with source/Javadoc artifacts so an adapter can compile independently against the public boundary and the computation contract it implements.

## Reasoning installation baseline

Reasoning JARs are discovered independently from Modules. Installed distributions use sibling `reasoning/` by default; `reasoning.directory` overrides it. Discovery uses `Path`, `Files`, `URLClassLoader` and `ServiceLoader`.

Missing and empty reasoning directories are valid. A provider may materialize zero, one or many mechanisms. Disabled/unconfigured instances register nothing. Adapter installation by itself never enables a mechanism.

`madre-app` passes an immutable read-only view of `reasoning.*` owner configuration to discovered providers and performs only generic registration. Provider-specific parsing, including endpoint/model fields and explicit Privacy, is owned by each adapter. Privacy is not inferred from locality, endpoint or transport.

The shipped llama.cpp AF_UNIX, llama.cpp loopback-HTTP compatibility and OpenAI-compatible artifacts are copied into `reasoning/` and discovered with the same `ReasoningMechanismProvider` path as external adapters. Their explicit-enable behavior remains unchanged in meaning: no configured enabled instance means no registered mechanism.

Provider/classloader resources are closed at shutdown. Startup failures roll back already-created mechanism registrations and close loaders/providers. Null provider materialization and null mechanism values are rejected.

## Reasoning runtime baseline

The Module-facing port is `ReasoningService`. `ReasoningRequest<R,C>` requires `C extends ReasoningComputation<R>`, preventing the reasoning runtime from becoming a generic command/action envelope by structural accident.

A request derives originating Module and carried Sensitivity from a valid bounded `OperationCall`. It carries the reasoning computation plus execution controls: mode, priority, eligibility, timeout, cancellation, retry and typed location/latency preferences.

It carries no Material identity/type, semantic continuation, concrete reasoning-mechanism identity or Operation Risk.

Kernel's `ReasoningCapabilityRegistry` selects only compatible reasoning contracts. Selection composes carried Sensitivity with manifest receiving Privacy, then applies observed availability, resource capacity, typed location/latency preferences, configured installation preference and deterministic identity ordering.

Duplicate capability identity is rejected. The registry also rejects computation-contract declarations where nominal contract identity and computation/result Java types conflict.

Immediate and durable reasoning share selection/resource/failure semantics. An unavailable mechanism is not selected; an immediate request with no reachable available mechanism fails with the typed unavailable category.

Durable work is stored in `SQLiteReasoningWorkStore`; queued input, attempt state and pending output survive restart. Persistence uses stable reasoning-contract identity and opaque encoded computation/result bytes, not adapter implementation class names. After restart, queued work becomes runnable when a compatible contract/mechanism is registered again.

An empty reasoning registry is valid at boot.

## Independent reasoning-adapter proof

`verification/reasoning-consumer` is a separate Gradle build whose only MADRE dependencies are:

```text
io.github.didacll:madre-reasoning-spi:0.1.0-SNAPSHOT
io.github.didacll:madre-text-inference:0.1.0-SNAPSHOT
```

It produces `independent-reasoning.jar`, exposes `ReasoningMechanismProvider`, and materializes a deterministic text-inference mechanism from ordinary owner configuration. The mechanism returns `independent:<prompt>` and requires no provider service, network, model, native binary, GPU or credentials.

The CI acceptance journey builds a MADRE distribution, builds the independent Module and independent reasoning adapter separately, installs their JARs into the real `modules/` and `reasoning/` directories, configures one independent mechanism instance, invokes `phd.module/reason`, and requires the PUBLIC result:

```text
public:reasoned:independent:hello
```

The same journey rejects leakage of the internal `private:reasoned:` Material. A separate invocation points the runtime at an empty reasoning directory and invokes `phd.module/inspect`, proving a non-reasoning installed Module remains usable with zero ReasoningCapabilities.

## Search baseline

Search is ordinary application/domain I/O, not Kernel reasoning.

`madre-web-search` remains a reusable typed search-value module. `madre-adapter-searxng` provides `SearxngClient` over ordinary JDK/Jackson HTTP/JSON code and depends on `madre-web-search`, not on `madre-kernel`.

The former `SearxngCapability`, standalone `madre-module-web-search`, service-provider registration and deep-search product path were removed from the active application architecture.

Build-time architecture checks reject SearXNG-to-Kernel coupling, SearXNG implementing `ReasoningCapability`, restoration of the standalone WebSearch Module and restoration of the generic `ExecutionService`/`WorkRequest` production API. They also reject concrete llama.cpp/OpenAI-compatible adapter knowledge or normal concrete-adapter implementation dependencies in `madre-app`, and Kernel-runtime dependencies from the public reasoning SPI/shipped reasoning adapters.

## CORE and boot baseline

CORE is optional. `roles.core` may be absent; a configured-but-absent CORE remains unresolved and does not prevent boot. CORE changes no invocation authority, Security Algebra value, visibility, reasoning installation, reasoning selection or scheduling privilege.

The runtime supports an absent reasoning directory, an empty reasoning directory and installed providers that produce zero mechanisms. The independent Module no-reasoning verification demonstrates real installed PUBLIC invocation in that state.

## Cross-platform acceptance matrix

The GitHub Actions matrix runs the same source/distribution acceptance path on `ubuntu-latest` and `windows-latest`:

- ordinary `check`;
- Javadocs/publication/package verification;
- built distribution;
- isolated independent Module build;
- isolated independent reasoning-adapter build;
- no-reasoning Module installation/PUBLIC invocation;
- independent reasoning-adapter installation/discovery;
- actual reasoning execution through that adapter;
- Module interpretation plus PUBLIC semantic transformation;
- installed-application smoke.

No container runtime, VM layer, orchestration system, hosted provider account or external service is part of the mandatory build/test path.

## Live integration evidence

Earlier PR #47 acceptance runs exercised real llama.cpp/model inference over the native AF_UNIX transport. That remains relevant evidence for the shipped llama.cpp reasoning implementation.

Earlier live SearXNG evidence demonstrates the ordinary client/provider integration historically, but it is not evidence that search belongs in Kernel or that the removed WebSearch Module remains shipped.

The owner-installable reasoning-adapter acceptance journey itself uses the deterministic independent adapter; it does not claim new live external-provider/model evidence.

## Plan status

`docs/master-development-plan.md` is the completed foundation-plan record and contains historical descriptions of earlier implementation stages. Those descriptions are superseded where they conflict with the active architecture above.

Current product meaning is in `MADRE.md`; active focused boundaries are in `docs/architecture/`; this document is the concise executable truth.
