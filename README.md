# MADRE

MADRE is an owner-sovereign modular environment for using local and external
inference or deterministic mechanisms while controlling which information can reach
them. The active implementation is Java 21 and loads ordinary owner-interaction and
WebSearch Modules; the owner-interaction Module is assigned to CORE by default.
Neither CORE, workflows, nor fast lane has a privileged Kernel path.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md)
  for the public object model;
- [Physical Execution Contract](docs/architecture/MADRE-execution-contract.md) for execution;
- [Implementation Baseline](docs/implementation-baseline.md) for current executable behavior.

## Build and package

Use JDK 21. The checked-in Gradle wrapper supplies the build tool:

```text
./gradlew --no-daemon clean check javadoc publish installDist distZip
./gradlew --no-daemon -p verification/sdk-consumer clean compileJava
```

This creates the installation under `madre-app/build/install/madre`, the distribution
ZIP under `madre-app/build/distributions`, and binary, sources, and Javadoc SDK
artifacts. `publish` writes `madre-algebra` and `madre-sdk` to
`build/isolated-repository`; the independent consumer compiles only from those
published coordinates.

## Start local inference

The text-inference physical contract is transport-neutral. For same-host llama.cpp,
MADRE now has two adapters which can coexist under that same contract.

### Preferred: Unix-domain socket

On a system where both llama-server and the Java runtime support Unix-domain sockets,
create an owner-only runtime directory and start llama-server on an absolute `.sock`
path:

```text
mkdir -p /absolute/path/madre-runtime
chmod 700 /absolute/path/madre-runtime
umask 077
llama-server -m /absolute/path/model.gguf \
  --alias local-model \
  --host /absolute/path/madre-runtime/llama-server.sock
```

Then enable `connector.llamacpp-unix.*` in the installation properties, set its socket
path to that exact absolute path, and normally disable the HTTP compatibility adapter.
The Unix-socket Capability still speaks llama-server's physical HTTP protocol inside
the adapter, but it creates no TCP/IP listener. Filesystem/socket access remains an
operating-system concern; MADRE does not derive Privacy or Integrity from locality.

### Compatibility: loopback HTTP

The existing compatibility adapter uses llama-server over TCP loopback:

```text
llama-server -m /absolute/path/model.gguf \
  --alias local-model \
  --host 127.0.0.1 --port 8080
```

That adapter deliberately accepts only explicit loopback IP literals. Keep the server
bound to loopback as well. A client endpoint cannot prove how an externally started
server was bound, so loopback HTTP is retained for compatibility rather than treated
as the preferred local-security boundary.

Both adapters accept `TextInferenceCommand` and return `TextInferenceResult`; neither
Kernel nor a Module knows which llama.cpp transport realized the work. Installation
preference selects among otherwise reachable available Capabilities. Third-party
transport details do not change MADRE's public architecture.

## Start the installation

For live web search, configure an installed SearXNG instance whose JSON search format
is enabled. The example configuration expects a search endpoint such as:

```text
http://127.0.0.1:8888/search
```

Build the installation, copy and review its configuration, then start MADRE:

```text
./gradlew --no-daemon installDist
cp config/madre.properties.example /absolute/path/madre.properties
madre-app/build/install/madre/bin/madre /absolute/path/madre.properties
```

Review every connector's explicit Privacy, Integrity, resource claims, endpoint or
socket path, and other physical facts before starting. Locality or provider identity
supplies none of those algebraic values.

At the console:

- `/standard <prompt>` performs one immediate bounded inference;
- ordinary text starts fast lane and can later print a useful `background>` follow-up;
- `/search <S1..S5> <query>` invokes the WebSearch researcher's public
  `single-search` Operation;
- `/deep-search <S1..S5> <query-1> || <query-2>` runs the researcher's Agent-owned
  `deep-search` Workflow: two searches followed by one inference review;
- `/exit`, `/quit`, or Ctrl-C stops the application.

The explicit `S1..S5` on the first WebSearch console paths is the Sensitivity of the
query Material being created. Kernel only selects a search Capability when that
Sensitivity can reach the connector's configured receiving Privacy. For example, an
S3 query cannot reach a P2 (`UNKNOWN`) web-search boundary. MADRE does not lower the
value to make the request succeed; the Module would need to create genuinely
transformed lower-sensitivity Material or use another reachable Capability.

Physical failures are printed as physical failures rather than dialogue. Kernel
SQLite contains only physical work, attempt telemetry and retained opaque payloads.
Semantic search results, reviews and workflow meaning belong to the WebSearch Module.

## WebSearch Module

The installed WebSearch Module provides one `web-research` Skill and one `researcher`
Agent. The Agent exposes one public Operation, `single-search`, and owns one Workflow,
`deep-search`. The minimal Workflow is an ordered Operation sequence, not a Kernel
workflow engine.

`single-search` sends a typed `WebSearchCommand` through Kernel. The SearXNG adapter
alone knows the HTTP/JSON protocol and returns a typed physical `WebSearchResult`.
The Module interprets those physical hits into Module-owned research Material.

The SearXNG adapter probes `/healthz` before Kernel can select it. A failed health
probe is unavailable; an interrupted/unestablished probe is unknown. Neither state is
selectable. No production adapter is allowed to claim availability merely to let a
workflow continue.

`deep-search` triggers two `single-search` Operations, joins their interpreted Material
with normal Sensitivity composition, then invokes a private review Operation. That
review sends an ordinary `TextInferenceCommand` through Kernel. Web search and review
therefore cross two independently selected physical boundaries and each crossing is
subject to the same Security Algebra.

The current console exposes direct UI invocation. Automatic conversational detection
by CORE of when research is needed is a later UX behavior, not a hidden feature of
Kernel or the Workflow definition.

Live WebSearch acceptance remains separate from deterministic protocol tests. It can
continue when a reachable SearXNG installation with JSON output enabled is available.
Full `deep-search` acceptance additionally requires a real available text-inference
Capability in the same run.

## Real local acceptance

The Unix acceptance helper starts an owner-supplied llama-server on an owner-only Unix
socket, builds the distribution, and opens the actual MADRE console:

```text
scripts/acceptance-local.sh \
  /absolute/path/llama-server \
  /absolute/path/model.gguf \
  /absolute/path/madre-acceptance
```

The helper requires `curl` with `--unix-socket` support and a platform where
llama-server and Java 21 support AF_UNIX. It configures the installed application to
use the Unix-socket Capability rather than the HTTP fallback. This repository does not
bundle llama.cpp or a model. Running deterministic fixture tests is not real-model
acceptance.

## CORE qualification

`roles.core` is still only an ordinary Module identity. After live registration the
application resolves that identity and verifies the existing minimum public behavior:
one Agent exposes public `standard-prompt` and `fast-lane` Operations. The shipped
owner-interaction Module qualifies. WebSearch deliberately does not. There is no CORE
class, subtype, privilege, lane or alternate execution path.

## Provider environments

The optional OpenAI-compatible connector and SearXNG connector use installation
configuration for physical behavior. Provider account/session/authentication mechanics
remain outside MADRE.

This repository does not bundle llama.cpp, a model, or SearXNG. Deterministic tests use
fixtures to exercise protocols and failure mechanics. Claims about a live external
search require an actually prepared SearXNG environment; none is implied by fixture
tests.

## Use the public SDK

After `publish`, a new Module project can depend on:

```text
io.github.didacll:madre-sdk:0.1.0-SNAPSHOT
```

The SDK contains algebra, typed Material, declarative Module and Agent-owned Workflow
definitions, codecs, bounded Operation construction, and Module-facing physical and
directory ports. It has no dependency on Kernel or either shipped Module. See
`verification/sdk-consumer` for the independently compiled example.
