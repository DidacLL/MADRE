# MADRE

MADRE is personal, owner-sovereign software for modular applications that can use bounded reasoning while keeping information reach explicit. The active implementation is Java 21.

Windows and Linux run the same application, Kernel, SDK and Module installation mechanism. There is no Windows compatibility product layered over a Unix implementation and no Linux-specific public runtime.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for composition;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md) for the public/executable Module model;
- [Reasoning Execution Contract](docs/architecture/MADRE-execution-contract.md) for the Kernel reasoning path;
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

Both hosts build the same Java sources and distribution. Gradle creates native launch shims for the same `MadreMain` application:

```text
Windows: madre-app\build\install\madre\bin\madre.bat
Linux:   madre-app/build/install/madre/bin/madre
```

CI verifies executable changes on both Windows and Linux. Production changes additionally run Javadocs, publication, packaging and installed-application smoke. SDK-surface changes build the independent executable Module fixture, install its JAR into the built distribution, discover it and invoke a PUBLIC Operation on both hosts.

No container runtime, VM layer, hosted provider or external account is required for the mandatory build/test path.

## Module installation

A MADRE Module is a complete executable application/domain boundary. Its JAR provides:

```text
io.github.didacll.madre.sdk.registration.ModuleProvider
```

through Java's standard service-provider resource:

```text
META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

The provider constructs a `ModuleInstance`: one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation. Runtime discovery uses `modules.directory`; installed distributions default to their sibling `modules/` directory.

The shipped owner-interaction Module is packaged there but `madre-app` does not compile against its concrete class. Independently built Modules use the same discovery/registration path. Bundling, class-loader placement and CORE designation confer no privilege.

`verification/sdk-consumer` demonstrates the independent boundary. It depends only on:

```text
io.github.didacll:madre-sdk:0.1.0-SNAPSHOT
```

and produces `independent-module.jar`.

## PUBLIC Operation invocation

Runtime callers discover declarations and invoke an exact installed `PUBLIC` Operation through SDK `ModuleInvoker` without depending on the concrete Module class.

A public binding must apply a Module-owned semantic result transformer before Material leaves the public boundary. The result must be new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`; raw internal Material cannot cross this path.

The local console exposes the same generic public boundary:

```text
/modules
/invoke <module> <operation> <material-type> <S1..S5> <payload>
```

or non-interactively:

```text
madre <properties> --list-modules
madre <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

The console shortcut currently supports no-effect Operations with Module-owned input Material types. The typed `ModuleInvoker` is the real runtime boundary.

## CORE and reasoning independence

`roles.core` is optional. CORE is an ordinary installed Module identity lookup; it creates no subtype, invocation authority, Security Algebra value, scheduling lane or required Operation names. MADRE boots with no CORE configured and tolerates a configured CORE that is not installed.

Reasoning mechanisms are also optional at boot. An Operation that later needs reasoning may fail when invoked if no compatible mechanism is available, but mechanism absence is not a startup error.

The example configuration keeps all reasoning connectors disabled by default.

## Reasoning connectors

Kernel execution is reasoning-only. The Module-facing `ReasoningService` accepts only nominal `ReasoningComputation<R>` values through `ReasoningRequest`; it is not a generic action/tool/physical-work dispatcher.

The current typed text-inference contract can be realized by:

- llama.cpp over AF_UNIX;
- explicit llama.cpp loopback HTTP compatibility;
- OpenAI-compatible HTTP.

Selection uses computation-contract compatibility, carried Sensitivity versus explicit receiving Privacy, observed availability, resources and typed location/latency preferences. Operation Risk is not part of reasoning selection.

Provider account/session/authentication mechanics remain outside MADRE.

## Search

Search is ordinary application/domain I/O, not a Kernel reasoning capability.

`madre-web-search` provides reusable typed search values and `madre-adapter-searxng` provides an ordinary Java `SearxngClient`. The SearXNG module has no Kernel dependency and does not implement `ReasoningCapability`.

The former standalone shipped WebSearch Module and generic SearXNG Kernel capability were removed. A future domain Module that genuinely owns research/search behavior may depend on the search library/client directly.

## Start MADRE

Build the distribution, copy/review the example configuration and run the single Java application. You may remove `roles.core` and leave all reasoning connectors disabled for a bare runtime.

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

Additional owner-installed Module JARs can be placed in the configured Module directory without adding an application compile-time dependency.

Reasoning failures remain reasoning-runtime failures. Kernel SQLite stores only durable reasoning work, attempts and retained opaque payloads; semantic interpretation belongs to the originating Module.

## Verified baseline

Executable head `e0f6803e10956f9cb70c0f386592e92f74a1c49f` passed GitHub Actions run `34848775519` on both Windows and Linux, including `check`, Javadocs/publication/package verification, isolated Module build, independent Module installation/PUBLIC invocation and installed-application smoke.

Real llama.cpp and earlier SearXNG live acceptance runs remain historical integration evidence, but the current architecture no longer routes search through Kernel or ships the old WebSearch Module.
