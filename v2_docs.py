from pathlib import Path
R=Path(__file__).parent
def rd(p):return (R/p).read_text()
def wr(p,s):(R/p).write_text(s)
wr('docs/architecture/MADRE-security-algebra.md', '''# MADRE Security Algebra V2

Authority: `MADRE.md` defines product meaning. This document defines the public security contracts, transition roles and deterministic evaluation needed for independent Module composition.

## Purpose and ownership

MADRE evaluates concrete representations, disclosure boundaries and bounded effects. It does not assign permission to applications or judge generic AI trustworthiness. Modules retain their data, semantic transformations, interpretation and domain effects. Kernel binds and evaluates public execution facts without understanding private payload meaning.

Encapsulation changes the contract being evaluated. A bounded Operation exposes its permitted effect surface, not the application behind it. A contained participant does not become a separate exposure boundary merely because it processes bytes. Transformations produce new concrete representations; they do not add security points to their inputs.

## Typed contracts

Ordinary projections use the ordered levels 1 through 5. Zero is reserved and invalid for ordinary operands. No addition, subtraction, averaging or universal compensation is defined.

`SecurityObject` provides immutable subject/version binding and a deterministic `SecurityID`. The current concrete value schemas are:

| Bound subject | Values |
| --- | --- |
| Artifact / ContextBundle | Sensitivity, Assurance |
| Actual participating Agent / Module / implementation | Assurance |
| Capability computation | Assurance |
| Disclosure boundary | PrivacyCapacity |
| Operation EffectProfile | RiskEnvelope, Autonomy, realization Assurance, bound public role relationships |
| TransformContract | Contract identity and evidence-schema identity, bound to implementation/version |

Sensitivity describes disclosure consequence of this representation, not all information held by its owner. Assurance describes reliance on a concrete representation or implementation in a security-significant causal/realization role; it is not truth, intelligence, ownership or permission. PrivacyCapacity describes the sensitivity admissible through the actual containment/exposure boundary.

`RiskEnvelope` contains `control_risk` and `effect_risk`, with `1 <= control_risk <= effect_risk <= 5`. Control risk is the maximum consequence attributable to unsafe residual machine choice permitted by this profile. Effect risk is the maximum consequence reachable through realization of the bounded profile.

Autonomy describes how far the profile proceeds without user intervention. A1 requires explicit user participation; A5 proceeds autonomously. Autonomy orders feasible profiles; it never reduces a safety obligation. Generic acknowledgement and exact-target mediation can both be A1 while having different control risk.

These scalars are current projections of typed contracts, not complete descriptions of behavior. Equal projections do not make two contracts semantically interchangeable.

## Identities and topology

Routing identity identifies the Module and exported implementation being invoked. Security identity binds the actual contract or representation used in a calculation. Neither grants anything.

Endpoint attachments capture the exact Module publication and explicit boundary contracts. Forwarding endpoint identity is routing metadata, not another SecurityObject. An endpoint contributes Assurance only through explicit participating implementation contracts when it changes production or realization. Replacement under the same publication version still invalidates a stale attachment.

Execution-bound SDK services preserve the established actor and route independently of caller-supplied history. They expire when that execution ends. There is no ambient execution context. This is the public SDK contract, not process isolation from arbitrary Python/private host access.

Transitions use:

```text
DISCLOSURE: material representation -> actual serial boundary contracts
CONTROL: EffectProfile <- actual residual controller representations/implementations
EFFECT_EXECUTION: EffectProfile <- actual realization mechanisms
DERIVATION: sources -> new representation, actual producers, optional TransformContract
```

Delivery records identify destinations. Do not create another recipient-security list solely for logging. Do not infer control, production or execution roles from ownership, enclosure or historical participation.

The prototype's EffectProfile binds whether the invocation material and caller retain consequential control, additional explicit controllers/executors, and publication boundaries. These are properties of the exported execution contract, never per-call overrides. A profile that mediates a choice must actually implement that behavior. Kernel does not infer mediation from an acknowledgement or payload. Caller-declared additional semantic controllers can add facts, not remove the profile's required roles. The active Agent is used for an Agent selection; Operation behavior uses that Operation's bound realization identity rather than its owning Module's unrelated Assurance.

## Evaluation

For every real disclosure:

```text
Sensitivity(material) <= min(PrivacyCapacity(actual serial boundaries))
```

For effectful transitions:

```text
EffectProfile.control_risk <= min(Assurance(actual residual controllers))
EffectProfile.effect_risk <= min(Assurance(actual executors), profile realization Assurance)
```

The empty controller set has neutral Assurance 5. A real disclosure resolves a nonempty boundary path. The selected profile always contributes realization Assurance. Unresolved references, incompatible roles, missing required facts, altered bindings and omitted bound profile relationships fail structurally.

Admission requires structural validity and all applicable predicates. Raising demand cannot improve admission; raising applicable capacity cannot worsen it. No numerical monotonicity assumption is made about Autonomy.

One strong component cannot compensate for a weak component that still occupies the same causal role. A source whose influence has been mediated into a new representation remains provenance and is not automatically a direct controller. If it independently still determines the effect, it remains a controller.

## Representations and transformations

Ordinary derivation cannot increase Assurance above its actual source/producer basis. Ordinary forwarding/repackaging cannot lower Sensitivity; classification reduction requires a concrete TransformContract execution. Forwarding an unchanged representation adds delivery rather than new production.

A Module publishes a TransformContract bound to implementation/version, contract identity and evidence schema. Its transform behavior executes on concrete material and determines the resulting representation, Sensitivity and Assurance according to that contract. The SDK constructs the new immutable material and derivation; broker completion binds the exact dispatched transform, source ancestry and output. The prototype accepts one source representation per invocation; a ContextBundle can represent multiple source materials with explicit ancestry.

There is no generic validator role, transform strength, classification decrement or universal function of input Sensitivity. Different outcomes from equally sensitive sources may have different classifications. A real transformation may leave both numerical projections unchanged. No production transform catalogue is required.

Kernel verifies participation, digest/representation continuity and contract identity; semantic correctness stays with the transformation owner. A claimed transform-derived representation cannot cross another Kernel boundary until that execution completes. Completed output can be reused after restart. Completion evidence for one output cannot endorse a different output or transform.

Derivation identities are canonical, unique per output and acyclic. Identical repetition is idempotent. Conflicting ancestry fails. Ephemeral invocation identifiers do not enter durable representation/derivation identity.

## Feasible profiles and composition

The bound Operation SDK client exposes non-executing `evaluate_profiles`. For an already selected semantic Operation, the broker constructs a distinct prospective transition for each real profile using its own relationships, boundaries and risk envelope.

Results expose each decision and its limiting operands, feasible IDs, maximum feasible Autonomy, and every feasible ID at that maximum. Identifier sorting is deterministic presentation, not semantic choice. Ties return to the Module. There is no synthetic intermediate profile, automatic effect retry or Kernel selection of another semantic Operation.

Actual invocation rechecks the selected contract and attachment. Inspection creates no realized disclosure/effect history. Failure evidence lets Module behavior choose another representation, transform, mechanism, profile or Operation—or stop. Kernel does not prescribe semantic remediation.

## Persistence and valuation

Security objects, transitions and derivations use V2 identities; history and decisions identify V2. Incompatible V1 storage is rejected without deletion or migration. Stored immutable contracts and accepted relationships preserve retry/restart decisions. Rejected prospective transitions remain decision evidence and cannot be admitted as realized history.

Kernel persists only public execution/security metadata, opaque references, digests and completion evidence—not private input/output payloads. Runtime history is not Module memory.

Declarations, adapter/platform facts or a separate valuation mechanism establish security facts. Locality alone supplies no PrivacyCapacity; registration and credentials supply no permission. This run defines neither valuation infrastructure nor authentication.

## Required separation cases

The acceptance suite must distinguish containment from actor Assurance; control risk from both Autonomy and effect risk; independent realization failure; concrete transform outcomes from generic relabeling; ancestry from residual control; and real feasible profiles from synthetic or arbitrarily tie-broken alternatives. Exact publication, representation and transformation substitution must fail, including across restart.

Add a new projection only for a concrete MADRE pair that existing observable facts cannot distinguish but that needs different decisions. Removing a projection likewise requires preserving the distinctions MADRE actually uses, not merely preserving results of an already narrowed evaluator.
''')
p='MADRE.md';s=rd(p);start=s.index('### 6. Security');end=s.index('### 7.',start)
s=s[:start]+'''### 6. Security composes bounded contracts

MADRE Security Algebra V2 evaluates concrete material representations, disclosure boundaries, transformations and bounded Operation EffectProfiles. Encapsulation narrows the exposure/effect contract; it does not add abstract security points to an application or Agent.

Material carries Sensitivity and Assurance. Concrete disclosure boundaries carry PrivacyCapacity. Actual causal/realization participants contribute Assurance. EffectProfiles bind `control_risk`, `effect_risk`, Autonomy and realization Assurance. `1 <= control_risk <= effect_risk <= 5`.

```text
Sensitivity(material) <= minimum PrivacyCapacity of actual disclosure boundaries
control_risk <= minimum Assurance of actual residual controllers
effect_risk <= minimum Assurance of actual executors and profile realization
```

Autonomy orders real feasible profiles and is not an operand in these predicates. Tied profiles remain a Module decision. Kernel does not synthesize execution profiles or choose semantic Operations.

Module/Agent/endpoint identity does not automatically create a numerical role. Bound execution facts prevent substitution of the active actor or contract. Immutable representations, actual accepted crossings and transformation relationships support continuity; unrelated history never enters later numerical reductions.

Transformations are Module-owned bound processes producing new representations and security contracts. Kernel verifies execution/representation continuity without interpreting private classification or transformation semantics. Ordinary derivation cannot manufacture stronger Assurance or reduced Sensitivity. There is no generic validator role or transform-strength arithmetic.

Identity, registration, installation, credentials and previous success grant no MADRE permission. Security facts come from their appropriate declarations/valuation boundary; the evaluator does not invent missing values or equate local execution with containment.

''' +s[end:]
s=s.replace('must carry sufficiently strong Privacy and Integrity characteristics','must provide sufficiently strong disclosure-boundary PrivacyCapacity and role-specific Assurance')
s=s.replace('lower-Privacy','lower-capacity disclosure')
wr(p,s)
p='docs/architecture/MADRE-agent-interoperability.md';s=rd(p).replace('Sensitivity and Integrity','Sensitivity and Assurance')
start=s.index('Conceptually:\n\n```text\nEffectProfile');end=s.index('## 7.',start)
s=s[:start]+'''An EffectProfile binds a `RiskEnvelope(control_risk, effect_risk)`, Autonomy, realization Assurance and its concrete public control/execution/disclosure relationships. Profiles describe actual implementations; callers cannot override these facts. User participation changes the bound execution shape, not an authorization bit.

The broker evaluates every real profile separately when requested. It returns decisions, feasible IDs and all highest-Autonomy ties. Kernel does not choose tied semantic alternatives, invent profiles or retry effects. Invocation revalidates the selected publication and contract.

An Operation may internally use deterministic code, services, Workflows or inference. Those semantics remain Module-owned. The exported bounded effect, not the owning application's full power, is evaluated.

''' +s[end:]
s=s.replace('Security-relevant transformation produces a new representation/security binding rather than mutating the source. A Module may therefore create a minimized representation with lower Sensitivity or an explicitly validated representation with stronger Integrity when its own semantics and validation path justify doing so.', '''A Module-owned TransformContract binds a concrete transformation implementation/version and evidence schema. Execution produces a new representation and MaterialContract; broker completion binds its actual sources, transform and output. This can establish different Sensitivity or Assurance according to the transform's semantics. Ordinary derivation is constrained by source/producer Assurance and cannot reduce Sensitivity. There is no generic validator role.

The SDK provides `Transform`, `TransformBehavior`, `TransformOutput` and a bound transform client. Transform behavior receives material and execution services; consumers cannot supply a result classification in place of executing it. No particular transform is part of the initial production SDK catalogue.''')
s=s.replace('sufficiently strong Privacy and Integrity characteristics','sufficiently strong boundary PrivacyCapacity and role-specific Assurance')
s=s.replace('Participant Privacy/Integrity and material Sensitivity','Boundary PrivacyCapacity, participant Assurance and material Sensitivity')
s=s.replace('before sending or using them through another transition when its semantics permit such a transformation.', 'through concrete bound transformation execution before sending or using the resulting representation through another transition.')
s+='''

## Execution-bound SDK services

Module entry binds `ExecutionServices` to the actual executing Agent or Operation and the attached disclosure route. Agent/Operation/Transform nested calls cannot override that context through their arguments or supplied history. Unbound configurations cannot execute; handles expire on return, failure or cancellation. Independent invocations do not share ambient state.

An `EndpointBinding` names the forwarding attachment and its actual disclosure boundaries. It is not automatically a security participant. Additional producing/realizing components are declared only where the endpoint performs those roles. The active behavior contributes to production when creating a new representation; unchanged delivery adds no producer.
''';wr(p,s)
p='docs/architecture/MADRE-platform-architecture.md';s=rd(p).replace('Privacy and Integrity characteristics','boundary PrivacyCapacity and role-specific Assurance').replace('EffectProfile/transition builders','EffectProfile/transition builders and bound transformation execution')
s=s.replace('actual disclosure/control/effect participants','actual disclosure boundaries and residual control/effect participants');wr(p,s)
p='docs/architecture/MADRE-execution-contract.md';s=rd(p)
s+='''

## V2 security execution contracts

Physical Capability descriptors bind computation Assurance separately from their explicit disclosure-boundary contracts. The route evaluates boundary PrivacyCapacity against the concrete material Sensitivity. `local`, `isolated` and `remote` are execution metadata, not implicit numeric valuations. Rejected mechanisms remain prospective evidence; only the selected path becomes realized history.

Generated results carry source/producer IDs and output Assurance constrained by the actual computation basis. Material transformations may establish a different resulting contract only through their bound execution path. Kernel verifies representation and completion continuity; Module implementations own semantic classification.

V2 immutable objects, transformations and accepted transitions survive restart/retry without being revalued from current registry metadata. Incompatible V1 storage is rejected; no migration or silent deletion occurs. Profile feasibility is inspection, not execution or an accepted crossing.
''';wr(p,s)
wr('docs/design-memory/security-algebra.md','''# Security composition rationale

Non-normative rationale. Current semantics live in `docs/architecture/MADRE-security-algebra.md`.

The enduring Owner requirement is useful deterministic composition across independent Modules without turning MADRE into an application owner, permission matrix or generic Agent framework. Bounded Operations and representations make that possible. The SDK materializes public contracts; Kernel stays payload-opaque.

V2 distinguishes containment from internal participation, residual control consequence from user involvement, and transformed representations from historical provenance. Autonomy orders actual feasible execution profiles. A failed route returns useful evidence for Module-owned adaptation rather than forcing every internal decision back to the user.

The earlier five-concept freeze and its universal control-demand equation are superseded. Their derivation and discarded schemas remain in Git history, not parallel normative instructions. Exhaustive numerical consistency does not establish that a chosen projection preserves every MADRE architectural distinction.

Preserve identity only for routing, immutable contract binding, actual role resolution and execution continuity. Preserve derivation to distinguish completed transformation from relabeling, not to impose all ancestors as later numerical operands. A forwarding wrapper earns no security value solely by existing.

Valuation, sandbox implementation, domain classification and provider mechanics remain at their respective integration/semantic boundaries. None requires Kernel to infer private meaning or build a universal policy language.
''')
p='docs/design-memory/core-and-interaction.md';s=rd(p).replace('strongest applicable isolation/privacy/trust characteristics defined by the final security algebra','applicable disclosure-boundary PrivacyCapacity and role-specific Assurance defined by V2').replace('strongly isolated/trusted actor','participant inside a strong containment boundary');wr(p,s)
p='README.md';wr(p,rd(p).replace('explicit `privacy` and `integrity` levels','explicit boundary `privacy_capacity` and computation `assurance` levels'))
p='madre.example.toml';wr(p,rd(p).replace('privacy =','privacy_capacity =').replace('integrity =','assurance ='))
wr('docs/implementation-baseline.md','''# MADRE implementation baseline

This describes executable implementation, not additional product authority. `MADRE.md` and `docs/architecture/` own meaning.

## Runtime foundation

The local Python Kernel provides transient inference, reference-only durable work, JIT Module material resolution, eligibility/scheduling, scarce-resource admission, cancellation, retry/recovery and transient result delivery. SQLite stores execution metadata, immutable public security contracts and digests rather than private input/output content. Capabilities remain replaceable physical mechanisms; provider mechanics stay in adapters.

Module-owned Agents, Operations and transforms use the public SDK. CORE is an ordinary replaceable Module using inference/delegation/durable services; its semantic behavior remains outside Kernel.

## Security Algebra V2

The evaluator applies only:

```text
Sensitivity <= min(actual boundary PrivacyCapacity)
control_risk <= min(actual residual controller Assurance)
effect_risk <= min(actual executor Assurance, profile realization Assurance)
```

The RiskEnvelope enforces `1 <= control_risk <= effect_risk <= 5`. Autonomy orders real feasible profiles; equal Autonomy does not imply equal risk. Missing references, malformed bindings, incompatible roles, conflicting/cyclic derivations and denied realized history fail structurally.

Module/Agent objects have no Privacy field. Endpoint attachments identify the exact publication, disclosure boundaries and any independently contributing producer/executor implementations. Forwarding wrappers do not become SecurityObjects. Module ownership does not introduce another numeric role.

`InvocationContext` records the established actor and route. Module-created `ExecutionServices` bind nested SDK clients to it; caller history cannot narrow it. Handles expire at execution exit. Operation behavior uses its bound profile identity; routing Module identity is not substituted for that implementation's Assurance.

`OperationBrokerClient.evaluate_profiles` inspects each exported profile's own transition without dispatch or realized-history mutation. The result gives each decision, feasible IDs, maximum feasible Autonomy and all highest-profile ties. Actual invocation revalidates the selected contract and attachment. Identifier ordering never selects a semantic winner.

## Representation and transformation execution

Ordinary derivation preserves a source/producer Assurance ceiling and cannot reduce Sensitivity. Generated Capability results preserve their actual source/producer basis. Unchanged forwarding retains the original representation.

Modules can publish `TransformContract`s and implement `TransformBehavior`. The bound transform client dispatches the exact contract; Module infrastructure constructs the resulting material and derivation from the behavior's concrete output. Broker completion binds sources, transform identity and output. Semantic classification belongs to the transform implementation; there is no generic validator role or production transform catalogue.

Transform-derived output must complete before reuse across another Kernel boundary. A completed nested output can be forwarded unchanged. Completion for one output cannot endorse another output or contract. Unique acyclic ancestry and immutable hashes survive persistence/reopen.

The prototype currently accepts one material representation per transform invocation; multi-source ContextBundles carry explicit ancestry. Scalar projections are not semantic equivalence proofs. The SDK execution binding is not isolation from arbitrary Python/private infrastructure access.

## Persistence format

Security identities, transitions and derivations use V2 prefixes; decisions/history identify version 2. The SQLite format fingerprint includes the security version. Incompatible prior storage raises an error and remains intact; use a fresh development data directory. No migration or V1 compatibility branch exists.

## Focused evidence and integration gate

The V2 suite covers all valid 1..5 combinations of control risk, effect risk, controller Assurance, realization Assurance and Autonomy, plus every disclosure Sensitivity/capacity pair. Integration regressions cover containment/egress, per-profile topology and ties, exact execution binding, concrete transformation classifications, unchanged projections, rejected relabeling, substitution, stale publication, nested completion and restart.

Focused algebra/topology, broker, execution-binding, SDK/CORE, architecture-boundary and runtime-lifecycle tests are the local acceptance set. Changed Python files receive Ruff lint/format checks and source mypy checking. Test outcomes are reported with the implementation handoff.

Broader repository validation, packaging and CI have not been claimed by this run. They are required before integration. GitHub/PR/integration work is deliberately excluded from this implementation run.
''')
