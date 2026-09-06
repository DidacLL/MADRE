# MADRE implementation plan

This plan sequences the full product described in [MADRE-AgenticSystem.tex](tex/MADRE-AgenticSystem.tex). It does not replace that specification or discard its research questions, domain concepts or requirements. The development process is small; the product retains its intended depth. CLI demonstrations are access surfaces, not the definition of MADRE.

The product remains a local-first, model-agnostic runtime for applications: module-owned agents, foreground continuity, durable delayed reasoning, governed context, bounded actions, knowledge with provenance, evaluated learning, and inspectable recovery. Software owns authority. A conceptual boundary need not become a framework, service or class hierarchy before an actual behavior needs it.

Use this as an ordered build sequence. Determine progress from code and executable examples; do not maintain a parallel status file. The discarded prototype and its tests are not inputs to implementation. Start from an empty codebase and these product behaviors.

## 1. Real inference through the governed local boundary

**Deliver:** A developer can submit text to an explicitly configured local model through MADRE's Python API and CLI and inspect generated output or a useful failure. Keep one explicitly owned module/agent and one allowed model binding; no registry service or agent topology. The kernel owns lifecycle and the module requests inference.

Start with one local OpenAI-compatible HTTP adapter, using the developer's running server and model. Configuration supplies base URL, model and timeout. Use non-streaming text first. Permit loopback destinations only for this first adapter; reject remote destinations, redirects and environment-proxy routing. No mandatory cloud credentials, automatic model downloads or provider discovery. Record the actual inference configuration and outcome; never interpret output as commands or permissions. Standard-library HTTP is sufficient unless implementation exposes a concrete limitation. [Ollama documents this local protocol](https://docs.ollama.com/api/openai-compatibility); using it does not bind MADRE to that server.

**Usable result:** CLI and Python examples return actual model output when a local server is available. Connection failure, timeout and invalid responses give actionable errors; data cannot silently leave the configured local boundary. Report any unavailable real-model run honestly; do not require Astra/Terra. Add focused automated checks only as part of making this working path reliable.

**Product reference:** Canonical Terms; ModelAction inference diagram; FR-004, AI-001/002, NFR-001, SEC-003.

## 2. First useful delayed-reasoning application path

**Deliver:** A developer supplies a question and explicit local text sources. MADRE acknowledges immediately, retains the task, performs a bounded source-backed analysis later using the real adapter, and exposes the result with source references and lifecycle evidence. A stopped/restarted process can continue pending work. This is the first usable milestone; it is not the whole product.

Use a small request-derived plan with explicit steps to select supplied context, infer and verify the result format/source references. Persist enough step/attempt state to resume without silently repeating completed steps. The foreground reports receipt, clarification or known information; it must not pretend unfinished analysis has already happened. Begin with one module and its owned agent. Model-produced plans are proposals validated by software, not executable authority.

Use local SQLite if suitable. Keep immediate and delayed scheduling, due-time discipline, bounded attempts/time, explicit failures and a foreground worker/watch command. Recover interrupted attempts visibly; retry only under an explicit rule. Provide withdrawal of pending work as part of user control. Do not add a service manager, distributed queue or general workflow DSL.

**Usable result:** A real-model source-backed request survives restart and produces an inspectable result. Due times, interrupted steps, withdrawal, resource limits and source references behave as documented in that same usage path. Generation may repeat after interruption; never claim exactly-once effects without evidence. A result's correctness is not established merely by fluent model text.

**Product reference:** Core User Value; Concurrent Agent Lanes; FR-001/002/007/008, NFR-003, UX-001, D-003/004/008/010.

## 3. Governed context, module isolation and bounded actions

**Deliver:** Extend that same useful path so context is selected, minimized, scoped and revocable, and one typed internal action can run only after an explicit policy decision. Add a second module only to demonstrate a real ownership/isolation case. Default interaction and module-main-agent remain roles, not duplicated subsystems.

Start with application-supplied text/source descriptors and a pure or scoped read-only action. Record source provenance, sensitivity and intended use. Enforce allow, block and authorization-required before invocation. A source document or generated instruction cannot grant access to other modules, files, commands or services. Revoke context before reuse. Preserve small records/functions where sufficient; do not generate every conceptual agent profile as a hierarchy.

**Usable result:** The useful example invokes the allowed action; denied actions make zero calls. Unrelated, revoked and sensitive context is excluded; cross-module access and injected instructions fail. External or destructive effects remain unavailable until separately implemented with authorization and recovery.

**Product reference:** Data And Authority Rules; Policy Matrix; FR-003/005, SEC-001/002/003, D-005/007/009.

## 4. Useful retained knowledge and explicit correction

**Deliver:** The application can retain appropriate material, retrieve it for the same task, correct or invalidate it, and see why an earlier result relied on it. Keep conversation, generated output, known material and pending knowledge changes distinct. Attach provenance, scope, sensitivity, intended use and truth metadata; storage does not confer factual authority.

Use the simplest retrieval that fits the first real corpus. Add lexical search, embeddings, RAG or a vector store only when a concrete retrieval experiment requires them. Provide correction, supersession and deletion/detachment of active material without conflating retained audit events with permission to retain all private payloads.

**Usable result:** Corrected or revoked material stops entering new context; earlier results expose their provenance and invalidation. Retaining conversation/output cannot silently create trusted knowledge or change behavior.

**Product reference:** Knowledge boundaries and glossary; FR-008, DATA-001/002, D-008/011.

## 5. Evaluated learning and module behavior evolution

**Deliver:** A module can propose one concrete improvement, compare it against a baseline on representative cases, and promote or reject it under a software-owned rule with rollback. Begin with a prompt, routing or workflow improvement; no weight training is required to demonstrate the learning boundary.

Keep LearningCandidate separate from KnowledgeCandidate. Proposals remain inactive until evaluation and authorized promotion. Routine developer-defined promotion need not interrupt the user; sensitive or high-impact changes still require the relevant authority. Preserve a rollback path. Fine-tuning and weight changes remain advanced actions with stronger authority.

**Usable result:** Failed or unapproved candidates cannot change active behavior; promotion improves the selected measure without breaking boundary cases; rollback restores the prior behavior. Use a small reproducible experiment, not a benchmarking platform.

**Product reference:** Learning Lifecycle/Controlled Learning; FR-006, AI-002, SEC-002/003, D-011; research questions RQ-1 through RQ-5 and hypotheses H-1 through H-5 as evaluation questions, not assumed results.

## 6. Applications, interfaces and research expansion

**Deliver:** Use the runtime in an actual domain application and extend only for its demonstrated requirements. Preserve a small Python/application boundary and a read-first inspection surface. GUI, speech, additional local or authorized remote inference, reusable workflows, richer agent profiles and multi-module collaboration remain product/application possibilities; they are not deleted by the earlier milestones.

Remote transfer needs explicit classification, minimization, authorization and trace. Higher-risk actions need suitable recovery. Add metrics for the hypothesis being tested: latency/cost versus delayed-result quality, context isolation, substitution, correction or learning. Use ordinary measurement tools only for the question being investigated. Distributed execution, model adaptation and organization-scale deployment require their own concrete use case, not preliminary infrastructure.

**Usable result:** A real application can use the capabilities without bypassing context, policy, audit or recovery, and the selected research claim has reproducible measurements. This milestone expands the usable product; it does not retroactively make every optional feature a prerequisite for starting.

**Product reference:** Access Surfaces; Quality Model; Benchmark Families; Deployment And Operational Assumptions; Risks, Limitations, And Excluded Functions.

## Working through the sequence with Classic

Implement one cohesive behavior from the earliest unfinished milestone per task. Classic may make ordinary design choices and finish commits/PR/integration within the task; no special coordinator or more expensive executor is required. Follow the relevant product references when meaning is uncertain. Deliver runnable examples and verify the behavior being implemented; avoid separate verification/reporting workstreams. Do not rewrite this plan after each commit.

The first assignment is milestone 1: implement configured real local inference from scratch, with no dependency on the removed prototype or tests. No separate architecture session or cancellation-only prerequisite is needed.
