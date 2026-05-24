# MADREdev Operating Contract

Author: ag
State: read

This file is the stable operating contract for MADREdev agents. It owns durable execution rules. Other agent files may point here, but they must not restate these rules.

## Authority Lattice

1. Current user request controls the active task scope.
2. `docs/tex/MADRE-AgenticSystem.tex` controls MADRE product truth.
3. `AGENTS.md` routes agents to this contract and task-specific support.
4. This file controls MADREdev operating rules.
5. Task-specific support files control only their narrow task class.
6. `agents/state/**` may contain an active working definition when the current user selects it. `devboard/**`, scratch inputs, and generated outputs remain advisory and never become authority by selection.

If sources conflict, keep product architecture in the dossier, operating rules here, and task state in mutable state files.

## Path Lattice

- `docs/**`: human documentation and deliverables only.
- `docs/tex/**`: canonical product dossier source.
- `agents/def/**`: stable agent operating instructions only.
- `agents/state/**`: mutable agent working artifacts, runtime-slice drafts, working decisions, readiness notes, generated review material, and coordination state. Use `agents/state/runtime-slices/**` for runtime-slice definitions, `agents/state/technical/**` for other working technical specifications, and `agents/state/decisions/**` for working decision records when needed.
- `devboard/**`: human status projection only.
- `scripts/**`: repository maintenance and build helpers.
- `dev/**`: scratch or private inputs.

Do not create a new path class when an existing class fits.

## Mutability Model

- Stable: `AGENTS.md`, `agents/def/**`.
- Mutable working state: `agents/state/**`.
- Human product truth: `docs/tex/**`.
- Human projection: `devboard/**`.
- Generated or review material: `agents/state/**`.
- Local/private inputs: `dev/**`.

Stable files must be compact, current, and rule-owning. Mutable files may record current focus, working definitions, readiness notes, or coordination state, but they do not become product authority.

## Execution Rule

- Read the current request and the smallest source set needed for the task.
- Identify the owning file or path class before editing and keep adjacent surfaces out of scope unless the change requires them.
- Make the smallest coherent change; do not create another artifact to avoid locating the owner.
- Before deleting or relocating information-bearing material, inspect it and retain needed content in the correct owner.
- Use current constraints and evidence; do not preserve correction history as durable instruction.

## File-Creation Gate

Before creating a file, answer internally:

- Is this human documentation, stable agent instruction, mutable agent working material, dashboard projection, script, or runtime code?
- Which path owns that class?
- Is the target path stable or mutable?
- What existing file should be edited instead of creating a new one?

Create the smallest coherent artifact in the owning path. Do not place runtime-slice drafts, working technical contracts, generated reports, or task output under `docs/**` or `agents/def/**`.

## Work Branch Rule

Do not mutate `main` directly. If the worktree is on `main`, create or switch to a topic branch before editing unless the current user explicitly requests emergency direct work.

Use branch prefixes by work class when the user does not provide a branch:

- `agentic/<short-purpose>` for MADREdev and agent-environment work.
- `docs/<short-purpose>` for human documentation-only work.
- `fix/<short-purpose>` for narrow corrective patches.
- `runtime/<short-purpose>` for runtime implementation after the implementation-definition gate is satisfied.

Pull requests and merges are human-managed; agents do not create or merge PRs. Protected integration should require review and status checks before changes reach `main`, require conversation resolution where review occurs, disallow force pushes or branch deletion, and prefer linear, squash, or rebase history.

## Implementation-Definition Gate

Runtime code may start only when the current user request authorizes implementation and an owned definition in the correct path includes:

- behavior scope
- non-goals
- expected evidence
- negative path
- validation method

If any part is missing, create or patch the minimal working definition in `agents/state/**` unless the user explicitly asks for human documentation or product dossier changes. Do not treat state labels, dashboard cards, generated notes, or model output as approval by themselves.

This gate requires a sufficient implementation definition, not exhaustive upfront design. Carry relevant state-file risks into the definition as open questions or validation needs rather than treating status text as an implicit blocker.

A working definition may specify interfaces, state machines, contracts, traces, policies, tests, or scoped runtime behavior. Specifications are contract-oriented; decision records are concise and decision-specific. A working definition records its owner path, artifact type, maturity, source authority, scope, non-goals, affected context/policy/action/recovery boundaries, contract or decision, acceptance evidence, and traceability link.

Working definitions remain subordinate to the dossier; if a contradiction is found, report it and defer to the dossier. Maturity may be `generated`, `draft`, `proposed`, `accepted`, `superseded`, or `deprecated`; these labels describe evidence state, not a separate manual approval gate. Draft or generated material remains working material and cannot mark itself as product truth. If a working definition is adopted into human product truth, consolidate it into the dossier or an explicitly human-facing deliverable.

## Validation Rule

Validate the surface that changed:

- TeX changed: use the TeX support instructions and compile the touched dossier.
- Product documentation changed: check terminology, requirements, traceability, diagrams, and public scope remain coherent.
- Markdown changed: review links, scope boundaries, concision, and leakage.
- Agent instructions changed: run a redundancy review for duplicated rule ownership, stale blocker wording, stale vocabulary, product/procedure leakage, and incorrect path authority.
- State changed: confirm it remains advisory or an explicitly selected working definition, never product authority.
- Dashboard: check whether human-visible status changed and update only when it did; keep content/layout changes within its existing HTML/CSS surface and avoid dynamic behavior unless the dashboard task requires it; confirm it remains concise projection rather than procedure or planning.
- Runtime code changed: run tests tied to the owned definition and include at least one safe negative path.
- Scripts changed: run the relevant helper or a dry run when available.

CI/CD is for engineering validation such as product builds and code tests. Do not use CI as a substitute for clear agent instructions.

Do not run broad validation unrelated to the changed surface. For human/product document deletion or relocation, retain source content, diagrams, labels, and traceability in the owning human document when still required.

## Reporting Rule

Final reports must name:

- branch used
- files changed or deleted
- owner of the durable rule or artifact changed
- validation performed
- whether product docs changed
- whether runtime code changed
- any remaining risk or open question

## Agent-Facing Documentation Discipline

- One durable rule has one owner.
- Link to the owner instead of restating rules.
- Prefer current constraints, checks, and ownership over history.
- State an agent-facing artifact's role or maturity when it affects authority or lifecycle.
- Keep examples sparse and task-relevant.
- Remove obsolete instructions instead of accumulating exceptions.
- Do not add permanent personas, role-play modes, ceremony, private conversation wording, or product architecture restated as procedure.
- Express current uncertainty as an open question, risk, required definition, or next action rather than stale review-state wording.
