# Working on MADRE

This is a single-developer project. ChatGPT Classic + GitHub should be sufficient for normal development; progress must remain recoverable from the repository without depending on a coordinator session or private conversation history.

## Product authority

Read `MADRE.md` before making architectural assumptions. Its **Product invariants** are the active authority for what MADRE means and how responsibility is divided.

Current requirements come from the active product definition and working code that is consistent with it. Historical documents, Git history, research reports, issues, pull requests and previous conversations are supporting evidence when a task explicitly needs historical context; they acquire current architectural relevance through an explicit update to the active definition or through current working behavior consistent with it.

The `Current realization strategy` in `MADRE.md` is an implementation order rather than a product invariant. It may be revised from code and evidence while preserving the product semantics.

Concrete implementation choices are allowed and expected. A language, library, transport, database, provider, class structure or other technology may be selected, depended on, and architected around when the current implementation genuinely needs it. Stable implementation commitments are appropriate when they simplify and strengthen the working system.

A concrete choice becomes part of the invariant product definition only through explicit product-level justification. Product invariants define the responsibilities and authority MADRE must preserve; implementation architecture chooses the simplest suitable mechanisms that realize them.

For a new responsibility, apply the ownership test in `MADRE.md`: domain meaning belongs to the application; runtime scheduling/execution/recovery belongs to MADRE; computation belongs behind a replaceable capability; generated output is data.

## Development

Read only the code relevant to the current task after `MADRE.md`. The current user request is the task. If the user simply asks to continue, inspect the code and implement one cohesive behavior from the earliest incomplete realization stage.

Keep the product boundary intact: applications/modules own domain data, knowledge, learning, workflows, UI, context selection and semantic domain governance; MADRE owns runtime work lifecycle, delayed scheduling, capability execution, recovery and active execution/transfer boundaries.

Prefer the smallest working implementation. An abstraction, dependency, service or persistent development artifact earns its place when current behavior or recurring work is materially simpler, clearer or more reliable because of it. Favor direct implementation when an additional layer has no demonstrated responsibility yet.

Make the real usage path work first. Validation evidence should match the claim being made: focused fixtures can establish transport and error behavior; successful real inference requires exercising a real model endpoint; documentation changes require documentation-level validation.

Routine Git/GitHub work belongs to the current task. Use the repository workflow required by current protection rules and complete branch/commit/PR/merge operations when permissions allow.

Escalate to the user when product meaning, sensitive-data exposure, irreversible external effects or missing permission genuinely require a decision. Otherwise choose the simplest reversible implementation consistent with the product invariants and continue.

Finish with: what now works, what was actually verified, and the next substantive missing behavior if one remains.
