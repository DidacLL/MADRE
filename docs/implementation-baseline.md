# Implementation Baseline

The active implementation is a Java 21 Gradle Kotlin DSL multi-project system with
the intended artifact boundaries:

- `madre-algebra`, the dependency-free nominal algebra carriers;
- `madre-sdk`, the immutable Module model, Agent-owned Workflow definitions,
  versioned definition codec, bounded Operation construction and responsibility-specific
  ports;
- `madre-kernel`, the live Module boundary, physical Capability SPI, deterministic
  selection, resources, immediate/durable dispatcher, SQLite lifecycle,
  configuration and in-process public ports;
- `madre-text-inference`, the transport-neutral physical text-inference command/result
  contract and codec;
- `madre-web-search`, the physical web-search command/result contract and codec;
- `madre-adapter-llamacpp`, real llama-server adapters for preferred Unix-domain-socket
  IPC and loopback-HTTP compatibility;
- `madre-adapter-openai-compatible`, a real chat-completions connector accepting an
  externally prepared HTTP transport;
- `madre-adapter-searxng`, a real SearXNG JSON-search HTTP connector;
- `madre-module-owner-interaction`, the shipped ordinary owner-interaction Module;
- `madre-module-web-search`, an ordinary live-web research Module;
- `madre-app`, the installable assembly and replaceable local console.

Modules provide Skills; Agents own the Workflows in their learned repertoire. The
current minimal Workflow is an ordered sequence of Operations triggered together as
one semantic behavior. The Module definition codec is version 2 and nests Workflow
definitions under their owning Agent; it introduces no workflow engine or Kernel
scheduling language.

The owner-interaction Module retains its ordinary interaction Agent, standard-prompt
and fast-lane Operations, Module-owned Material, durable background association state,
and interpretation of physical inference output. Its behavior remains on the same SDK
and Kernel path as every other Module.

The WebSearch Module supplies one `web-research` Skill and one `researcher` Agent. Its
only public Operation is `single-search`. `single-search` converts search-query
Material into `WebSearchCommand`, submits an immediate physical request through the
same `ExecutionService`, interprets returned physical hits as Module-owned
`SearchResultSet` Material, and preserves the query's actual Sensitivity on that new
Material.

The researcher owns `deep-search`. Its current minimal Operation sequence is two
`single-search` calls followed by one private `review-searches` Operation. The two
search calls are triggered together. Their independent result Material is joined with
Sensitivity composed by maximum, then `review-searches` constructs a normal
`TextInferenceCommand` and submits it through Kernel for synthesis. Every physical
crossing therefore receives its values from the exact bounded Operation call: each web
query must independently compose with the selected search Capability Privacy, and the
joined research corpus must independently compose with the selected inference
Capability Privacy. No workflow-level policy, security state, or retry mechanism was
introduced.

The application loads both ordinary Modules into Kernel's live registry and removes
both on shutdown. No generic Module invocation framework was added; both current
Modules run in-process and receive the public SDK ports directly. The configured
`roles.core` resolves through the ordinary live Module registry and is qualified
against the existing minimum CORE behavior: one Agent exposes public
`standard-prompt` and `fast-lane` Operations. Owner interaction qualifies; WebSearch
does not. Assignment changes no type, algebra, privilege, scheduler behavior, or
execution path.

The local console preserves ordinary owner interaction and adds direct WebSearch UI
entrypoints. `/search <S1..S5> <query>` invokes `single-search` and prints interpreted
sources. `/deep-search <S1..S5> <query-1> || <query-2>` executes the researcher's
Workflow and prints the final review. Sensitivity is explicit in these first direct UI
paths so the presentation layer never silently lowers owner information merely to make
an external search reachable. Automatic CORE conversational detection/routing of a
research need is not claimed by this slice.

The SearXNG adapter implements the documented JSON search endpoint as a physical-only
Capability. Its endpoint, receiving Privacy, physical Integrity, expected latency,
preference and resource claims are installation configuration. Its availability is
observed rather than assumed: `/healthz` must succeed before Kernel can select it;
failed reachability is `UNAVAILABLE`, and interrupted/unestablished observation is
`UNKNOWN`. Kernel selects only explicitly `AVAILABLE` Capabilities. SearXNG protocol
payloads do not enter Module definitions or Material.

Local llama.cpp now has two physical adapters under the same
`TextInferenceCommand`/`TextInferenceResult` contract. The Unix-domain-socket adapter
connects to an absolute owner-configured socket path and sends llama-server's HTTP/1.1
wire protocol directly over AF_UNIX; no TCP listener is involved. It probes `/health`
for observed availability, enforces execution deadlines, accepts normal Content-Length
or chunked HTTP responses, and maps transport/protocol failures into ordinary physical
failure categories. The loopback HTTP adapter remains available for compatibility and
accepts only explicit loopback IP literals. Both declare `PhysicalLocation.LOCAL` but
locality still supplies no Privacy or Integrity value.

When both local llama.cpp adapters are installed, Kernel sees two ordinary
Capabilities with the same physical contract. Installation preference can favor the
Unix socket while the existing deterministic selection still applies security algebra,
availability, physical preferences, and resources independently. No Unix, HTTP,
llama.cpp, model-server, or third-party API concept was added to SDK, Kernel domain
objects, Modules, Agents, Workflows, Operations, Material, or the Security Algebra.
Third-party implementation constraints remain inside their Capability adapter.

Deterministic verification covers the SearXNG health/wire protocol with a local HTTP
fixture, the WebSearch Module's Kernel path, two-search-plus-review Workflow ordering,
algebraic exclusion of an S3 query from a P2 search Capability, and exclusion of both
`UNKNOWN` and `UNAVAILABLE` Capabilities. The llama.cpp Unix-socket adapter is exercised
against an actual Java AF_UNIX server fixture, including observed health, request
payload, chunked response decoding, absent-socket failure, and application assembly
with Unix-socket and HTTP inference Capabilities coexisting. These are physical
protocol/runtime tests, not evidence of real model inference.

The historical real llama.cpp acceptance of the owner-interaction Module remains valid
evidence for the text-inference contract and loopback-HTTP adapter. The repository's
Unix acceptance helper now starts an owner-supplied llama-server on an owner-only
`.sock` path (`umask 077`), disables the HTTP fallback in the generated acceptance
configuration, and drives the installed application through the new Unix-socket
Capability. That helper has not been executed in this development session because no
llama-server binary/model is available here, so real Unix-socket model acceptance is
not yet claimed.

Live WebSearch acceptance is also incomplete. It can continue as soon as a reachable
SearXNG installation with JSON search output enabled is supplied or started. At that
point `single-search` must be exercised against real web results through the installed
application. Complete `deep-search` acceptance additionally requires an actually
available text-inference Capability whose Privacy composes with the joined research
Material in the same run. Until both physical mechanisms participate, end-to-end live
WebSearch is not claimed complete.

Kernel SQLite remains restricted to opaque physical bytes, scheduling, attempts,
delivery state, originating Module identity, and accumulated physical values. Semantic
research results and workflow meaning remain Module concerns. The WebSearch behavior
in this slice needs no semantic persistence.

The OpenAI-compatible implementation remains real but unexercised against an external
prepared provider session unless the Owner supplies one. Direct `libllama` remains a
possible future Capability adapter, but its native ABI, model lifecycle, and shared
crash-domain consequences require their own concrete slice; they do not justify
changing MADRE's current public contracts.
