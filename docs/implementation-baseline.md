# Implementation Baseline

The active implementation is a Java 21 Gradle Kotlin DSL multi-project system with
the intended artifact boundaries:

- `madre-algebra`, the dependency-free nominal algebra carriers;
- `madre-sdk`, the immutable Module model, versioned definition codec, bounded
  Operation construction and responsibility-specific ports;
- `madre-kernel`, the live Module boundary, physical Capability SPI, deterministic
  selection, resources, immediate/durable dispatcher, SQLite lifecycle,
  configuration and loopback transport;
- `madre-text-inference`, the first physical command/result contract and codec;
- `madre-adapter-llamacpp`, the real llama-server connector;
- `madre-adapter-openai-compatible`, a real chat-completions connector accepting an
  externally prepared HTTP transport;
- `madre-module-owner-interaction`, the shipped ordinary owner-interaction Module;
- `madre-app`, the installable assembly and replaceable local console.

The shipped Module owns owner-prompt, immediate-answer, background-analysis, and
visible-follow-up text Material types. Its public definition declares one interaction
Agent, the two Skills and two Workflows its behavior uses, and ordinary public
standard-prompt and fast-lane Operations with explicit accepted Privacy, promised
output Sensitivity, and inference EffectProfiles. Behavior and prompt text are not
serialized.

Standard prompt creates one immediate `TextInferenceCommand`, submits it through the
public `ExecutionService`, interprets physical text, and creates independent
Module-owned answer Material. Fast lane submits ordinary-priority foreground and
lower-priority durable background requests through that same service. It returns
foreground Material without awaiting background work. The Module later interprets
collected text into background-analysis Material and either creates separate visible
follow-up Material or stops on explicit `NO_FOLLOW_UP`. Model text has no execution
path.

The Module persists pending background associations in its own state file
so a restarted application can collect a durable Kernel result. Kernel's SQLite
database remains restricted to opaque physical bytes, scheduling, attempts, delivery
state, originating Module identity, and accumulated physical values. CORE assignment
resolves the Module's normal identity only after ordinary live registration.

The application supports explicit llama.cpp and optional OpenAI-compatible connector
facts, quantitative resources, CORE assignment, runtime paths and loopback binding.
Its console maps `/standard <prompt>` to standard prompt and ordinary text to fast
lane, surfaces useful background completion independently, and preserves physical
failure categories. The generated distribution contains startup scripts and an
example configuration.

The build covers algebra/SDK/registry invariants, Capability selection/resources,
SQLite recovery/retry/cancellation/delivery, connector protocols, shipped CORE
behavior, application assembly, architecture source checks, SDK sources/Javadocs,
and an isolated SDK-only consumer. The acceptance helper starts an owner-supplied
llama-server and model, builds the distribution, then guides standard, fast-lane,
restart, and SQLite inspection evidence.

No llama-server executable or model is bundled. Real-model acceptance must not be
reported until that exact journey obtains real text through the installed Kernel path
and demonstrates durable background recovery. The OpenAI-compatible implementation
is real, but an externally authenticated provider remains unexercised unless the
Owner supplies its prepared transport/session.
