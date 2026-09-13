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
- `madre-text-inference`, the physical text-inference command/result contract and
  codec;
- `madre-web-search`, the physical web-search command/result contract and codec;
- `madre-adapter-llamacpp`, the real llama-server connector;
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

The application now loads both ordinary Modules into Kernel's live registry and
removes both on shutdown. No socket or generic Module invocation framework was added;
both current Modules run in-process and receive the public SDK ports directly. The
configured `roles.core` still resolves through the ordinary live Module registry. The
application additionally qualifies the resolved Module against the existing minimum
CORE behavior: one Agent must expose public `standard-prompt` and `fast-lane`
Operations. The owner-interaction Module qualifies; WebSearch does not. Assignment
still changes no type, algebra, privilege, scheduler behavior, or execution path.

The local console preserves ordinary owner interaction and adds direct WebSearch UI
entrypoints. `/search <S1..S5> <query>` invokes `single-search` and prints interpreted
sources. `/deep-search <S1..S5> <query-1> || <query-2>` executes the researcher's
Workflow and prints the final review. Sensitivity is explicit in these first direct UI
paths so the presentation layer never silently lowers owner information merely to make
an external search reachable. Automatic CORE conversational detection/routing of a
research need is not claimed by this slice.

The SearXNG adapter implements the documented JSON search endpoint as a physical-only
Capability. Its endpoint, receiving Privacy, physical Integrity, expected latency,
preference and resource claims are installation configuration. SearXNG protocol
payloads do not enter Module definitions or Material. Provider authentication/session
mechanics remain outside MADRE.

Deterministic verification covers the SearXNG wire protocol with a local HTTP fixture,
the WebSearch Module's real Kernel path, two-search-plus-review Workflow ordering, and
algebraic exclusion of an S3 query from a P2 search Capability before the physical
connector executes. These are fixture-backed protocol/runtime tests, not evidence that
a live external search service was contacted. No prepared SearXNG installation was
supplied in this session, so live-web acceptance remains unexercised. The historical
real llama.cpp acceptance of the owner-interaction Module remains valid evidence for
the text-inference connector; the new deep-search review has not been re-exercised
against a real local model in this session.

Kernel SQLite remains restricted to opaque physical bytes, scheduling, attempts,
delivery state, originating Module identity, and accumulated physical values. Semantic
research results and workflow meaning remain Module concerns. The WebSearch behavior
in this slice needs no semantic persistence.

The OpenAI-compatible implementation remains real but unexercised against an external
prepared provider session unless the Owner supplies one. A later external-process
Module must introduce only the concrete local transport required by its exercised
paths; the current WebSearch Module does not justify such transport.
