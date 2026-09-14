# Implementation Baseline

The active implementation is one Java 21 Gradle multi-project system. Windows and
Linux run the same application, Kernel, SDK, persistence model and shipped Modules;
there is no separate Windows compatibility implementation and no Linux-specific public
runtime.

The current artifact boundaries are:

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, codecs,
  bounded Operation construction and public Module-facing ports;
- `madre-kernel`: live Module and Capability registries, deterministic selection,
  resources, immediate/durable physical execution, SQLite recovery and result delivery;
- `madre-text-inference`: typed physical text-inference command/result contract;
- `madre-web-search`: typed physical web-search command/result contract;
- `madre-adapter-llamacpp`: llama.cpp physical adapters;
- `madre-adapter-openai-compatible`: OpenAI-compatible physical adapter;
- `madre-adapter-searxng`: SearXNG physical web-search adapter;
- `madre-module-owner-interaction`: shipped ordinary CORE-capable Module;
- `madre-module-web-search`: ordinary WebSearch Module;
- `madre-app`: installable assembly and replaceable local console.

## Security Algebra baseline

All five nominal Security Algebra carriers use ranks 0 through 5. Rank 0 is
`SYSTEM_RESERVED` for every carrier and is not an ordinary Module, Agent, Operation,
Material, EffectProfile, causal-participant or installed-Capability value. Ordinary
Sensitivity, Integrity, Risk and Autonomy retain their rank names `S1..S5`, `I1..I5`,
`R1..R5` and `A1..A5`.

Privacy uses semantic domain names rather than placeholder rank names:

```text
0  SYSTEM_RESERVED
1  PUBLIC
2  UNKNOWN
3  LOCAL
4  MODULE
5  SECRET
```

Installation configuration may use `P1..P5` as textual rank notation, mapping to the
semantic Privacy values above. It may also use the semantic names directly. Rank 0 is
not accepted as an ordinary connector fact.

The algebraic rules remain intrinsic to the values: Sensitivity combines by maximum,
Privacy and Integrity by minimum, information reaches a receiver iff
`Sensitivity <= Privacy`, and one EffectProfile's Risk/Autonomy are checked only
against its actual causal participants and physical realizers. No policy evaluator,
security service, decision wrapper or exception path is introduced by this scale.

Modules provide Skills. Agents own Workflows. The current Workflow is an ordered
sequence of Operations triggered together as semantic behavior; it is not a Kernel
workflow engine.

The owner-interaction Module exposes ordinary `standard-prompt` and `fast-lane`
Operations. Fast lane submits foreground and durable background inference through the
same public `ExecutionService` used by every Module. The Module owns interpretation,
new Material and continuation.

The WebSearch Module supplies one `web-research` Skill and one `researcher` Agent.
Its public `single-search` Operation converts search-query Material into a typed
`WebSearchCommand`, submits ordinary physical work through Kernel, and interprets the
physical result as Module-owned `SearchResultSet` Material. Its Agent-owned
`deep-search` Workflow performs two searches together and then a private review
Operation through the ordinary text-inference Capability contract.

The application loads both Modules into Kernel's live registry. `roles.core` resolves
an ordinary Module identity and qualifies the current minimum public CORE behavior;
assignment creates no CORE subtype, privilege or alternate execution path.

## Cross-platform baseline

CI runs the complete Java build, tests, Javadocs, publication, distribution packaging,
independent SDK-consumer compilation and installed-application smoke path on both a
native Windows GitHub runner and a hosted Linux GitHub runner. The Linux runner is a
CI host, not an architectural Ubuntu dependency. Distro-specific qualification can be
expanded later when useful; the codebase must not introduce Ubuntu-specific product
assumptions in the meantime.

The Gradle application distribution generates `bin/madre` and `bin/madre.bat` as
launch shims for the same `MadreMain` Java application and packaged libraries. They are
not independent products.

No container runtime, VM layer, orchestration system, hosted provider account or
external service is part of the mandatory core build/test path.

## Physical connectors

Capability availability is observed physical state: `UNKNOWN`, `AVAILABLE` or
`UNAVAILABLE`. Kernel selects only explicitly `AVAILABLE` Capabilities. Production
adapters do not hardcode favorable availability merely to keep execution moving.

Local llama.cpp currently has two adapters implementing the same
`TextInferenceCommand`/`TextInferenceResult` contract:

- an `AF_UNIX` domain-socket adapter that connects to an absolute owner-configured
  socket path, probes `/health`, performs llama-server HTTP framing over that socket,
  enforces execution deadlines and creates no TCP listener;
- an explicit loopback-HTTP compatibility adapter restricted to loopback IP literals.

`AF_UNIX` is the protocol-family name rather than a MADRE platform split. The same
`LlamaCppUnixSocketCapability` implementation has now been exercised against real
native llama-server processes on Windows, including actual GGUF inference through the
shipped owner-interaction Module and Kernel. No Windows adapter, alternate Kernel route
or compatibility architecture is required.

Neither llama.cpp connector is enabled by default in the example configuration. Local
HTTP is never selected implicitly simply because it is convenient for integration.

The SearXNG adapter implements the typed web-search contract and probes `/healthz`
before it can be selected. The OpenAI-compatible adapter remains a separate physical
text-inference connector. Provider account/session/authentication mechanics remain
outside MADRE.

## Verification and acceptance state

Deterministic tests exercise the Security Algebra, public SDK contracts, SQLite Kernel
runtime, Capability selection, restart/retry behavior, SearXNG protocol behavior,
WebSearch ordering and interpretation, and llama.cpp physical protocol behavior. The
Unix-domain-socket llama.cpp tests use an actual Java AF_UNIX server fixture where
supported; that is deterministic protocol/runtime evidence rather than the live-model
evidence below.

Cross-platform mechanical verification at PR head
`eb0cf05054547447c5d969e4786be1c133dcca7a` completed successfully in GitHub Actions
run `34794424116`: native Windows and hosted Linux both completed the full clean build,
tests, Javadocs, publication, `installDist`, `distZip`, isolated SDK-consumer
compilation and installed-application smoke path.

Windows AF_UNIX was first qualified at the physical transport boundary in one-off run
`34793969886`: a real native `llama-server.exe` built from pinned llama.cpp commit
`ad6c66839af3c5646fba8c6c2e2087a1e4e38948` bound an absolute `.sock`, and the actual
MADRE `LlamaCppUnixSocketCapability` observed `/health` as `AVAILABLE` without a TCP
listener.

Full real-model Windows AF_UNIX acceptance then succeeded in one-off GitHub Actions run
`34794681796` on native Windows Server 2025 with Temurin Java 21.0.12. The run:

1. built the installed MADRE application;
2. built the same pinned llama.cpp commit as a CPU `llama-server.exe` with CURL,
   accelerator-native tuning, embedded UI and prebuilt UI fetching disabled;
3. downloaded only the previously established checksum-pinned
   `qwen2.5-0.5b-instruct-q4_k_m.gguf` acceptance artifact and verified SHA-256
   `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`;
4. started the real model server on
   `D:\a\_temp\madre-llama-model.sock` with no TCP listener;
5. started the actual `MadreApplication`, created S5 owner-prompt Material in the
   shipped owner-interaction Module, invoked its ordinary `standard-prompt` Operation,
   submitted the resulting WorkRequest through Kernel, selected the installed
   `LlamaCppUnixSocketCapability`, performed real model inference, and returned the
   generated physical result for Module interpretation as answer Material.

The generated answer was nonblank and llama-server logged the model as loaded,
`listening on unix://D:\a\_temp\madre-llama-model.sock`, followed by real prompt and
generation timings. This is real Windows model acceptance through the complete shipped
MADRE path, not a protocol fixture or server-health-only result.

The temporary acceptance workflow and model are not part of normal MADRE CI or the
product distribution. The separate probe branch is reset after recording the evidence;
the product retains no build-time llama.cpp, Hugging Face, model-download, container or
provider dependency.

Historical real acceptance on this PR also exercised the shipped owner-interaction
Module against a real llama.cpp model through the loopback-HTTP compatibility adapter.
That remains evidence for the same text-inference contract and Module/Kernel execution
path, while HTTP remains compatibility rather than the preferred same-host mechanism.

`scripts/acceptance-local.sh` exercises real GGUF inference through the AF_UNIX adapter
from a Unix shell using an owner-supplied `llama-server` executable and model. The
helper is host-specific acceptance tooling, not a separate product implementation.

Repeating the Windows AF_UNIX journey on the Owner's particular machine, GPU/backend
and chosen model is installation/hardware qualification, not an unresolved MADRE
cross-platform transport or execution feature.

Live WebSearch acceptance remains incomplete until a real SearXNG JSON endpoint is
available. Complete live `deep-search` additionally requires a real available
text-inference Capability in the same run. Any live acceptance conclusion must be
re-run after this Security Algebra correction before being treated as current evidence.

Kernel SQLite remains restricted to physical work, scheduling, attempts, delivery
state and opaque payloads. Semantic search results, Material, workflows and
continuation remain Module-owned.
