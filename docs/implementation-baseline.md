# Implementation Baseline

This branch is replacing a development-only runtime whose public contracts diverged
from `MADRE.md`. The architecture files describe the replacement boundary; executable
claims belong here only after the corresponding slice is implemented and verified.

## Verified foundation inherited from the target branch

The repository has a Python package managed by `uv`, a FastAPI process boundary,
SQLite-backed durable work mechanics, and an OpenAI-compatible physical adapter.
These concrete dependencies may remain where they continue to serve the corrected
ownership model.

## Replacement sequence

1. Rebuild the public SDK around nominal immutable identities, role-specific algebra
   values, Material, Module definitions, bounded Operations, and explicit codecs.
2. Rebuild physical work so Modules submit Material opaquely and receive raw
   `PhysicalResult`, while installed Capability definitions remain Kernel-private.
3. Recreate durable queue storage around opaque input and pending-output snapshots;
   retain physical scheduling, cancellation, retry, and attempt telemetry.
4. Replace stored Module definitions with a live registry and exact public-surface
   reachability.
5. Remove the development demonstration package and prove the complete path with a
   private integration fixture.

Until each slice is recorded below, the older implementation and its tests are only
divergence evidence and must not be treated as the current contract.

## Executable behavior recorded after replacement

The public `madre_sdk` package now provides:

- nominal immutable identities for Modules and every Module-owned definition;
- five non-interchangeable ordered carriers;
- role-specific input, output, and responsibility surfaces with direct immutable
  composition;
- typed independent Material and nonempty Material sets;
- canonical Module, Agent, Skill, Workflow, Operation, and EffectProfile definitions;
- bounded Operation-call construction from one exact profile and actual participants;
- typed physical work requests and raw physical results without Capability identity;
- a versioned JSON codec separate from the domain inheritance model;
- narrow physical execution, Operation implementation, and Module-directory ports.

Focused SDK tests and strict type checking pass. Kernel consumers still target the
superseded SDK at this checkpoint and are the next replacement slice; no complete
runtime claim is made yet.
