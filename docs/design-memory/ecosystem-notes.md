# Ecosystem Notes

Authority: **non-authoritative external research**. These projects are comparison material only. MADRE architecture must remain derivable from its own product requirements.

Reviewed September 2026.

## Engrim

Source: <https://github.com/timgordontg/engrim>

Engrim is a local-first, project-scoped cross-agent/cross-model episodic memory system. Its useful observation for MADRE development is that project decisions can survive model/session changes while only a small curated memory pack is reloaded into a fresh agent context.

Worth remembering:

- project knowledge should not depend on one model/vendor/session;
- retrieval should be bounded to the current task rather than replaying entire histories;
- explicit durable decisions/constraints can reduce repeated explanation;
- cross-agent adapters can sit around an independent knowledge store.

Do not infer from this that MADRE Kernel should own generic Agent/Module memory. Semantic memory remains Module-owned. The relevant lesson is for development/design-memory retrieval and potentially future Module tooling.

## Ripwire

Source: <https://github.com/redhat-et/ripwire>

Ripwire gives coding agents a deterministic repository map before they read large source trees, with explicit attention to token cost, blast radius, tests and disclosed uncertainty/loss.

Worth remembering:

- deterministic structural tooling can reduce how much model context is required;
- give agents a small map/index before broad file ingestion;
- expose uncertainty/truncation rather than pretending a compressed view is complete;
- cheap deterministic inspection should precede expensive model reasoning when possible;
- a context tool can be useful through simple CLI interfaces without becoming a permanent agent framework.

This is relevant to MADRE development tooling and a possible future `MADREDeveloper` Module, not Kernel product semantics.

## OKF Agent Memory

Source: <https://github.com/okf-memory/okf-agent-memory>

OKF Agent Memory stores inspectable Git-native Markdown knowledge and uses progressive disclosure plus search-before-write to reduce duplication and context bloat.

Worth remembering for the MADRE harness/design-memory corpus:

- keep durable design knowledge human-inspectable and version-controlled;
- load only the relevant topic/index path;
- search existing memory before writing a parallel formulation;
- separate normative specification from agent-behavior conventions and tooling;
- deterministic local lexical search can be enough before adding embedding/vector infrastructure.

MADRE does not need to adopt the OKF schema or its memory ontology. The useful ideas are progressive disclosure, inspectability and duplication control.

## OpenWhispr voice/audio ecosystem audit

Sources: <https://openwhispr.com/> and <https://github.com/OpenWhispr/openwhispr>

A dedicated audit is preserved in [`openwhispr-voice-ecosystem-audit.md`](openwhispr-voice-ecosystem-audit.md). It is reference evidence for future voice/audio work, not an adoption decision.

Worth remembering:

- OpenWhispr is mainly useful as a reference implementation and failure corpus rather than as a MADRE dependency;
- its complete desktop product mixes Module-level interaction UX, cross-platform audio/device plumbing, concrete speech runtimes and unrelated notes/meetings/agent/search/cloud functionality;
- the reusable physical mechanisms are usually better integrated directly from upstream projects such as OS-native speech services, whisper.cpp, sherpa-onnx, Vosk or PocketSphinx;
- microphone capture, VAD, ASR, TTS, playback, system audio and diarization should remain independently selectable computations rather than becoming one provider-bound "voice" mechanism;
- runtime Capability truth matters: installed/configured/available/selected/actually executing are different states, especially when native helpers, permissions, assets and fallbacks are involved;
- legacy/native low-resource mechanisms remain strategically useful on constrained machines and are evidence against equating Capability with modern ML model execution;
- OpenWhispr's platform-helper techniques, streaming finalization, event-driven monitoring and failure handling are useful implementation references without importing its application architecture.

Do not infer from this audit that MADRE should adopt OpenWhispr, make it a Module, expose OpenAI-shaped speech APIs, or pre-build a general voice framework. Any architectural refinement identified by the audit remains non-authoritative until independently justified against MADRE requirements and promoted into the relevant canonical owner.

## General comparison rule

External projects can supply:

```text
implementation ideas
interfaces to interoperate with
benchmarks/comparison cases
context-engineering techniques
examples of useful or problematic conventions
```

They do not supply MADRE requirements automatically.

Adopt a pattern only when it independently serves a MADRE requirement or demonstrated development problem.
