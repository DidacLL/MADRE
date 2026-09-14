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
`LlamaCppUnixSocketCapability` implementation has now been exercised against a real
native llama-server process on Windows, while the existing Java fixture exercises its
wire and failure behavior wherever the host supports Unix-domain sockets. No Windows
adapter, alternate Kernel route or compatibility architecture is required.

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
supported; that is protocol/runtime evidence, not real-model acceptance.

Cross-platform mechanical verification at PR head `3a423062516fde77d6262d19eec49ec4464e99ea`
completed successfully in GitHub Actions run `34791697747`: Windows and hosted Linux
both completed the full clean build, tests, Javadocs, publication, `installDist`,
`distZip`, isolated SDK-consumer compilation and installed application smoke path.

Windows non-TCP llama.cpp transport was then qualified separately without modifying the
product branch. One-off GitHub Actions run `34793969886` used native Windows Server
2025, Temurin Java 21.0.12 and pinned llama.cpp commit
`ad6c66839af3c5646fba8c6c2e2087a1e4e38948`. It built a real `llama-server.exe`,
started that server in model-free router mode with an absolute `.sock` host path, and
invoked the actual MADRE `LlamaCppUnixSocketCapability`. MADRE observed the real server
as `AVAILABLE` through `/health` over AF_UNIX. This establishes the Windows non-TCP
transport boundary; it does not claim GGUF inference because no model participated.
The qualification workflow lives only on the separate probe branch and is not a
permanent dependency of normal MADRE CI.

Historical real acceptance on this PR exercised the shipped owner-interaction Module
against a real llama.cpp model through the loopback-HTTP compatibility adapter. That
remains evidence for the text-inference contract and Module/Kernel execution path, but
it does not establish a preferred local transport.

`scripts/acceptance-local.sh` exercises real GGUF inference through the AF_UNIX adapter
from a Unix shell using an owner-supplied `llama-server` executable and model. The
helper is host-specific acceptance tooling, not a separate product implementation.

Real-model AF_UNIX inference remains to be exercised with an actual GGUF model on the
owner's target installation. The missing evidence is model execution, not a missing
Windows non-TCP transport. Do not replace that acceptance step with an HTTP default or
an OS-specific MADRE architecture.

Live WebSearch acceptance also remains incomplete until a real SearXNG JSON endpoint
is available. Complete live `deep-search` additionally requires a real available
text-inference Capability in the same run.

Kernel SQLite remains restricted to physical work, scheduling, attempts, delivery
state and opaque payloads. Semantic search results, Material, workflows and
continuation remain Module-owned.
