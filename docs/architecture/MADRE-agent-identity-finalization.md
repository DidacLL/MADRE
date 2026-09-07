# MADRE Agent identity finalization

Status: **Owner-directed clean-slate schema finalization**

This document resolves the remaining identity-normalization seam found during review of
`docs/architecture/MADRE-agentic-schema.md` at commit
`5495e9b0c32b3f0757be1eec1083d3c63d97bb9b`.

It is intentionally narrow. The second-pass schema remains authoritative for all other
entities and relationships. Where this document conflicts with the Agent/Skill identity
portion of that schema, **this document wins**.

The correction is required before implementation because an `AgentSkillInstanceRef`
scoped to an exact `AgentDefinitionRef` would force every unrelated AgentDefinition
revision to recreate/re-home unchanged learned Skill instances. That is semantically
correct but unnecessarily couples two independent revision lifecycles.

The normalized model separates:

```text
logical Agent identity
AgentDefinition revision
Agent-owned Skill-instance identity/revision
concrete AgentInstance identity
```

---

## 1. Stable logical Agent identity

Introduce a stable Module-scoped logical Agent reference:

```text
AgentRef
    module: ModuleRef
    agent_id: OpaqueId
```

`AgentRef` identifies one logical configurable Agent managed by one Module across
configuration revisions.

It is not a running actor and carries no mutable state.

The responsible Module is always `AgentRef.module`.

Examples:

```text
CORE/planner
CORE/general
MyModule/private-calendar-planner
```

The textual examples are illustrative only; identifiers remain opaque.

---

## 2. AgentDefinition is a revision of AgentRef

Replace the previous flattened definition reference:

```text
AgentDefinitionRef
    module
    agent_id
    revision
```

with the normalized form:

```text
AgentDefinitionRef
    agent: AgentRef
    revision: PositiveInt
```

`AgentDefinition` remains an immutable published configuration revision:

```text
AgentDefinition
    ref: AgentDefinitionRef
    name: string
    description: string
    manager_definition_ref: ModuleAgentDefinitionRef
    skill_instances: AgentSkillInstanceRef[]
    direct_workflows: WorkflowRef[]
    security: ActorSecurityFacts
    resolution_descriptors: AgentResolutionDescriptor[]
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance<AgentDefinitionRef>
```

The invariant becomes:

```text
manager_definition_ref.module == ref.agent.module
```

Changing the Agent configuration publishes another `AgentDefinitionRef` revision under
the same `AgentRef` unless the change intentionally creates a new logical Agent.

Creating a distinct customized/named Agent allocates a new `AgentRef` and may preserve
`derived_from` provenance to the source AgentDefinition.

---

## 3. AgentSkillInstance has an independent revision lifecycle

An installed/learned Skill belongs to the logical Agent, not to one exact
AgentDefinition revision.

Normalize the reference as:

```text
AgentSkillInstanceRef
    agent: AgentRef
    skill_instance_id: OpaqueId
    revision: PositiveInt
```

The record is an immutable revision:

```text
AgentSkillInstance
    ref: AgentSkillInstanceRef
    source_skill: SkillRef
    adopted_workflows: WorkflowRef[]
    created_at: Instant
    provenance: SkillInstanceProvenance
```

Invariants:

- `ref.agent.module` is the responsible Agent-managing Module.
- `source_skill` pins one exact immutable upstream SkillDefinition revision.
- `adopted_workflows` pin exact WorkflowDefinition revisions.
- Source Module Skill updates never float into an existing AgentSkillInstance revision.
- AgentSkillInstance changes publish a new `revision` under the same
  `(AgentRef, skill_instance_id)` when it is still conceptually the same installed Skill.
- Installing another independent copy of the same Skill allocates another
  `skill_instance_id`.
- AgentSkillInstance never grants Operation authority.

This gives the learned Skill its own stable identity and history while keeping the
upstream Module Skill immutable and separately owned.

---

## 4. AgentDefinition pins exact AgentSkillInstance revisions

Each immutable AgentDefinition revision references the exact Skill-instance revisions
that form part of that configuration:

```text
AgentRef A

AgentSkillInstance X @ 1
AgentSkillInstance Y @ 3

AgentDefinition A @ 7
    skill_instances = [X@1, Y@3]
```

An unrelated Agent configuration change can publish:

```text
AgentDefinition A @ 8
    skill_instances = [X@1, Y@3]
```

without recreating either Skill instance.

If Skill instance X is intentionally customized/upgraded:

```text
AgentSkillInstance X @ 2
```

then the Agent configuration that adopts it publishes another definition revision:

```text
AgentDefinition A @ 9
    skill_instances = [X@2, Y@3]
```

Old AgentDefinition revisions still point to the exact earlier Skill-instance revisions.

This preserves both reproducibility and independent evolution.

---

## 5. AgentInstance remains concrete execution identity

`AgentInstanceRef` remains installation-scoped:

```text
AgentInstanceRef
    instance_id: OpaqueId
```

An AgentInstance pins one exact AgentDefinition revision:

```text
AgentInstance
    ref: AgentInstanceRef
    definition: AgentDefinitionRef
    manager_instance_ref: ModuleAgentInstanceRef
    state_refs: AgentStateRef[]
    created_at: Instant
    closed_at: Instant | null
```

The responsible Module is derived from:

```text
instance.definition.agent.module
```

The existing rule remains unchanged:

```text
AgentDefinition != AgentInstance
```

Many AgentInstances may instantiate the same exact AgentDefinition concurrently.

Agent state remains `0..N` opaque Module-owned `AgentStateRef`; this normalization does
not introduce a universal Session/state model.

---

## 6. AgentRequirement

Where an Agent requirement prefers one exact configuration, keep:

```text
preferred_definition: AgentDefinitionRef | null
```

Future resolution may additionally use `AgentRef` as a stable logical preference when a
specific revision is not required, but the first implementation does not need to add
that field unless the vertical AgenticLoop genuinely requires it.

Persisted exact resolved bindings remain `AgentDefinitionRef` so historical WorkPlans do
not float when the logical Agent publishes another configuration revision.

---

## 7. Provenance and customization

The distinction is now explicit:

```text
AgentRef
    stable logical Agent/configuration lineage

AgentDefinitionRef
    exact immutable configuration revision

AgentSkillInstanceRef
    exact immutable revision of one Skill installation owned by AgentRef

AgentInstanceRef
    one concrete execution actor
```

Recommended derivation behavior:

### Modify the same logical Agent

Publish another `AgentDefinitionRef` revision under the same `AgentRef`.

### Modify one installed Skill of that Agent

Publish another `AgentSkillInstanceRef` revision, then publish an AgentDefinition revision
that pins it.

### Create a distinct named/customized Agent

Allocate a new `AgentRef`; publish its first AgentDefinition with provenance pointing to
the source definition when applicable.

### Upgrade an upstream Skill

Publish a new AgentSkillInstance revision pinned to the explicitly selected new
`SkillRef`, then publish an AgentDefinition revision adopting it.

No automatic floating occurs.

---

## 8. Persistence and garbage collection

Exact definitions/Skill-instance revisions must remain resolvable while referenced by:

```text
AgentDefinition
AgentInstance
AgentTask
WorkPlan/evidence retention
```

A later implementation may garbage-collect old revisions only after no retained semantic
or execution evidence requires them.

Stable `AgentRef` identity may survive even when no current active instance exists.

---

## 9. CORE replacement

The normalization strengthens the replaceable CORE invariant.

Changing `CoreRoleAssignment` never changes:

```text
AgentRef.module
AgentDefinitionRef.agent
AgentSkillInstanceRef.agent
AgentInstance.definition
```

Existing Agents and their installed Skills remain owned by the Module that actually
manages them.

Future CORE fallback resolution uses the newly assigned CORE Module; historical bindings
remain exact.

---

## 10. Architecture examples

### Unrelated Agent configuration revision

```text
AgentRef calendar-planner

SkillInstance calendar-skill @ 2

AgentDefinition @ 5
    direct_workflows = [weekly-plan@1]
    skills = [calendar-skill@2]

# user adds another direct Workflow

AgentDefinition @ 6
    direct_workflows = [weekly-plan@1, travel-check@1]
    skills = [calendar-skill@2]      # reused, not recreated
```

### Skill customization only

```text
AgentSkillInstance calendar-skill @ 2
    source = CalendarSkill@3

# user explicitly adopts upstream CalendarSkill@4

AgentSkillInstance calendar-skill @ 3
    source = CalendarSkill@4

AgentDefinition @ 7
    skills = [calendar-skill@3]
```

### Concurrent execution

```text
AgentDefinition planner@7
    |
    +-- AgentInstance A
    +-- AgentInstance B
    +-- AgentInstance C
```

All three share immutable Agent/Skill configuration revisions but not implicit mutable
state.

---

## 11. Final invariants

```text
AgentRef is stable logical Agent identity.

AgentDefinitionRef is an exact immutable revision of AgentRef.

AgentSkillInstanceRef belongs to AgentRef and has its own revision lifecycle.

AgentDefinition pins exact AgentSkillInstance revisions.

Unrelated AgentDefinition changes do not recreate unchanged Skill instances.

Changing an installed Skill does not mutate previous AgentDefinitions.

Upstream SkillDefinition updates never silently alter AgentSkillInstances.

AgentInstance pins an exact AgentDefinition revision.

Agent state remains opaque, Module-owned and 0..N.

CORE role assignment never changes Agent/Skill ownership.
```

With this normalization, the clean-slate schema has no known identity seam that should
block the first vertical implementation.
