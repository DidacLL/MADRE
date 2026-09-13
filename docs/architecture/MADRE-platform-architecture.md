# MADRE Platform Architecture

## Responsibility map

```text
Owner
  |
  v
ordinary Module assigned to CORE role or another Module
  | owns Material, meaning, Agents, Operations and continuation
  | Agents own Workflows over their Operation repertoires
  | creates an opaque physical work request
  v
Kernel
  | live Module and Capability registries
  | selection, routing, resources, scheduling, queue and delivery
  v
Capability adapter
  | invokes one installed physical mechanism
  v
physical output
  | delivered opaquely
  v
originating Module
  | interprets output, creates new Material and continues or stops
```

The algebra is behavior of the values carried by these objects. It is not a runtime
component in the diagram.

## Module boundary

A Module is the semantic and application boundary. It owns:

- its domain state and persistence;
- Material identities, types, payloads and Sensitivity;
- Agents and the Skills it provides;
- public and private Operations;
- conversion from Material to physical connector input;
- interpretation of physical output;
- creation of new Material;
- continuation and user presentation.

Each Agent owns the Workflows in its learned repertoire. A Module-provided Skill may
materialize as one or more Workflows for an Agent. The current minimal Workflow is an
ordered sequence of Operations triggered together as one semantic behavior. That is
Module/Agent behavior, not a Kernel workflow language.

An Operation is the bounded entry to Module behavior. It declares the Material it can
accept, the receiving Privacy that applies, the Material it may produce, and its
EffectProfiles when it has consequential variants. Those declarations are direct
parts of the Operation; they are not separately identified surface objects.

Module behavior calls the SDK directly while composing its actual values. Correct
construction is the implementation model, not a voluntary call to a separate
service. When a Workflow invokes multiple Operations, every Operation composes from
the actual Material entering that Operation and every resulting physical request is
selected independently by Kernel.

The WebSearch Module is the first concrete stress case. Its researcher Agent owns a
`deep-search` Workflow whose minimal sequence is two `single-search` Operation
invocations followed by one private review Operation. The two searches independently
cross the web-search physical boundary; their Module-interpreted Material is then
joined and the review independently crosses the text-inference boundary. The Workflow
has no combined security state and no privileged execution path.

## Kernel boundary

Kernel owns mechanisms shared across Modules:

- an in-memory registry of running Module definitions;
- an installation registry of physical Capability manifests and adapter bindings;
- resolution of the configured CORE Module identity;
- Capability selection from request values, installed manifest values, current
  availability, resources, and deterministic configuration;
- immediate and durable physical work;
- timing, priority, timeout, cancellation, resource coordination, and retry after
  physical failure;
- opaque result delivery;
- ordinary runtime logging.

Kernel does not own Module definitions after restart, Module state, Material,
semantic workflows, or Operation behavior. It does not create or classify output.

The live Module registry makes exact public Agents and Operations reachable to other
Modules. Kernel does not interpret them. Invocation enters the target Module's
ordinary bounded Operation interface.

## Capability boundary

A Capability is a connector registered in Kernel. Its manifest describes only the
installed mechanism:

- Kernel identity;
- physical command/input and output contract;
- explicit receiving Privacy;
- Integrity where the connector is an actual physical realizer;
- availability and physical properties;
- required resources;
- connector configuration and supported bounded options.

The adapter accepts the physical command and returns physical output. It contains no
Material, Module, Agent, Workflow, Skill, or Operation reference.

Capability availability is an observed physical fact, not a favorable default. An
adapter may report `AVAILABLE` only after its own mechanism-specific reachability
check establishes that state. A failed check reports `UNAVAILABLE`; an interrupted or
otherwise unestablished check is `UNKNOWN`. Kernel selects only explicitly
`AVAILABLE` Capabilities. Test fixtures may inject explicit states to exercise Kernel
logic, but production adapters must not hardcode success merely to keep execution
moving.

MADRE currently has two typed physical contracts:

- text inference: llama.cpp and OpenAI-compatible adapters accept
  `TextInferenceCommand` and return `TextInferenceResult`;
- web search: the SearXNG adapter accepts `WebSearchCommand` and returns
  `WebSearchResult`.

SearXNG HTTP/JSON details stay inside the adapter. The WebSearch Module sees only the
typed physical search contract and interprets its returned hits into Module-owned
research Material. Search endpoint, Privacy, Integrity, latency and resource claims
are installation facts. Locality or provider identity never derives algebraic values.

A physical contract does not imply one transport. Multiple adapters may implement the
same contract through different physical mechanisms and coexist in one installation.
Same-host llama.cpp now has both a Unix-domain-socket adapter and a loopback-HTTP
compatibility adapter under the same text-inference contract. Kernel sees ordinary
Capabilities and selects using their manifests and request values; it has no concept of
llama.cpp, HTTP, AF_UNIX, model-server routes, or native APIs. The Unix adapter keeps
the server in a separate process without creating a TCP listener; the HTTP adapter is
retained for platforms/environments where that compatibility route is needed.

Third-party libraries and protocols constrain only their adapter implementation. They
must not redefine MADRE objects or move their transport/session/lifecycle concepts into
Kernel, SDK, Modules, Agents, Workflows, Operations, Material, or the Security Algebra.
If a future direct `libllama` adapter is implemented, its native ABI and model lifetime
remain private physical mechanics behind the same contract unless a genuinely new
MADRE responsibility proves otherwise.

New physical contracts are introduced only when a real connector requires different
physical command/result semantics; they extend the Capability SPI rather than expanding
one generic dictionary.

## CORE installation role

Installation configuration assigns `CORE` to one ordinary `ModuleId`. Kernel
resolves that identity in the live registry. The surrounding application sends
default owner interaction to that Module.

A CORE candidate must expose the ordinary public behavior required by the role. The
current minimum qualification is the public `standard-prompt` and `fast-lane`
Operations exposed through one Agent, matching the existing product contract. The
shipped owner-interaction Module qualifies. The WebSearch Module does not. No CORE
subtype, privileged path, special algebra, or Kernel scheduling lane exists.

A CORE interaction Agent may detect that another installed Agent/Operation/Workflow is
better suited to an owner request, or the owner may choose that behavior directly in
a UI. That trigger is user-experience behavior. It does not make the target Workflow
a CORE or Kernel concept. The current console directly exposes WebSearch operations as
the first exercised UI path; automatic conversational routing is not implied by this
slice.

## Storage

Module persistence stays inside each Module.

Kernel persists only physical work that must survive restart:

- opaque queued input;
- scheduling and attempt state;
- pending physical output until delivery.

Runtime cleanup removes payload bytes according to delivery and retention. The live
Module registry is rebuilt when Modules start.

## Provider environment

MADRE configuration describes connector endpoints and physical behavior. Account
sessions, credentials, authentication flows, authorization, permissions, and provider
roles are supplied outside the MADRE model. They do not alter Capability Privacy or
Integrity.
