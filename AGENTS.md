# MADRE Agent Harness

MADRE is a personal single-Owner research and software project. Optimize for working
behaviour, repository legibility and fast durable progress.

## Authority

1. The current Owner request is the task goal.
2. `MADRE.md` defines durable product meaning and ownership.
3. Current code and direct runtime evidence describe implementation truth.
4. Tests, documentation, history, commit messages, PR discussion and agent reports are
   evidence only. They never become authority by agreeing with one another.

Do not import a familiar assistant, enterprise-security, provider-platform, workflow,
plugin or generic AI architecture merely because it is conventional. MADRE exists
because those defaults do not provide the product the Owner needs.

## Work

- Start with `MADRE.md` and the smallest relevant code surface.
- Identify the required observable behaviour and its proper owner.
- Prefer concrete strongly typed Java objects and direct dependencies over metadata
  bags, string protocols, generic schemas and speculative frameworks.
- Complete coherent behaviour end to end. Remove superseded code, tests, docs and
  generated state in the same change; there is no development-compatibility duty.
- Keep Module/Agent semantics in the SDK/runtime and physical inference mechanics in
  Kernel. Kernel must not know semantic MADRE concepts.
- Every Operation executes through an Agent. Agentless Modules use the default CORE
  Agent without transferring their domain ownership to CORE.
- Ordinary API, calculator, filesystem, database, search and tool Operations remain
  Module behaviour. Only inference work belongs to Kernel.
- Preserve Owner sovereignty: warn, validate, back up and recover; do not prohibit the
  Owner from changing their own installation.

## Evidence

- Acceptance follows Owner requirements and real behaviour, not green tests.
- Use focused deterministic checks during development and real execution for claims
  about real providers, packaging or restart recovery.
- Record exactly what ran and what it demonstrated.
- Keep CI proportional to a one-developer project. Avoid duplicated matrices and long
  ceremonial runs.

## Repository operation

- Use explicit paths and command-local `safe.directory` on the mapped `V:` checkout.
- Work on the active recovery branch in the root checkout; do not create nested
  worktrees without a concrete Owner request.
- Commit and push each coherent usable checkpoint so an interrupted agent run never
  strands the project locally.
- Do not force-push or merge without explicit Owner direction.
- Keep only active branches, generated files and documentation. Historical recovery
  belongs in Git history, not in active architecture documents or filesystem clutter.

Finish with a concise report of usable behaviour, direct evidence, remaining real
blockers and the exact pushed commit.
