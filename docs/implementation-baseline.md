# Implementation baseline

The current pre-R5 recovery foundation is Java 21 and contains six Gradle projects listed in the repository README.

## Semantic execution

`MadreRuntime` owns the live Module registry. It resolves an Operation's actor in this order: explicitly selected Agent, one unambiguous Agent in the owning Module, then the default Agent of the assigned CORE Module. Missing or ambiguous agency fails explicitly. The resolved Agent is the only public path into the `Operation` implementation. A foreign Agent may execute an Operation while the target Module retains its Material, state, and Operation ownership.

Security Algebra remains in the SDK. Runtime and Agents apply semantic meaning and translate only resulting technical inference needs. SearXNG, deterministic computation, HTTP, databases, filesystem work, and other bounded behavior remain ordinary Module Operations and never enter the Kernel merely because they affect the world.

## Inference execution

`InferenceKernel` owns installed engine objects, current availability, technical matching, resource reservations, urgency scheduling, retries, cancellation, and technical measurements. Selection considers inference type, optional exact Owner selection, placement, family-specific capability, latency, availability, resources, and technical preference.

Kernel SQLite state contains technical lifecycle data only. Inference input and output are transient. After restart, pending work becomes `NEEDS_INPUT`; `RuntimeInferenceService` reattaches the exact input from semantic persistence. Runtime durably accepts results before Kernel records delivery. An uncertain delivery is represented honestly as `OUTCOME_UNKNOWN` and reconciled by opaque Work ID.

`LlamaCppEngine` and `OpenAiCompatibleEngine` are optional peers. A valid installation has neither configured.

## Verified behavior

The focused tests cover mandatory Agent execution, agentless Module fallback to CORE, explicit missing/ambiguous failures, foreign Operation execution, Kernel-owned engine matching and resource reservation, technical-only persistence, restart input reattachment, and durable result acknowledgement. The Gradle distribution has been started on Windows with one discovered Module, CORE assigned, and zero engines.

The source of product truth is `MADRE.md`; tests and this baseline are implementation evidence, not substitute requirements.
