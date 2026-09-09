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
