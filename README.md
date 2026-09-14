# MADRE

MADRE is personal, owner-sovereign software for coordinating local and external
inference or deterministic mechanisms while controlling which information can reach
each physical boundary. The active implementation is Java 21.

Windows and Linux run the same MADRE application, Kernel, SDK and shipped Modules.
There is no Windows compatibility product layered over a Unix implementation and no
Linux-specific public runtime.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md)
  for the public object model;
- [Physical Execution Contract](docs/architecture/MADRE-execution-contract.md) for execution;
- [Implementation Baseline](docs/implementation-baseline.md) for executable truth.

## Build and package

Use JDK 21 and the checked-in Gradle wrapper.

Windows PowerShell or Command Prompt:

```text
.\gradlew.bat --no-daemon clean check javadoc publish installDist distZip
.\gradlew.bat --no-daemon -p verification/sdk-consumer clean compileJava
```

Linux:

```text
./gradlew --no-daemon clean check javadoc publish installDist distZip
./gradlew --no-daemon -p verification/sdk-consumer clean compileJava
```

Both commands build the same Java sources and produce the same application
distribution. Gradle creates the native launch shims for that one application:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

CI executes the complete build, tests, Javadocs, publication, packaging, independent
SDK-consumer compilation and installed-application smoke path on Windows and Linux.
The hosted Linux runner is CI infrastructure only; MADRE must not depend on
Ubuntu-specific behavior.

No container runtime, VM layer, hosted provider, or external account is required to
build or test the core repository.

## Physical connectors

The text-inference contract is transport-neutral. Connector configuration is explicit;
the example configuration enables no connector automatically.

### llama.cpp Unix-domain socket

MADRE includes a non-TCP llama.cpp Capability using an owner-configured `AF_UNIX`
socket. Despite the historical protocol-family name, this mechanism is usable on both
Windows and Unix-like hosts when the installed llama-server and Java runtime support
`AF_UNIX`. The same MADRE Capability implementation is used on both operating-system
families; no Windows transport fork is involved.

Windows PowerShell example:

```text
New-Item -ItemType Directory -Force C:\madre-runtime | Out-Null
llama-server.exe -m C:\models\model.gguf `
  --alias local-model `
  --host C:\madre-runtime\llama-server.sock
```

When writing that socket path in a Java `.properties` file, forward slashes avoid
backslash escaping:

```text
connector.llamacpp-unix.socket=C:/madre-runtime/llama-server.sock
```

Linux example:

```text
mkdir -p /absolute/path/madre-runtime
chmod 700 /absolute/path/madre-runtime
umask 077
llama-server -m /absolute/path/model.gguf \
  --alias local-model \
  --host /absolute/path/madre-runtime/llama-server.sock
```

Then enable `connector.llamacpp-unix.*` and set its absolute socket path. The adapter
uses llama-server's HTTP framing internally over `AF_UNIX`; it creates no TCP listener.
Keep the socket path short and owner-controlled; socket-path and filesystem access
remain operating-system concerns rather than MADRE algebra values.

Native Windows acceptance has exercised this path end to end with a real pinned
`llama-server.exe` and a checksum-pinned Qwen2.5 0.5B GGUF model. The shipped
owner-interaction Module invoked `standard-prompt`, Kernel selected the actual
`LlamaCppUnixSocketCapability`, llama-server performed real model inference through a
`.sock`, and the Module received and interpreted the generated text. No TCP listener,
Windows-specific MADRE transport, container runtime, or hosted inference provider was
used.

### llama.cpp loopback HTTP compatibility

A loopback-HTTP llama.cpp adapter remains available only as explicit compatibility.
It accepts explicit loopback IP literals and is disabled by default. MADRE does not
silently choose TCP merely because llama-server exposes an HTTP server.

### Other connectors

The repository also contains an OpenAI-compatible text-inference adapter and a SearXNG
web-search adapter. Provider account/session/authentication mechanics remain outside
MADRE. SearXNG and OpenAI-compatible connectors are disabled until deliberately
configured.

## Start MADRE

Copy and review the example configuration, deliberately enable the physical mechanism
you actually intend to use, then start the installation.

Windows PowerShell:

```text
.\gradlew.bat --no-daemon installDist
Copy-Item config\madre.properties.example C:\path\to\madre.properties
.\madre-app\build\install\madre\bin\madre.bat C:\path\to\madre.properties
```

Linux:

```text
./gradlew --no-daemon installDist
cp config/madre.properties.example /absolute/path/madre.properties
madre-app/build/install/madre/bin/madre /absolute/path/madre.properties
```

The shipped application loads the ordinary owner-interaction Module and WebSearch
Module. `roles.core` assigns the owner-interaction Module to the ordinary CORE role.

Current console behavior:

- `/standard <prompt>` performs one immediate bounded inference;
- ordinary text uses the owner-interaction fast lane and may later surface a useful
  background follow-up;
- `/search <S1..S5> <query>` invokes WebSearch `single-search`;
- `/deep-search <S1..S5> <query-1> || <query-2>` runs the researcher's Agent-owned
  two-search-plus-review Workflow;
- `/exit` or `/quit` stops the application.

Physical failures remain physical failures. Kernel SQLite stores only physical work,
attempt state and retained opaque payloads; semantic interpretation belongs to the
originating Module.

## WebSearch

The WebSearch Module provides one `web-research` Skill and one `researcher` Agent.
Its public `single-search` Operation submits a typed `WebSearchCommand` through Kernel
and interprets typed physical search results into Module-owned Material.

The Agent owns `deep-search`: two searches are triggered together, their interpreted
Material is joined with normal Sensitivity composition, and a private review Operation
submits an ordinary `TextInferenceCommand` through Kernel. No special workflow or
security path exists.

Live WebSearch acceptance requires a reachable SearXNG installation with JSON output
enabled. Full live `deep-search` additionally requires a real available
text-inference Capability in the same run.

## Local acceptance

`scripts/acceptance-local.sh` remains a Unix-shell convenience for running a real GGUF
model through the same `AF_UNIX` llama.cpp adapter. It is host-specific acceptance
tooling, not a separate MADRE implementation and not the Windows product path.

Windows real-model AF_UNIX acceptance has already exercised the shipped Module,
Operation, Kernel and Capability path on native Windows. Repeating that journey on the
Owner's particular Windows installation is deployment/hardware qualification rather
than a missing cross-platform MADRE mechanism.

The repository does not bundle llama.cpp, a GGUF model, SearXNG, Docker, or any other
container/orchestration runtime. The Windows real-model acceptance used a temporary,
checksum-verified model fixture and did not add that artifact or its installer to the
normal product build.

## Public SDK

After `publish`, an independent Module can depend on:

```text
io.github.didacll:madre-sdk:0.1.0-SNAPSHOT
```

The SDK contains the algebra, typed Material, Module/Agent/Skill/Workflow/Operation
model, codecs, bounded Operation construction, and Module-facing public ports. It does
not depend on Kernel or either shipped Module. `verification/sdk-consumer` compiles as
an independent consumer of the published artifacts.
