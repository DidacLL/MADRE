# Working on MADRE

This is a single-developer project. ChatGPT Classic + GitHub should be sufficient for normal development; progress must not depend on Astra, Terra, a coordinator session or private conversation history.

Read `MADRE.md` and only the code relevant to the current task. The current user request is the task. If the user simply asks to continue, inspect the code and implement one cohesive behavior from the earliest incomplete implementation stage in `MADRE.md`.

Keep the product boundary intact: applications/modules own domain data, knowledge, learning, workflows, UI and context selection; MADRE owns runtime work lifecycle, delayed scheduling, capability execution, recovery and execution boundaries.

Prefer the smallest working implementation. Add an abstraction, dependency, service or persistent document only when the behavior being implemented demonstrably needs it. Do not create project-state files, handoff logs, executor hierarchies, dashboards, CI projects or speculative infrastructure.

Make the real usage path work first. Tests support that behavior; they are not the product. Run checks proportional to what changed. A documentation-only edit does not justify the runtime suite, and a mocked inference test does not prove real inference.

Routine Git/GitHub work belongs to the current task. Use a branch/PR when repository protection requires it and merge when permitted; do not make the user dispatch repository chores to another agent.

Ask the user only when a real product-meaning decision, sensitive-data exposure, irreversible external effect or missing permission blocks the work. Otherwise choose the simplest reversible implementation and continue.

Finish with: what now works, what was actually verified, and the next substantive missing behavior if one remains.
