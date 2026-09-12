# MADRE Execution Contract

## Immediate execution

A Module creates an `ExecutionRequest` containing:

- the requesting Module identity;
- the actual input Material;
- a typed `CapabilityQuery`;
- a `MaterialSpecification` for the new physical output;
- mechanical execution constraints.

The output specification belongs to the requesting Module, has an identity different
from the input Material, names its content contract, and supplies its exact applicable
security facts.

Kernel selects one Capability using only the typed physical properties in the query.
Selection does not inspect Material bytes or Security Algebra history.

The selected Capability joins the request through ordinary construction of a
Disclosure from:

- the input Material’s actual source surface;
- the Capability’s actual observer surface.

If those surfaces do not match, construction raises `SecurityMismatch` and the
request ends before physical execution. Kernel does not search for a security-approved
candidate, persist the denial, or modify the request.

If they match, Kernel reserves the Capability's resource slot, passes the opaque payload
to its adapter, and receives opaque output. Kernel constructs the specified new
Material and returns it to the requesting Module.

A Capability result cannot encode an executable continuation. Even when its bytes
mention an Operation, tool, target, or Kernel request, those bytes are ordinary
Material. Only Module behavior may interpret them and make another explicit call.

## Module-owned continuation

After receiving output Material, a Module may:

- return or present it;
- store it in Module-owned state;
- create another Material with a new identity and applicable facts;
- make another physical execution request;
- invoke one of its own bounded Operations;
- stop.

These are Module decisions. Kernel and Capability expose no universal continuation,
assistant turn, delegation, planning, or tool-use semantics.

## Durable execution

Durable work preserves execution intent without storing private Material bytes:

```text
WorkSubmission -> WorkRecord -> WorkAttempt
```

A submission carries a Material handle rather than payload. The handle binds Material
identity, contract, digest, and its exact security scope. It is not security history.

Kernel persists:

- requesting Module identity;
- Capability query;
- Material handle;
- output Material specification;
- eligibility, priority, timeout, and idempotency metadata;
- attempt lifecycle, selected Capability identity/boundary, compact lifecycle failure
  code and retry disposition, output digest/size, and delivery state.

Kernel does not persist:

- input or output payloads;
- prompts, private context, Agent state, or WorkPlans;
- Disclosure, Control, EffectExecution, SecurityScope, or SecuritySurface records
  beyond the exact scope embedded in the reference contract;
- mismatches, rejected candidates, security decisions, histories, or lineage;
- provider error bodies or private adapter data.

When work becomes eligible, Kernel resolves the exact Material from its owning Module,
checks the handle and digest, constructs the current request, and performs the same
immediate execution path. Retry, cancellation, idempotency, scheduling, and restart
recovery are lifecycle mechanics only; none changes security facts.

If algebraic construction fails, the detailed `SecurityMismatch` remains transient.
Durable work records only a generic terminal lifecycle failure; the same request
cannot be retried. The Module must submit a different request after changing the
responsible Material, surface, or action.

Produced payload bytes remain transient in the current process until consumed. A
restart before consumption marks delivery as lost.

## Capability selection

`CapabilityQuery` and `CapabilityProperties` use typed values for specialization,
modality, execution boundary, latency, reasoning effort, quality, cost, and resources.
A selection strategy is replaceable through a small interface.

The default strategy first requires an exact property match and then uses the
query-declared tuple order. It contains no provider-specific branches and never ranks
on Privacy, Integrity, reputation, authentication, or prior outcomes.

Execution boundary is a physical property only. It neither supplies nor changes
Privacy.

## Effect execution

Physical inference that returns Material does not itself realize a Module Operation.

When a bounded Operation is executed, the Module constructs its exact Control and
EffectExecution values from one declared EffectProfile, the actual non-user
controllers, and the actual executors. Invalid construction prevents the action.
EffectProfile values cannot be overridden by model output or request payload.

Operation dispatch and uncertain external-effect retry semantics will be implemented
only with a concrete Module-owned Operation path. They are not generalized into an
Agent broker or tool framework.
