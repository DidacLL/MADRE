# Implementation Baseline

The active implementation is a Java 21 Gradle Kotlin DSL multi-project system with
the intended artifact boundaries:

- `madre-algebra`, the dependency-free nominal algebra carriers;
- `madre-sdk`, the immutable Module model, versioned definition codec, bounded
  Operation construction and responsibility-specific ports;
- `madre-kernel`, the live Module boundary, physical Capability SPI, deterministic
  selection, resources, immediate/durable dispatcher, SQLite lifecycle,
  configuration and in-process public ports;
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

Standard prompt derives one immediate work request from its valid Operation call,
submits its `TextInferenceCommand` through the public `ExecutionService`, interprets
physical text, and creates independent
Module-owned answer Material. Fast lane submits ordinary-priority foreground and
lower-priority durable background requests through that same service. It returns
foreground Material without awaiting background work. The Module later interprets
collected text into background-analysis Material and either creates separate visible
follow-up Material or stops on explicit `NO_FOLLOW_UP`. Model text has no execution
path. Public work construction cannot independently replace the originating Module,
carried Sensitivity, or EffectProfile Risk, and Operation invocation keeps returned
Material within its declared output contract.

The Module persists pending background associations in its own state file
so a restarted application can collect a durable Kernel result. Kernel's SQLite
database remains restricted to opaque physical bytes, scheduling, attempts, delivery
state, originating Module identity, and accumulated physical values. CORE assignment
resolves the Module's normal identity only after ordinary live registration.

The application supports explicit llama.cpp and optional OpenAI-compatible connector
facts, quantitative resources, CORE assignment, and runtime paths.
Its console maps `/standard <prompt>` to standard prompt and ordinary text to fast
lane, surfaces useful background completion independently, and preserves physical
failure categories. The generated distribution contains startup scripts and an
example configuration.

The build covers algebra/SDK/registry invariants, Capability selection/resources,
SQLite recovery/retry/cancellation/delivery, connector protocols, shipped
owner-interaction behavior, application assembly, architecture source checks, SDK
sources/Javadocs, and an isolated SDK-only consumer. The acceptance helper starts an owner-supplied
llama-server and model, builds the distribution, then guides standard, fast-lane,
restart, and SQLite inspection evidence.

No llama-server executable or model is bundled. On 2026-09-13 the installed
distribution was exercised against llama.cpp build `10016 (32b741c33)` and
`qwen2.5-3b-instruct-q4_k_m.gguf`. The real run produced a standard response, returned
fast-lane foreground text before durable background completion, interrupted an active
background attempt, restarted the application, retried the physical work, and
delivered the Module-interpreted background result. Repeating the run after changing
the local adapter from raw completion to chat completion produced clean
instruction-following text and completed a no-follow-up background path with empty
Module pending state. After work-request construction was made derivable only from a
valid Operation call, the exact built distribution again returned `FINAL_SDK_OK`
through standard prompt and completed fast lane with empty pending Module state. The
OpenAI-compatible implementation is real, but an externally
prepared provider session remains unexercised unless the Owner supplies it.

The first useful installation is one process and injects the SDK ports directly into
the owner-interaction Module. No socket transport is currently implemented. A second
real Module running outside that process will require a concrete local transport.
The current application assembly installs only the owner-interaction Module and maps
CORE to its ordinary identity. Loading a replacement Module and verifying the
minimum public behavior required by CORE are not implemented yet.
