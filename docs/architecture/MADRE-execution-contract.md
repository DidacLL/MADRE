# MADRE Reasoning Execution Contract

## Scope

Kernel execution is intentionally restricted to reasoning. It is not a universal physical-action dispatcher and does not provide a generic tool/capability execution framework.

A Module may perform ordinary application I/O directly inside bounded Module behavior. File, database, network, search, device and similar effects do not enter Kernel merely because they are external.

Reasoning is independent from Module exposure, generic host/debug entry, owner interaction and external/public disclosure. Those concerns may share an `OperationCall`, but they do not determine reasoning eligibility.

## Model-agnostic inference extensibility

`Model-agnostic` means Kernel is independent from concrete models, providers and inference runtimes. It does not require every computation contract to expose only a lowest common denominator.

MADRE separates three categories:

1. portable request/result semantics belong to nominal typed reasoning computation contracts when meaningful across multiple implementations;
2. mechanism/model/runtime tuning belongs to independently installed reasoning providers/adapters and their owner configuration;
3. shared execution mechanics belong to Kernel: compatible selection, information reach, availability/resources, immediate/durable scheduling, retry, cancellation, persistence and result delivery.

Stable computation artifacts currently demonstrate three contracts:

- `madre-text-inference` keeps durable `madre.text-inference.v1`;
- `madre-text-generation` provides `madre.text-generation.v2` with ordered messages, output budget, stops and typed result metadata;
- `madre-embeddings` provides `madre.text-embedding.v1` with explicit `EmbeddingSpace` identity/dimensionality.

These are inference contracts, not Module-domain concepts. New computation families should be added only when concrete inference experiments establish portable semantics.

Engine-specific controls such as threading, device placement, batching, model loading or runtime-specific optimizations remain provider responsibility unless a semantic is genuinely portable across implementations.

## Public reasoning-adapter SPI

The published `madre-reasoning-spi` artifact is the boundary for independently built reasoning mechanisms. It exposes typed reasoning execution concepts plus the small installation/configuration contract required by the owner product:

- `ReasoningCapability`;
- capability identity/manifest;
- exact `ReasoningContract` and durable codecs;
- observed `ReasoningAvailability`;
- `ReasoningExecutionContext`;
- resource claims;
- typed failure reporting;
- provider identity/descriptor/configurator contracts.

A `ReasoningCapability` may implement a mechanism-owned value-level compatibility predicate through `supports(C computation)`. The default is compatible. Kernel evaluates it only after nominal computation/result compatibility. It may express intrinsic mechanism facts such as supported embedding space; it must not perform semantic routing or become a provider-specific Kernel options channel.

The SPI does not expose Module/Agent/Workflow semantics, Material, Kernel persistence objects, application assembly, generic tools/actions, semantic continuation or a universal settings framework.

## Installation, discovery and provider configuration

Reasoning adapter JARs expose `ReasoningMechanismProvider` through Java service-provider discovery. Module and reasoning installation are distinct. Installing an adapter never assigns CORE, registers a Module or automatically enables a mechanism.

Each provider declares stable provider identity and owner-facing descriptor/configurator metadata. The host owns generic discovery/rendering/persistence. Provider-specific field meaning, parsing, defaults, model/runtime controls and validation remain inside the provider artifact.

Current stable owner-facing field kinds are deliberately small (`TEXT`, `INTEGER`, `CHOICE`) and expand only from concrete provider needs.

Privacy is an explicit configured mechanism fact. It is never inferred from endpoint, transport, process placement, model name, provider identity, local/remote location or shipped status.

An absent directory, empty directory, installed provider with no configured instances, or only disabled instances produces zero registered `ReasoningCapability` values and does not prevent MADRE from booting.

## Module-created reasoning work

A Module starts from one valid bounded `OperationCall` and constructs a nominal `ReasoningComputation<R>`. The SDK creates a `ReasoningRequest<R,C>` where `C extends ReasoningComputation<R>`.

The request contains:

- originating `ModuleId`, derived from the bounded Operation call;
- the typed reasoning computation;
- carried Sensitivity, derived from the input Material of that call;
- immediate or durable execution mode;
- priority and eligibility time;
- timeout, cancellation key and retry policy;
- typed selection preferences such as optional location/latency constraints.

The request contains no semantic continuation, Agent, Workflow, cross-Module exposure fact, host-entry choice, public-disclosure binding, concrete reasoning-mechanism identity or Operation Risk.

Operation Risk is deliberately absent. A reasoning mechanism computes information; it does not thereby realize the external consequence described by an EffectProfile.

## Reasoning selection

A `ReasoningCapability<R,C>` exposes:

```text
manifest()
availability()
supports(C computation)
execute(C computation, ReasoningExecutionContext context)
```

Its manifest contains only installed facts used by selection:

- nominal capability identity;
- exact reasoning contract;
- explicit receiving `Privacy`;
- reasoning location;
- expected latency;
- resource claims.

Kernel selection is deterministic. A candidate is usable only when:

1. nominal computation/result types match the request exactly;
2. the capability supports that exact computation value;
3. carried Sensitivity can reach the capability's receiving Privacy;
4. the capability is available;
5. request preferences are satisfied;
6. resources can be reserved.

Installed order, Module exposure, host entry, CORE assignment and shipped-vs-external origin do not create selection privilege.

No rejected security-decision domain object is created. A non-composable mechanism is simply unreachable for that request.

## Contextual Material and Sensitivity

When semantic context combines several source values before reasoning, the Module must represent the actual combined context as Material at combined maximum Sensitivity and derive its `ReasoningRequest` from an `OperationCall` over that Material.

The SDK intentionally has no raw carried-Sensitivity override. A lower-labelled current prompt does not erase more sensitive conversation/history included in its actual reasoning context.

If a weaker receiving mechanism is desired, the Module must deliberately derive new minimized Material when semantically justified. Kernel does not mutate classification to make a mechanism eligible.

## Invocation

Kernel reserves selected resources and invokes the capability with only the typed computation and execution mechanics needed for timeout/cancellation/attempt handling.

The adapter owns provider protocol translation and mechanism-specific tuning. Kernel contains no provider/model-specific branches for standardized computation families.

Current shipped realizations include llama.cpp and OpenAI-compatible adapters. Installation or bundling grants no selection privilege.

## Module-owned interpretation

A reasoning result is not Material. Kernel neither assigns Material identity nor chooses application output Sensitivity.

The originating Module:

1. receives the reasoning result;
2. interprets it according to bounded Module/Agent semantics;
3. creates Material when the result acquires semantic value;
4. updates Module state/presentation as appropriate;
5. invokes another bounded Operation or stops.

Concrete result Material is owned by the Module that creates it. Its nominal Material type may be Module-owned or an explicitly referenced foreign contract.

Receiver semantics apply only after the Module has produced contract-valid Material:

- Module-to-Module composition uses the target Module's exposed interface and fixed `Privacy.MODULE`; no public-disclosure transformer runs;
- generic owner/debug entry returns contract-valid Module Material unchanged;
- ordinary owner interaction is semantically owned by the `OwnerInteractionAgent` in the Module assigned CORE; that Agent may use exact selected-CORE owner-interaction bindings internally and returns Agent-approved `OwnerMessage` values to host presentation;
- external/public disclosure uses a binding with `PublicResultTransformer`, which must create new declared Material capable of reaching `Privacy.PUBLIC`.

These receiver/product boundaries do not change the reasoning request that occurred inside the Operation. In particular, host presentation does not choose reasoning semantics for the selected CORE.

## Immediate and durable reasoning

Immediate reasoning uses the same registry selection, resource coordination, failure mapping and retry semantics as durable reasoning, but returns the result to the waiting Module call.

Durable reasoning stores only physical runtime state required to survive restart: opaque serialized computation bytes, stable reasoning-contract identity, Module identity for delivery, priority/eligibility/timeout/cancellation/retry state, attempts/failure category and opaque result bytes until collection/acknowledgement.

Queued input survives restart. Interrupted running work returns to an eligible state under recovery rules. Successful output survives until collection/acknowledgement or retention cleanup.

Kernel cannot inspect persisted computation bytes as Module knowledge and owns no semantic continuation.

The shipped owner-interaction Module demonstrates the intended split: Kernel persists durable reasoning work while the Module owns pending WorkId association, conversation state, interpretation, acknowledgement and whether a completed result warrants visible follow-up. Its `OwnerInteractionAgent` owns the decision to surface that follow-up; host polling is only physical delivery.

## Failures

Reasoning failures use stable categories such as unavailable, timeout, cancelled, connection, protocol, remote failure, internal and interrupted. Retry is bounded by the request's `ReasoningRetryPolicy`.

The reasoning registry rejects duplicate capability identity and conflicting contract identity/type declarations. Provider discovery rejects duplicate provider identity and invalid provider metadata/configuration. Invalid enabled provider configuration fails startup with provider-owned validation detail.

Absence of an installed/available compatible mechanism is a runtime condition, not a platform boot failure. MADRE can start with an empty reasoning registry.

## Search and other external I/O

Search is not a Kernel reasoning capability merely because a Module may use search to construct context. The repository's SearXNG integration is an ordinary reusable Java client. A Module may use it directly when web search belongs to that Module's behavior.

Likewise, files, databases, HTTP services, devices, MCP and other application I/O remain Module/application concerns unless a concrete shared Kernel responsibility is established.

## Evolution rule

Reasoning architecture should remain narrow enough that independently developed Modules can experiment with semantics while independently developed providers can experiment with models/runtimes.

Do not turn cross-Module exposure, owner interaction, external/public disclosure, provider tuning or application I/O into reasoning-selection semantics. Do not add a universal execution/capability framework merely because Kernel already schedules reasoning.
