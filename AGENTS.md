# MADRE Agent Harness

MADRE is personal, owner-sovereign software. It connects Modules to installed
physical inference and deterministic mechanisms while making information reach
explicit and manageable by the owner.

## Authority

Use the current Owner request as the task goal and `MADRE.md` as the durable product
model. Treat code, tests, architecture notes, history, and generated documents as
implementation evidence. They do not acquire product authority through age,
repetition, or apparent completeness.

Recover the intention behind Owner explanations. Do not transplant conventional AI
platform, agent-harness, hosted-service, or enterprise-security architecture into
MADRE merely because its vocabulary looks familiar.

## Ownership model

- A Module owns meaning, domain state, Material, transformations, Agents, Skills,
  Workflows, interpretation, continuation, UI, and bounded Operations.
- The SDK supplies the strongly typed reusable objects from which correct Modules
  are built. Module authors use its algebraic construction directly.
- Kernel owns live registries, physical Capability selection, routing, scheduling,
  resource coordination, durable work queueing, physical retry, result delivery,
  and ordinary execution logs.
- A Capability is one physical inference or deterministic mechanism registered
  inside Kernel. It returns physical output, not Material or instructions.
- CORE is only an external installation-role assignment to an ordinary Module. It
  changes no public type or runtime behavior.

Kernel may carry a Module's Material opaquely as work input. This does not transfer
ownership. Kernel never assigns Material identity or meaning, classifies an output,
or chooses a semantic continuation. The requesting Module receives physical output
and decides what it means.

Modules describe required computation and physical execution preferences. They do
not select, name, or inspect the concrete Capability used by Kernel.

## Security Algebra

Sensitivity, Privacy, Integrity, Risk, and Autonomy are distinct ordered types.
Composition is an intrinsic operation of immutable SDK values, not a Kernel service
or a runtime lifecycle.

- exposed Sensitivity accumulates by maximum;
- receiving Privacy accumulates by minimum;
- causal or physical Integrity accumulates by minimum;
- information can reach a receiving surface only while accumulated Sensitivity is
  no greater than accumulated Privacy;
- one EffectProfile's `min(Risk, Autonomy)` is supported by the actual non-user
  causal participants, using I5 when there are none;
- one EffectProfile's Risk is supported by a nonempty set of actual physical
  realizers;
- values from separate EffectProfiles never combine.

Applicability comes from role-specific object types. Do not put optional algebra
facets into one universal object. `Privacy.UNKNOWN` is the explicit P2 boundary for
third-party handling outside the owner's control; it is not absent information or a
value inferred from locality, endpoint, adapter, provider, or model.

Composition either produces the immutable combined value or cannot produce one.
Failed construction leaves the prior value unchanged and creates no durable object.
Execution logs and work retries remain ordinary physical-runtime concerns and never
become algebra operands.

Every transformation creates independent new Material with a new identity and
explicit applicable values. Prior Material remains unchanged. Optional provenance is
Module metadata and does not influence composition.

## Public design

Public MADRE objects are immutable, nominally typed, and responsible for their own
invariants. Definitions are declarative and behavior implementations are separate.
JSON and future XML are codecs at system boundaries, not the programming model.

Prefer cohesive domain objects, segregated ports, structural ownership, and derived
values. Do not substitute string tags, generic dictionaries, dynamic type checks,
duplicated summaries, import paths, or Python framework convenience for a durable
OOP contract.

Add a reusable abstraction only when a concrete MADRE responsibility requires it.
Semantic behavior remains private to concrete Modules; the SDK publishes only the
reusable structure those Modules actually share.

## Engineering loop

1. Identify the exact owner-visible behavior or boundary being changed.
2. Inspect only the code and evidence needed to understand that surface.
3. Replace divergent development contracts coherently; MADRE has no installed-base
   compatibility requirement yet.
4. Implement the smallest complete typed behavior.
5. Validate public behavior and runtime evidence, not implementation-shaped tests.
6. Inspect names, contracts, persistence, transport, tests, and docs for responsibility
   leakage across the whole changed surface.
7. Commit and push each coherent green checkpoint. Never merge without explicit Owner
   instruction.

Preserve unrelated user changes. Keep product meaning in `MADRE.md`, focused runtime
architecture in `docs/architecture/`, executable truth in code and tests, and runnable
setup in `README.md`.

At completion report the usable behavior, exact verification, pushed head, and only
genuine unresolved product questions.
