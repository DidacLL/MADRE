# MADRE implementation baseline

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
