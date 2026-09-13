# Implementation Baseline

The active implementation is a Java 21 Gradle Kotlin DSL multi-project foundation.
It contains three artifacts:

- `madre-algebra`, the dependency-free nominal algebra carriers;
- `madre-sdk`, the immutable public Module programming model, version-one JSON
  definition codec, bounded Operation construction and responsibility-specific ports;
- `madre-kernel`, currently limited to the in-memory live Module registry,
  information-reach directory, invocation routing and resolution of the configured
  CORE identity as an ordinary registered Module.

The SDK owns nominal Module-domain identities, typed Material and MaterialType,
canonical Module/Agent/Skill/Workflow/Operation/EffectProfile declarations, and the
physical WorkRequest accepted by the Module-facing ExecutionService. A WorkRequest
contains a physical command, accumulated applicable values and ordinary Kernel
controls; it cannot contain Material or a selected Capability.

Definitions reject unresolved ownership and repertoire references. JSON decoding is
explicitly versioned, rejects unknown or missing fields, resolves typed Material
declarations through a caller-supplied resolver, and never serializes executable
behavior. Consequential Operation construction directly enforces information reach,
non-user causal demand and nonempty physical-realizer support.

The build defines invariant tests, an architecture source check, publication of SDK
sources and Javadocs, and an isolated consumer project that depends only on published
`madre-sdk` coordinates. CI provisions Java 21 and Gradle 8.12 to run those checks.

No Capability SPI, physical runtime, persistence, connector, shipped CORE-capable
Module or installable application exists yet; those are the next two master-plan
slices. No removed Python implementation or fabricated product Module is active.
