# Implementation Baseline

The active branch currently contains no executable MADRE implementation.

This is deliberate. The removed Python implementation coupled Capability selection to
Module Material types, represented algebraic application points as autonomous surface
objects, obscured CORE, and used a fabricated Module as its end-to-end product claim.
Keeping that code runnable would make the repository less truthful and would give
later development a contaminated starting point.

The surviving repository establishes:

- the Owner's product model in `MADRE.md`;
- the exact algebra in `docs/architecture/MADRE-security-algebra.md`;
- Module, Kernel, Capability, CORE, execution, and interoperability boundaries in the
  focused architecture documents;
- the complete implementation sequence and acceptance path in
  `docs/master-development-plan.md`.

The implementation target is Java 21. The first implementation checkpoint must create
the public SDK and Kernel foundation described by the master plan. No Python package,
runtime, test fixture, wheel, or runnable service remains active.

This document must be rewritten after each development slice to describe only behavior
that actually runs at the current head.
