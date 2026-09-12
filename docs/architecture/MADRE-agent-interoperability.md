# MADRE Module Interoperability

## Declarative public definitions

The SDK represents Module interoperability through immutable domain objects:

- `ModuleDefinition` canonically owns its child definitions and public surfaces;
- `AgentDefinition` identifies its typed inputs and outputs and references the exact
  Skills, Workflows, and Operations it exposes;
- `SkillDefinition` identifies reusable Module-owned capability or knowledge;
- `WorkflowDefinition` identifies a reusable Module-owned semantic recipe;
- `OperationDefinition` identifies one bounded callable consequence and owns its
  input surfaces, output surfaces, repeatability, and EffectProfiles.

Every identity is nominal. Child identities structurally contain their `ModuleId`, so
an identity from another category or owner cannot be substituted by matching strings.
Constructors enforce uniqueness, ownership, and reference resolution.

Definitions describe public structure only. Agent reasoning, state, memory,
interpretation, semantic orchestration, and Operation implementations remain private
Module code.

## Role-specific surfaces

`InputSurface` identifies an accepted `MaterialType` and explicit Privacy.
`OutputSurface` identifies a produced or reachable `MaterialType` and Sensitivity.
`ResponsibilitySurface` carries Integrity for one actual causal or physical role.

Any definition may own the surface types that apply to its real responsibility. It
does not receive irrelevant algebra fields. Collections derive their aggregate ranks
from members.

Module Sensitivity is derived for an exact current `MaterialSet` and the Module's
reachable output surfaces. Agent Privacy is derived from the exact Operation input
surfaces the Agent exposes. Public directory filtering therefore works at the
published surface, not at an unrelated whole-Module summary.

## Material

`MaterialType[T]` defines nominal content-contract identity and media representation.
`Material[T]` binds its own identity, owning Module, type, payload, and Sensitivity.
`MaterialSet` is a nonempty immutable collection whose Sensitivity is the maximum of
its members.

Module transformations always construct independent Material. No historical member
is needed to use it.

## Runtime ports

Only responsibilities shared across implementations receive public ports:

- `ExecutionService.submit(WorkRequest) -> PhysicalResult`;
- a typed Operation implementation port for one declared Operation;
- read access to the live Module directory.

Private Module and Agent behavior is ordinary Module code. The SDK imposes no common
receive method, turn type, loop, planner, or semantic runtime.

## Serialization

Explicit versioned codecs map the object graph to boundary DTOs and back. DTOs do not
belong to the domain inheritance hierarchy. Wire data contains no executable objects,
import paths, arbitrary property dictionaries, or duplicated derived ranks. The same
domain graph can later receive an XML codec without redesign.

## Directory behavior

Registration adds or replaces a running Module definition in Kernel's in-memory
directory. Removal occurs when that Module leaves. Restart begins with an empty
directory.

A reachability query supplies the caller's exact current carried values. Each
published input surface composes itself with those values; only the matching Module,
Agent, and Operation definitions are returned. The query result is transient and does
not change either Module.
