# MADRE

MADRE is an owner-sovereign modular environment for using local and external
inference or deterministic mechanisms while controlling which information can reach
them. The active implementation is Java 21 and ships an ordinary Module assigned to
the installation's CORE role; neither CORE nor fast lane has a privileged Kernel
path.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md)
  for the public object model;
- [Physical Execution Contract](docs/architecture/MADRE-execution-contract.md) for execution;
- [Master Development Plan](docs/master-development-plan.md) for the release journey.

## Build and package

Use JDK 21 and Gradle 8.12 or newer:

```text
gradle --no-daemon clean check javadoc publish installDist distZip
gradle --no-daemon -p verification/sdk-consumer clean compileJava
```

This creates the installation under `madre-app/build/install/madre`, the distribution
ZIP under `madre-app/build/distributions`, and binary, sources, and Javadoc SDK
artifacts. `publish` writes `madre-algebra` and `madre-sdk` to
`build/isolated-repository`; the independent consumer compiles only from those
published coordinates.

## Start the local installation

Start a separately installed llama.cpp server with an owner-selected GGUF model:

```text
llama-server -m /absolute/path/model.gguf --host 127.0.0.1 --port 8080
```

Build the installation, copy and review its configuration, then start MADRE:

```text
gradle --no-daemon installDist
cp config/madre.properties.example /absolute/path/madre.properties
madre-app/build/install/madre/bin/madre /absolute/path/madre.properties
```

Review `connector.llamacpp.privacy`, `connector.llamacpp.integrity`, resource
capacity/claims, endpoint, and model alias before starting. These are explicit
installation facts; locality supplies none of them. Use absolute runtime paths when
the application may restart from another directory.

At the console:

- `/standard <prompt>` performs one immediate bounded inference;
- ordinary text starts fast lane, returns its foreground answer without waiting for
  durable background analysis, and later prints a useful `background>` follow-up;
- `/exit`, `/quit`, or Ctrl-C stops the application. Restart it with the same
  properties file to recover physical work and CORE's pending background associations.

Physical failures are printed as physical failures rather than model dialogue. The
Kernel SQLite database contains only physical work, attempt telemetry, and retained
opaque payloads. CORE's separate state file is Module-owned and retains pending work
identities and applicable result Sensitivity.

## Real local acceptance

The helper starts a real llama-server, runs automated checks, builds the distribution,
and opens the real console journey:

```text
scripts/acceptance-local.sh \
  /absolute/path/llama-server \
  /absolute/path/model.gguf \
  /absolute/path/madre-acceptance
```

Set `GRADLE_COMMAND` if Gradle has a non-default name. Follow the printed standard,
fast-lane, restart, and SQLite inspection steps and retain the server log plus exact
model and llama.cpp version with release evidence. This repository does not bundle a
model or llama-server, and a protocol fixture is not real-model acceptance.

The optional OpenAI-compatible connector uses
`connector.openai-compatible.*` properties. The application supports an
unauthenticated endpoint. Provider account/session mechanics remain outside MADRE;
an authenticated provider requires an externally prepared transport and must be
recorded as unexercised unless that environment is actually supplied.

## Use the public SDK

After `publish`, a new Module project can depend on:

```text
io.github.didacll:madre-sdk:0.1.0-SNAPSHOT
```

The SDK contains algebra, typed Material, declarative Module definitions, codecs,
bounded Operation construction, and Module-facing physical/live invocation ports. It
has no dependency on Kernel or the shipped CORE Module. See
`verification/sdk-consumer` for the independently compiled example.
