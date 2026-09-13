# Implementation Baseline

The active implementation is a Java 21 Gradle Kotlin DSL multi-project system. It
contains six artifacts at this checkpoint:

- `madre-algebra`, the dependency-free nominal algebra carriers;
- `madre-sdk`, the immutable public Module programming model, version-one JSON
  definition codec, bounded Operation construction and responsibility-specific ports;
- `madre-kernel`, the live Module boundary plus the generic physical Capability SPI,
  deterministic selection, quantified resource coordination, shared immediate and
  durable dispatcher, SQLite physical-work store, recovery/retry/cancellation/result
  lifecycle, explicit Kernel configuration and loopback-only local transport;
- `madre-text-inference`, the first physical command/result contract and its stable
  durable codec;
- `madre-adapter-llamacpp`, a real llama-server health and native-completion protocol
  connector;
- `madre-adapter-openai-compatible`, a real chat-completions connector accepting an
  externally prepared HTTP transport.

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

Capability code receives only its physical command and execution mechanics. Manifests
carry explicit Privacy and optional physical-realizer Integrity; neither locality nor
connector identity supplies those values. Work requests contain no Material identity,
Material type, semantic continuation, or selected Capability. SQLite stores opaque
command/result bytes and physical runtime state only; successful input is removed and
retained output remains collectable across restart until acknowledgement or expiry.

The build defines algebra/SDK/registry invariants, Capability selection and resource
tests, SQLite restart/retry/cancellation/delivery tests, connector protocol tests, an
architecture source check, publication of SDK sources and Javadocs, and an isolated
consumer project that depends only on published `madre-sdk` coordinates. CI provisions
Java 21 and Gradle 8.12 to run those checks.

No shipped CORE-capable Module or installable application exists yet; that is the
remaining master-plan slice. The llama.cpp connector is a real product path, but no
llama-server executable or model is bundled and real-model acceptance has not yet
been exercised at this checkpoint. The external-provider path likewise requires an
available externally prepared connection environment. No removed Python
implementation or fabricated product Module is active.
