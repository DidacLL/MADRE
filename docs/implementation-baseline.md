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

Kernel's immediate physical path now uses that SDK:

- installed Capability identities and definitions remain in the Kernel extension
  package and never enter `WorkRequest` or `PhysicalResult`;
- Capability inputs own their explicit Privacy independently of physical location;
- registry selection first requires computation and Material contracts, then direct
  immutable value composition, physical requirements, availability, and preferences;
- resource coordination operates on typed resource claims rather than mechanism or
  locality cases;
- Kernel forwards payloads through a typed physical invocation and returns raw
  `PhysicalResult` without constructing Material;
- a private fixture Module interprets a first result, constructs independent Material,
  and makes a second ordinary work request as its own continuation.

Durable physical work and Module discovery now use the same boundary:

- SQLite stores an opaque versioned input snapshot until execution and a raw pending
  physical result until delivery;
- queued input and pending output survive process restart, while Kernel never creates
  Material;
- selected physical attempts record only the mechanism actually used;
- physical failure follows the request's attempt policy, while current Capability
  unavailability remains queued without recording a rejected participant;
- cancellation and delivery remove their payload bytes, and failed input has an
  explicit cleanup operation;
- the SQLite schema contains only work, attempt, and scheduler tables;
- running Module definitions live only in `ModuleRegistry`, which starts empty and
  returns exact reachable public Agent and Operation definitions.

Focused SDK, immediate execution, restart, retry, delivery, schema, and registry
tests, Ruff, and strict type checking pass. HTTP transport, installation mapping, and
the OpenAI-compatible adapter still target superseded contracts at this checkpoint.
