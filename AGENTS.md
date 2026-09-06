# Working on MADRE

This is a single-developer project. ChatGPT Classic + GitHub should be sufficient for normal development; progress must not depend on Astra, Terra, a coordinator session or private conversation history.

## Product authority

Read `MADRE.md` before making architectural assumptions. Its **Product invariants** are the active authority for what MADRE means and how responsibility is divided.

Git history, deleted documents, old architecture files, research reports, issues, pull requests and previous conversations may provide historical evidence, but they do not restore product requirements or concepts that are absent from the active definition. Do not infer that an old class, subsystem, workflow or policy model still exists because it appears in history.

The `Current realization strategy` in `MADRE.md` is an implementation order, not an invariant. It may be revised from code and evidence without redefining the product. Do not promote a current language, library, transport, database, provider, class name or implementation technique into product architecture unless the product invariants actually require the responsibility.

For a new responsibility, apply the ownership test in `MADRE.md`: domain meaning belongs to the application; runtime scheduling/execution/recovery belongs to MADRE; computation belongs behind a replaceable capability; generated output is data.

## Development

Read only the code relevant to the current task after `MADRE.md`. The current user request is the task. If the user simply asks to continue, inspect the code and implement one cohesive behavior from the earliest incomplete realization stage.

Keep the product boundary intact: applications/modules own domain data, knowledge, learning, workflows, UI, context selection and semantic domain governance; MADRE owns runtime work lifecycle, delayed scheduling, capability execution, recovery and active execution/transfer boundaries.

Prefer the smallest working implementation. Add an abstraction, dependency, service or persistent document only when the behavior being implemented demonstrably needs it. Do not create project-state files, handoff logs, executor hierarchies, dashboards, CI projects or speculative infrastructure.

Make the real usage path work first. Tests support that behavior; they are not the product. Run checks proportional to what changed. A documentation-only edit does not justify the runtime suite, and a mocked inference test does not prove real inference.

Routine Git/GitHub work belongs to the current task. Use a branch/PR when repository protection requires it and merge when permitted; do not make the user dispatch repository chores to another agent.

Ask the user only when a real product-meaning decision, sensitive-data exposure, irreversible external effect or missing permission blocks the work. Otherwise choose the simplest reversible implementation consistent with the product invariants and continue.

Finish with: what now works, what was actually verified, and the next substantive missing behavior if one remains.
