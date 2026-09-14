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

Windows and Linux remain equal validation targets for executable or build-logic
changes. The change-aware workflow first classifies the paths changed since the
previous branch/PR head. Executable/build-logic changes run `check` on both hosts;
production-source changes additionally run Javadocs, publication, application
packaging and installed-application smoke; public algebra/SDK surface changes also
compile the independent SDK consumer. Gradle build-cache reuse is enabled and the
workflow does not force `clean` on every change.

Documentation-only and acceptance-workflow-only changes stop after lightweight path
classification and do not allocate the Windows/Linux Java matrix. Active PR branches
are validated by the pull-request event rather than also duplicating the same suite for
every branch push. Superseded runs for the same PR/ref are cancelled by workflow
concurrency.

The Linux runner is a CI host, not an architectural Ubuntu dependency. Distro-specific
qualification can be expanded later when useful; the codebase must not introduce
Ubuntu-specific product assumptions in the meantime.

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

Cross-platform mechanical verification of executable head
`914987e4780d115225af7253210aad54ef2e37b6` completed successfully in GitHub Actions
run `34800439078`: native Windows and hosted Linux both completed `check`, Javadocs,
publication, `installDist`, `distZip`, isolated SDK-consumer compilation and
installed-application smoke. That head also introduced the change-aware CI rules.

CI selectivity was exercised separately in run `34800483346`: a documentation-only
change produced `verify=false`, `production=false`, `sdk-consumer=false`, so the Java
matrix and its Windows/Linux test, package, SDK-consumer and application-smoke work did
not run. Acceptance-workflow-only changes were likewise classified without launching
the generic Java matrix; the dedicated live WebSearch probe's generic workflow run
`34800999504` completed with its build job skipped.

Windows AF_UNIX was first qualified at the physical transport boundary in one-off run
`34793969886`: a real native `llama-server.exe` built from pinned llama.cpp commit
`ad6c66839af3c5646fba8c6c2e2087a1e4e38948` bound an absolute `.sock`, and the actual
MADRE `LlamaCppUnixSocketCapability` observed `/health` as `AVAILABLE` without a TCP
listener.

After the Security Algebra repair, full real-model Windows AF_UNIX acceptance was
re-run successfully in GitHub Actions run `34800581474` on native Windows Server 2025
with Temurin Java 21.0.12. The run:

1. built the installed MADRE application;
2. built pinned llama.cpp commit `ad6c66839af3c5646fba8c6c2e2087a1e4e38948`
   as a CPU `llama-server.exe` without an embedded/prebuilt UI or provider dependency;
3. downloaded the checksum-pinned `qwen2.5-0.5b-instruct-q4_k_m.gguf` acceptance
   artifact and verified SHA-256
   `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`;
4. started the real model server on a Windows `.sock` with no TCP listener;
5. constructed the actual llama Capability with `Privacy.SECRET`, configured the
   installation with textual `SECRET`, created S5 owner-prompt Material in the shipped
   owner-interaction Module, invoked its ordinary `standard-prompt` Operation, submitted
   the resulting WorkRequest through Kernel, selected the installed
   `LlamaCppUnixSocketCapability`, performed real model inference, and returned a
   nonblank generated result for Module interpretation.

This is current real Windows S5-to-SECRET acceptance through the complete shipped
MADRE path after the algebra correction, not a protocol fixture or server-health-only
result.

Live WebSearch and complete `deep-search` acceptance were likewise re-run after the
Security Algebra repair in GitHub Actions run `34800999502`. The temporary acceptance
installation used pinned SearXNG commit
`d4ce87c23431f607162fc5c39ce52c538d64588f` with only its Wikipedia engine enabled,
and the same pinned local llama.cpp/Qwen physical reviewer described above. It used no
container or hosted inference provider.

The run first verified real outbound SearXNG JSON results for `Java programming
language` and `SQLite`; their first URLs were respectively
`https://en.wikipedia.org/wiki/Java_(programming_language)` and
`https://en.wikipedia.org/wiki/SQLite`. MADRE then executed both queries as S1 Material
through an explicitly `PUBLIC` SearXNG Capability. The WebSearch Module interpreted
both physical result sets as Module-owned Material and preserved S1 sensitivity. Its
Agent-owned `deep-search` Workflow then executed the two searches and the private
review Operation through the real local AF_UNIX text-inference Capability. The final
nonblank review remained S1. No special WebSearch algebra rule, Kernel workflow path,
or sensitivity promotion was introduced.

The temporary acceptance workflows, SearXNG installation, llama.cpp build and model
are not part of normal MADRE CI or the product distribution. The probe branches are
reset after recording the evidence; the product retains no build-time llama.cpp,
Hugging Face, SearXNG, model-download, container or provider dependency.

Historical real acceptance on this PR also exercised the shipped owner-interaction
Module against a real llama.cpp model through the loopback-HTTP compatibility adapter.
That remains additional evidence for the same text-inference contract and Module/Kernel
execution path, while HTTP remains compatibility rather than the same-host non-TCP
path.

`scripts/acceptance-local.sh` exercises real GGUF inference through the AF_UNIX adapter
from a Unix shell using an owner-supplied `llama-server` executable and model. The
helper is host-specific acceptance tooling, not a separate product implementation.

Repeating the Windows AF_UNIX journey on the Owner's particular machine, GPU/backend
and chosen model is installation/hardware qualification, not an unresolved MADRE
cross-platform transport or execution feature.

Kernel SQLite remains restricted to physical work, scheduling, attempts, delivery
state and opaque payloads. Semantic search results, Material, workflows and
continuation remain Module-owned.
