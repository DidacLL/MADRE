# MADRE

MADRE is personal, owner-sovereign software for coordinating Module applications and
bounded physical reasoning mechanisms while controlling which information can reach
public and physical boundaries. The active implementation is Java 21.

Windows and Linux run the same MADRE application, Kernel, SDK and Module installation
mechanism. There is no Windows compatibility product layered over a Unix implementation
and no Linux-specific public runtime.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md)
  for the public/executable Module model;
- [Physical Execution Contract](docs/architecture/MADRE-execution-contract.md) for the
  current physical execution path;
- [Implementation Baseline](docs/implementation-baseline.md) for executable truth.

## Build and package

Use JDK 21 and the checked-in Gradle wrapper.

Windows:

```text
.\gradlew.bat --no-daemon --build-cache check javadoc publish installDist distZip
.\gradlew.bat --no-daemon --build-cache -p verification/sdk-consumer jar
```

Linux:

```text
./gradlew --no-daemon --build-cache check javadoc publish installDist distZip
./gradlew --no-daemon --build-cache -p verification/sdk-consumer jar
```

Both hosts build the same Java sources and application distribution. Gradle creates
native launch shims for the same `MadreMain` application:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

CI verifies executable changes on both Windows and Linux. Production changes also run
Javadocs, publication, packaging and installed-application smoke. SDK-surface changes
build the independent executable Module fixture, install its JAR into the built MADRE
distribution, discover it and invoke a PUBLIC Operation on both hosts.

No container runtime, VM layer, hosted provider, or external account is required to
build or test the repository.

## Module installation

A MADRE Module is a complete executable application/domain boundary. Its JAR provides
`io.github.didacll.madre.sdk.registration.ModuleProvider` through Java's standard
service-provider resource:

```text
META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

The provider constructs a `ModuleInstance`: one canonical `ModuleDefinition` plus the
exact executable binding for every declared Operation. The runtime discovers Module
JARs from `modules.directory`. Installed distributions default to their sibling
`modules/` directory.

Shipped owner-interaction and WebSearch Modules are packaged into that directory, but
`madre-app` does not import or compile against their concrete classes. An independently
built Module uses the same discovery/registration path; bundling, class-loader
placement and CORE designation confer no privilege.

`verification/sdk-consumer` demonstrates the independent build boundary. It depends
only on:

```text
io.github.didacll:madre-sdk:0.1.0-SNAPSHOT
```

and produces `independent-module.jar`.

## PUBLIC Operation invocation

Runtime callers discover declarations and invoke the exact installed `PUBLIC`
Operation through the SDK `ModuleInvoker`; they do not need the concrete Module class.
A public executable binding must apply a Module-owned semantic result transformer
before Material leaves the public boundary. The result must be new declared Material
and must be minimized enough to reach `Privacy.PUBLIC`; raw internal Material cannot be
returned through this path.

The local console exposes the same boundary generically:

```text
/modules
/invoke <module> <operation> <material-type> <S1..S5> <payload>
```

or non-interactively:

```text
madre <properties> --list-modules
madre <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

The console shortcut currently supports no-effect Operations with Module-owned input
Material types. The typed SDK `ModuleInvoker` is the runtime invocation boundary and is
not limited to this text adapter.

## CORE and connector independence

`roles.core` is optional. CORE is only an ordinary installed Module identity lookup;
it creates no subtype, invocation authority, Security Algebra value, scheduling lane,
or required Operation names. MADRE boots with no CORE configured and also tolerates a
configured CORE that is not installed.

Physical connectors are likewise optional at boot. The platform can start with no
reasoning connector at all. An Operation that actually requires physical inference may
fail when invoked if no mechanism is available, but connector absence is not a startup
error.

The example configuration therefore keeps every connector disabled by default. The
existing llama.cpp AF_UNIX/loopback-HTTP, OpenAI-compatible and SearXNG adapters remain
explicit installation choices.

## Physical connectors

The current text-inference contract is transport-neutral. Local llama.cpp has an
owner-configured AF_UNIX adapter and an explicit loopback-HTTP compatibility adapter;
the repository also contains OpenAI-compatible text inference and SearXNG web-search
adapters. Provider account/session/authentication mechanics remain outside MADRE.

Native Windows acceptance has exercised the AF_UNIX path end to end with a real pinned
`llama-server.exe` and checksum-pinned Qwen model, and separate live acceptance has
exercised SearXNG plus the WebSearch Module. Those prior physical acceptance results
remain recorded in the implementation baseline/PR history; this executable Module
slice does not broaden their generic Capability architecture.

## Start MADRE

Build the distribution, copy/review the example configuration, and run the single Java
application. You may remove `roles.core` and leave every connector disabled for a bare
runtime.

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

The distribution's shipped Module JARs are discovered from its `modules/` directory.
Additional owner-installed Module JARs can be placed in the configured Module
directory without adding an application compile-time dependency.

Physical failures remain physical failures. Kernel SQLite stores only current physical
work, attempt state and retained opaque payloads; semantic interpretation belongs to
the originating Module.

## Architecture recovery boundary

The executable Module work does not make the current generic `Capability` SPI the
final architecture. The remaining known recovery debt is explicit: reasoning must
narrow to `ReasoningCapability`, SearXNG/search placement must be corrected, and the
universal physical-action Kernel path must be removed from ordinary Module/application
behavior. No new generic Memory, Knowledge, Communication, WebSearch, marketplace,
model-management, Docker, MCP, Kubernetes or Linux-only architecture is introduced by
Module installation/discovery.
