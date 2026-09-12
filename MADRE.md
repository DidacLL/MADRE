# MADRE

MADRE is personal software that lets one owner use locally installed and external
inference or deterministic mechanisms without silently surrendering control of what
information reaches them.

The owner may create, install, change, or remove Modules and may choose how their own
computer is used. MADRE's information concern is unmanaged reach into third-party,
SaaS, cloud, public, or otherwise less-private boundaries. It makes the relevant
values explicit so the owner and their Modules can choose a destination, expose less,
or first create independent minimized, tokenized, anonymized, summarized, or
otherwise adapted Material.

## The building blocks

A **Module** is an owner-installed application or integration. It owns all semantic
work: its domain state and persistence, Material, UI, Agents, Skills, Workflows,
transformations, interpretation, continuation, and bounded Operations.

The **SDK** is the strongly typed construction model for a correct Module. Human
authors, future Module-generation tools, and wrappers around other software use the
same objects and composition operations. Serialization is a boundary representation
of those objects, not the way Module logic is programmed.

An **Agent** is a Module-owned intelligent actor. A **Skill** is reusable capability,
knowledge, or instruction material owned by a Module. A **Workflow** is a reusable
semantic recipe owned by a Module. MADRE does not prescribe their private behavior.

An **Operation** is one explicit, bounded, Module-owned callable consequence. An
**EffectProfile** describes one execution variant of that Operation through Risk and
Autonomy. The Module implements and invokes its Operations.

A **Material** is a typed Module-owned value with its own identity, payload contract,
payload, and Sensitivity. Every adaptation or produced semantic value is independent
new Material. The Module that understands the transformation supplies its identity
and applicable value.

A **Capability** is one physical inference or deterministic mechanism installed in
Kernel. It has physical contracts and the algebraic values applicable to its actual
surfaces. It does not own meaning or decide what happens next.

## Security Algebra

MADRE uses five distinct ordered carriers:

- **Sensitivity**: the confidentiality consequence of exposing information;
- **Privacy**: the confidentiality boundary of a receiving surface;
- **Integrity**: bounded causal or physical-realization responsibility;
- **Risk**: the consequence class of one Operation variant;
- **Autonomy**: the residual machine execution of that same variant.

Equal ranks do not make these types interchangeable. Integrity does not describe
truth, model quality, reliability, reputation, or generic trust.

Applicability is expressed by role-specific types. Material and outbound surfaces
carry Sensitivity. Receiving surfaces carry Privacy. Actual non-user causal
participants and physical realizers carry Integrity. EffectProfiles carry Risk and
Autonomy. An entity has no field for a facet that has no meaning in its role.

`Privacy.UNKNOWN` is the explicit P2 boundary used when information reaches a
third-party environment outside the owner's control without being declared fully
public. It is a real value, not missing knowledge. `Privacy.PUBLIC` is P1. Installation
supplies the applicable Privacy explicitly; locality, endpoint, provider, adapter,
and model identity never compute it.

The algebra is intrinsic immutable composition:

```text
accumulated Sensitivity = maximum participating Sensitivity
accumulated Privacy     = minimum participating Privacy
accumulated Integrity   = minimum participating Integrity

information reach requires:
    accumulated Sensitivity <= accumulated receiving Privacy

one bounded Operation variant requires:
    min(profile Risk, profile Autonomy)
        <= accumulated Integrity of actual non-user causal participants
    no non-user causal participant contributes the I5 identity

    profile Risk <= accumulated Integrity of actual physical realizers
    at least one physical realizer participates
```

Risk and Autonomy from different EffectProfiles never combine. Adding a participant
returns a new valid composite when the ranks support each other. Otherwise no new
value exists and the earlier immutable value is unchanged. This is ordinary object
construction used directly by Module and Kernel-side extension code where their own
objects are assembled; it does not create a separate runtime function or stored
state.

Algebraic values are cumulative wherever their roles are cumulative. A Module's
Sensitivity follows the Material and output surfaces it actually owns or makes
reachable. An Agent's Privacy follows the exact input surfaces of the Operations it
exposes. A Module may manage S5 Material while publishing distinct Operations whose
independent outputs are S4, S3, or S2.

## Kernel and physical work

Kernel owns shared physical mechanics:

- live registries of running Modules and installed Capabilities;
- listing Module, Agent, and Operation surfaces that can be reached from the caller's
  exact current values;
- selection among installed physical mechanisms;
- routing, timing, priority, resource coordination, and scheduling;
- durable work queueing, physical attempts and retries;
- delivery or temporary buffering of physical results;
- ordinary execution logging.

A Module submits its actual Material, required computation contract, and typed
physical requirements or preferences. It never names or receives the identity of the
concrete installed Capability chosen by Kernel. Kernel uses the carried algebraic
values and physical request to find an available mechanism, forwards the Material
opaquely, and returns a physical result.

Kernel receiving or durably queueing opaque input does not transfer Material
ownership. Kernel does not understand payload meaning, create output Material,
choose its Sensitivity, interpret the physical result, select a semantic continuation,
or invoke an Operation. The requesting Module receives the result, interprets it, and
may create new Material, submit another work request, invoke one of its bounded
Operations, or stop.

Durable queue storage exists only so physical work and undelivered results can survive
Kernel restart. Payload bytes are opaque lifecycle snapshots retained until delivery
and cleanup. Module definitions live only in the runtime registry and Modules register
again after Kernel restart.

Physical unavailability, interruption, timeout, and mechanism failure belong to work
scheduling and retry. They do not alter the Security Algebra.

## Interoperability

Module definitions declare nominal identities and the exact public Agent, Skill,
Workflow, Operation, and role-specific surfaces they own. Kernel's live Module
registry exposes only the currently reachable public definitions for the caller's
actual values. Registry presence grants nothing and persists no Module domain state.

Definitions contain no executable Python objects, import paths, generic property
bags, provider settings, or prescribed Agent loop. Implementations bind to narrow
runtime ports outside serialization.

Capability definitions belong to Kernel's extension boundary, not to Module-facing
SDK discovery. Provider protocol and transport details remain private to adapters.

## Installation role

An installation may designate one ordinary Module as its default interaction role.
That designation is external configuration only. It introduces no package, public
type, behavior, privilege, default algebraic value, or Kernel branch, and removing the
designation does not change the Module.

## External mechanics

Provider accounts and connection setup belong to the adapter environment. They do
not enter MADRE's domain or algebra.

MADRE does not attempt to contain deliberately hostile owner-installed software. Its
promise is narrower and more useful: correctly built Modules use explicit typed
composition so the owner can understand and manage where their information goes,
while Kernel provides durable access to a varied physical computation ecosystem.
