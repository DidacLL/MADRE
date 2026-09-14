# Implementation Baseline

The active implementation is one Java 21 Gradle multi-project system. Windows and Linux run the same application, Kernel, SDK, persistence model and Module installation mechanism. There is no separate Windows compatibility implementation and no Linux-specific public runtime.

The current artifact boundaries are:

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable Module binding/registration/provider contracts, codecs, public Module invocation ports and the Module-facing reasoning port;
- `madre-kernel`: live executable Module registry/public invocation, reasoning-capability registry/selection, reasoning resources, immediate/durable reasoning, SQLite recovery and result delivery;
- `madre-text-inference`: typed nominal text-inference reasoning computation/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`: reasoning adapters;
- `madre-web-search`: reusable typed web-search values;
- `madre-adapter-searxng`: ordinary SearXNG Java client with no Kernel dependency;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-app`: installable assembly, Module discovery, reasoning configuration and replaceable local console.

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

`verification/sdk-consumer` is an executable independent Module built against only the published `io.github.didacll:madre-sdk` artifact. CI installs its JAR into a built MADRE distribution, discovers it, invokes `phd.module/inspect`, expects `public:hello`, and rejects leakage of its internal `private:hello` Material.

## Reasoning runtime baseline

The Module-facing port is `ReasoningService`. `ReasoningRequest<R,C>` requires `C extends ReasoningComputation<R>`, preventing the reasoning runtime from becoming a generic command/action envelope by structural accident.

A request derives originating Module and carried Sensitivity from a valid bounded `OperationCall`. It carries the reasoning computation plus execution controls: mode, priority, eligibility, timeout, cancellation, retry and typed location/latency preferences.

It carries no Material identity/type, semantic continuation, concrete reasoning-mechanism identity or Operation Risk.

Kernel's `ReasoningCapabilityRegistry` selects only compatible reasoning contracts. Selection composes carried Sensitivity with manifest receiving Privacy, then applies observed availability, resource capacity, typed location/latency preferences and deterministic ordering.

The current text-inference mechanisms are llama.cpp AF_UNIX, explicit llama.cpp loopback HTTP compatibility and OpenAI-compatible HTTP. Provider-specific protocol and account/session details remain outside the public reasoning contract.

Immediate and durable reasoning share selection/resource/failure semantics. Durable work is stored in `SQLiteReasoningWorkStore`; queued input, attempt state and pending output survive restart. Input/result bytes remain opaque to Kernel and are removed according to execution/delivery/retention lifecycle.

An empty reasoning registry is valid at boot.

## Search baseline

Search is ordinary application/domain I/O, not Kernel reasoning.

`madre-web-search` remains a reusable typed search-value module. `madre-adapter-searxng` provides `SearxngClient` over ordinary JDK/Jackson HTTP/JSON code and depends on `madre-web-search`, not on `madre-kernel`.

The former `SearxngCapability`, standalone `madre-module-web-search`, service-provider registration and deep-search product path were removed from the active application architecture.

Build-time architecture checks reject SearXNG-to-Kernel coupling, SearXNG implementing `ReasoningCapability`, restoration of the standalone WebSearch Module and restoration of the generic `ExecutionService`/`WorkRequest` production API.

## CORE and boot baseline

CORE is optional. `roles.core` may be absent; a configured-but-absent CORE remains unresolved and does not prevent boot. CORE changes no invocation authority, Security Algebra value, visibility or reasoning/scheduling privilege.

The independent Module runtime verification intentionally supplies only Kernel database, Module directory and Module state directory properties, proving discovery/invocation with no CORE assignment and no reasoning mechanism.

## Cross-platform verification

Executable head:

```text
e0f6803e10956f9cb70c0f386592e92f74a1c49f
```

passed GitHub Actions run `34848775519` on both `ubuntu-latest` and `windows-latest`.

On both hosts the run passed:

- `check`;
- production documentation/publication/package verification;
- isolated executable Module build;
- independent Module installation and PUBLIC invocation;
- installed-application smoke.

The initial attempt on the preceding executable commit exposed one Java anonymous-generic inference error in a Kernel test fixture. Commit `e0f6803...` made the fixture type parameters explicit; no production architecture changed.

No container runtime, VM layer, orchestration system, hosted provider account or external service is part of the mandatory build/test path.

## Live integration evidence

Earlier PR #47 acceptance runs exercised real llama.cpp/model inference over the native AF_UNIX transport and separately exercised SearXNG/live search under the older generic physical-capability design.

The llama.cpp evidence remains relevant to the retained reasoning adapter/transport. The SearXNG evidence demonstrates the client/provider integration historically, but it is not evidence that search currently belongs in Kernel or that the removed WebSearch Module remains shipped.

No new external llama.cpp/model or live SearXNG service was executed as part of the reasoning-boundary correction.

## Plan status

`docs/master-development-plan.md` is the completed foundation-plan record and contains historical descriptions of the generic physical-capability stage. Those descriptions are superseded where they conflict with the active architecture above.

Current product meaning is in `MADRE.md`; active focused boundaries are in `docs/architecture/`; this document is the concise executable truth.
