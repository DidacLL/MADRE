# MADRE

MADRE is an owner-sovereign modular environment for using local and external
inference or deterministic mechanisms while controlling which information can reach
them.

The repository is being rebuilt in Java 21 from its intentional clean architecture
checkpoint. The public algebra and Module SDK foundation now coexist with Kernel's
in-memory live Module boundary. There is not yet a runnable MADRE distribution, a
physical Capability runtime, or a test Module standing in for one.

Start with:

- [MADRE.md](MADRE.md) for product meaning;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) for the standalone
  composition model;
- [Platform Architecture](docs/architecture/MADRE-platform-architecture.md) for
  responsibility boundaries;
- [Module SDK and Interoperability](docs/architecture/MADRE-agent-interoperability.md)
  for the public object model;
- [Physical Execution Contract](docs/architecture/MADRE-execution-contract.md) for
  Module-to-Kernel-to-Capability execution;
- [Master Development Plan](docs/master-development-plan.md) for the complete Java 21
  implementation and real local acceptance path.

## Build the current foundation

The build requires JDK 21 and Gradle 8.12 or newer:

```text
gradle clean check javadoc publish
gradle --no-daemon -p verification/sdk-consumer clean compileJava
```

The first command tests the algebra, SDK and live registry, runs the active
architecture checks, creates source/Javadoc artifacts, and publishes
`io.github.didacll:madre-algebra` and `io.github.didacll:madre-sdk` to the isolated
repository under `build/isolated-repository`. The second command proves a fresh
consumer can compile from those published coordinates without Kernel or CORE.

The next usable release must add the complete physical Kernel runtime, llama.cpp and
OpenAI-compatible text-inference connectors, and the shipped ordinary Module assigned
to CORE with standard-prompt and fast-lane Operations.
