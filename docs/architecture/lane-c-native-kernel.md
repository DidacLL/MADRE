# Lane C native implementation contract — archived reference

The detailed Lane C native contract that originally occupied this path has been preserved verbatim at:

- `docs/history/lane-c-native-kernel-implementation-contract.md`

It remains useful historical/implementation evidence, but it is **not current product or target Kernel authority**. The original worker/engine architecture was rejected, and the later C++ exact-`ConcretePhysicalInvocation` correction is now retained only as a reference implementation/test oracle because the accepted DRE architecture requires Kernel-side capability-aware physical scheduling.

Use the current documents instead:

- `docs/product/lane-c-owner-decision.md` — accepted current Lane C/DRE decision and the reasons behind it;
- `docs/product/owner-intent-corpus.md` — product meaning and causal context;
- `docs/architecture/security-algebra.md` — operational SPIRA carriers, composition relations and boundaries;
- `docs/architecture/mid-level-architecture.md` — accepted whole-system semantic/runtime/physical boundaries;
- `docs/architecture/kernel.md` — target physical Kernel/DRE architecture and replacement validation path.

The active correction branch still contains the C++ LCR1–LCR3 implementation. Preserve its useful behavioral evidence, but do not restore its historical architecture or continue it by inertia when it conflicts with the later Owner decision.
