# MADRE Security Algebra

This is the focused normative architecture owner under `MADRE.md`. It replaces
previous Security Algebra designs; committed history is rationale, never authority.

## Meaning and carrier

MADRE governs local-first execution relationships between bounded scopes, sensitive
state, actual observers, causal controllers and effect-realization participants.
It does not evaluate truthfulness, hallucination, intelligence, moral correctness,
provider reputation, generic software reliability, hostile native code, ACLs,
roles, clearances, authorization tokens, retries, idempotency or Work recovery.

The five independent facets use the ordinary ordered carrier `1 < 2 < 3 < 4 < 5`.
Only order, join/max and meet/min are fundamental. There is no global score.
Facet names retain distinct meanings even though they use the same carrier.

## Scopes and immutable facts

A SecurityObject represents an exact projection, not necessarily an entire software
object. Module/Agent/endpoint surfaces may have different facets and levels:
`CORE/private-domain`, `CORE/arithmetic-task`, or a particular material revision.
`SecuritySubjectRef`, immutable values, reachable-source IDs and structural binding
evidence determine the SecurityID. A changed revision, digest or scope is a new
object. One SecurityID cannot resolve to conflicting facts.

Facet applicability follows the represented scope and its actual transition role,
not a subject-kind matrix. A Module surface may expose sensitive state. An Artifact
need not carry Integrity. Missing role-required facets fail structurally.
Risk and Autonomy occur only together on an Operation-owned EffectProfile.

## Sensitivity

Sensitivity originates in user/system information or state:

| Level | Calibration |
| --- | --- |
| S1 | Public/negligible |
| S2 | Limited private information |
| S3 | Materially private/confidential |
| S4 | Highly sensitive, identifying, intimate or correlated |
| S5 | Secrets, credentials or security-critical state |

For all state actually reachable/exposable through scope x:
`S(x) >= max(S(s) for s in Sources(x))`.
Ordinary construction with no added semantic sensitivity uses equality. Correlation
may raise Sensitivity. Owning an S5 credential does not force an isolated arithmetic
surface to S5, but any route through that surface to the credential does.
Module/SDK declarations establish complete reachability under the honest-contract
threat model; Kernel checks declared closure and bindings, not private content.

## Privacy

Privacy means how privately a participation handles entrusted information:

| Level | Meaning |
| --- | --- |
| P1 PUBLIC | Outward/public participation: publishing, mail, messages, sharing |
| P2 UNKNOWN | No stronger MADRE boundary established; ordinary independent cloud inference |
| P3 LOCAL_PRIVATE | Inside the user's local MADRE environment |
| P4 MODULE_PRIVATE | Inside the relevant Module-private domain |
| P5 SECRET | Strongest MADRE confidentiality scope |

Vendor contracts, retention promises, training policies and certifications never
upgrade Privacy. Configuring a provider is not a UserRelease. Only actual observers
enter a disclosure meet; rejected candidates receiving no bytes do not.

## Integrity, Risk and Autonomy

Integrity is the security-significant causal or effect-realization responsibility
that the exact scope is warranted to bear under its bounded MADRE contract, I1..I5.
It applies to actual controllers, executors and explicitly control-relevant material.
It is not generic trust, truth validation or an automatic property of generated text.

Risk is the consequence class of realizing an immutable bounded EffectProfile:
R1 negligible/observation/trivially reversible; R2 low bounded; R3 meaningful domain
mutation/external consequence; R4 high impact/difficult recovery/broad consequence;
R5 destructive, irreversible, executable, credential/security-critical or equivalent.

Autonomy on that same profile measures residual security-significant machine control:
A1 exact direct-user effect; A2 narrow residual discretion; A3 bounded delegated task;
A4 substantial standing/recurring automation; A5 substantially/full machine control.
Different control contracts require different immutable profiles. Callers select
published profiles and cannot substitute lower values. Work retries are not operands.

For several effects controlled by the same scope, demand is
`max(min(R(e), A(e)) for e in profiles)`. Independent `(R5,A1)` and `(R1,A5)` never
create a fictional `(R5,A5)`. Runtime may evaluate one transition per profile.

## Transition topology and predicates

Relationships are DISCLOSURE, CONTROL, EFFECT_EXECUTION, DERIVATION and USER_RELEASE.
SecurityHistory carries immutable facts and relationships, not a global reduction.
A prospective decision evaluates the relationships actually connected to it.

For each disclosure d, `S_d = max(S(s) for s in Sources(d))` and
`P_d = min(P(p) for p in Observers(d))`:

`DisclosureAllowed(d,H) = (S_d <= P_d) OR ReleaseCovers(d,H)`.

The current Disclosure binds one source scope (which can be a composed bundle) and
all actual observers. Independent disclosures are evaluated independently.

UserRelease is actual carried user-interaction evidence. At each crossing, its
material identity/revision/digest, destinations, relevant effect and scope must
match the concrete disclosure. Release changes neither S nor P, creates no global
declassification, and has no token or separate authorization time. Telemetry is
not covered by release to a different recipient. The minimal executable form is
exact disclosure plus exact effect-execution route and interaction revision.
Durable broader evidence may be introduced only with explicit structural matching
of each instantiated crossing; it is not currently a wildcard API.

For effect e, actual non-user controllers determine whether it occurs, profile,
target, parameters, effect content, scope or significant continuation:

`I_C(e) = min(I(c) for c in Controllers(e))`, or 5 for an empty set.
`ControlSafe(e) = min(R(e), A(e)) <= I_C(e)`.

Mere participation or carrying text does not make a controller. The broker binds the
active selecting actor; Modules declare additional actual control material/scopes.

Executors are actual scopes capable of changing the realized physical effect:
Module endpoint, implementation boundary, mechanism or adapter where relevant.
An executed effect requires at least one executor:

`I_E(e) = min(I(x) for x in Executors(e))`.
`EffectSafe(e) = R(e) <= I_E(e)`.

The profile is not implicitly an executor. A represented execution boundary must
be explicitly connected as one. Direct user control reduces controller demand but
never the execution responsibility required by a high-consequence effect.

## Derivation

Every derivation creates a distinct immutable result and exact source/result binding.
Ordinary propagation requires `S(out) >= max(S(sources))`. Pure structured selection
uses exactly retained members and `S(out) = max(S(retained_members))`.

A semantic transform uses Module-owned `tau_S_t(concrete inputs)` and may lower,
preserve or raise Sensitivity. There is no universal numeric minimization operation.
The relation binds its declared procedure owner/identity/revision, concrete source
SecurityIDs and output SecurityID (including result values and digest). The Module
owns semantic correctness; Kernel checks those immutable bindings.

When output Integrity is actually claimed, ordinary repackaging cannot increase it
beyond the minimum of control-relevant sources and producers. Absent Integrity does
not mean zero: ordinary content need not make a control claim. Explicit validation
requires a declared procedure and actual validators establishing the new warranted
contract; it is not generic AI-output truth validation. Broker completion retains
its existing checks on actual producer/validator participation.

## Structural validity and complete decision

Before numeric permission, validate resolved IDs and revisions, immutable conflicts,
role-required ordinary facets, sensitivity/observer/controller/executor closure,
Operation/profile ownership, paired R/A, actual route/Capability/endpoint, material
digest continuity, derivation bindings, and concrete release coverage.

Declared closure completeness is a Module/adapter contract. Kernel adds/checks
participants known from physical protocol topology without inspecting semantics.
CORE uses exactly the same public contracts as any Module.

`Admissible(T,H)` is structural validity AND every disclosure predicate AND every
control predicate AND every execution predicate AND every new derivation predicate.
History is not replayed as unrelated numeric obligations. Retry/attempt identity,
scheduler state and idempotency do not enter this formula. Equal security projections
produce equal decisions; fallback matters only when it changes actual operands.

## Minimality

Each facet has an observable separation: sensitivity of state, privacy of observer,
warrant of controller/executor, consequence of effect, and residual machine control.
Removing one requires proving that no valid transition observes its distinction.
Adding a sixth scalar requires two transitions with identical S/P/I/R/A, topology,
derivation and release/control evidence for which MADRE must decide differently.
Conventional security practice is not such a witness.
