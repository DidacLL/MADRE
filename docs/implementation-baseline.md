# Implementation Baseline

The active implementation is one Java 21 Gradle multi-project system. Windows and
Linux run the same application, Kernel, SDK, persistence model and Module installation
mechanism. There is no separate Windows compatibility implementation and no
Linux-specific public runtime.

The current artifact boundaries are:

- `madre-algebra`: dependency-free nominal Security Algebra carriers;
- `madre-sdk`: typed Material, Module/Agent/Skill/Workflow/Operation model, executable
  Module binding/registration/provider contracts, codecs and public Module-facing ports;
- `madre-kernel`: live executable Module registry/public invocation, current Capability
  registry, deterministic selection, resources, immediate/durable physical execution,
  SQLite recovery and result delivery;
- `madre-text-inference`: typed physical text-inference command/result contract;
- `madre-web-search`: current typed physical web-search command/result contract;
- `madre-adapter-llamacpp`, `madre-adapter-openai-compatible`,
  `madre-adapter-searxng`: current physical adapters;
- `madre-module-owner-interaction` and `madre-module-web-search`: shipped ordinary
  Modules packaged as installable Module JARs;
- `madre-app`: installable assembly, Module discovery, connector configuration and a
  replaceable local console.

## Security Algebra baseline

The exact carrier values are:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Ranks and intrinsic composition are unchanged. Sensitivity combines by maximum;
Privacy and Integrity by minimum; information reaches a receiver iff
`Sensitivity <= Privacy`; one EffectProfile's Risk/Autonomy compose only against its
actual participants/physical realizers. No policy evaluator was added.

## Executable Module baseline

A running Module is registered as `ModuleInstance`, not as metadata alone. The instance
contains its canonical `ModuleDefinition` and an exact `OperationBinding` for every
declared Operation. Registration rejects incomplete, undeclared, foreign or
non-canonical executable surfaces.

`ModuleProvider` is the standard Java service-provider installation entrypoint. The
application discovers provider JARs from `modules.directory` (or the distribution's
sibling `modules/` directory) using JDK APIs. Shipped Modules are copied into that
directory during packaging but are not concrete `madre-app` compile dependencies.
Independent Module artifacts use the identical route.

`ModuleInvoker.invokePublic` resolves the installed Module and exact canonical public
Operation. Callers do not construct or know the concrete implementation class. Private
Operations and forged/mismatched calls are rejected.

Every public Operation binding owns a `PublicResultTransformer`. Before an internal
result crosses the external/public invocation boundary, the transformer must create
new declared Material with a new nominal identity and a Sensitivity able to reach
`Privacy.PUBLIC`. The transformed result must still satisfy the Operation's declared
output contract. Raw internal Material and insufficiently minimized replacement
Material are rejected. This is mandatory boundary enforcement around Module-owned
semantic transformation, not a generic policy evaluator.

`verification/sdk-consumer` is now an executable independent Module, not a compile-only
definition example. Its separate Gradle build depends only on the published
`io.github.didacll:madre-sdk` artifact. CI builds its JAR, installs it into a built
MADRE distribution, discovers it, invokes `phd.module/inspect`, expects `public:hello`,
and rejects any appearance of its internal `private:hello` Material.

## CORE and boot baseline

CORE is optional. `roles.core` may be absent. A configured but absent CORE remains an
unresolved optional role and does not prevent boot. No Operation name is required to
qualify platform startup. CORE assignment changes no invocation authority, Security
Algebra, visibility, scheduling or execution privilege.

Physical connectors are also optional at boot. A completely empty Capability registry
is a valid runtime state. An Operation that later requests an unavailable physical
mechanism fails or waits under the ordinary execution contract when invoked; MADRE does
not refuse to start merely because no reasoning connector exists.

The installed-Module runtime verification intentionally supplies only Kernel database,
Module directory and Module state directory properties. It therefore exercises
independent Module discovery/invocation with no CORE assignment and no connector.

## Cross-platform baseline

Windows and Linux remain equal validation targets. Executable/build-logic changes run
`check` on both hosts. Production-source changes additionally run Javadocs,
publication, `installDist`, `distZip`, installed-application smoke, and the independent
Module installation/invocation proof on both Windows and Linux when the SDK fixture is
in scope.

The Gradle application distribution generates `bin/madre` and `bin/madre.bat` for the
same Java application. Module discovery itself uses Java `Path`, `Files`, URL class
loading and `ServiceLoader`; it has no shell-specific installation behavior.

No container runtime, VM layer, orchestration system, hosted provider account or
external service is part of the mandatory build/test path.

## Current physical connectors and intentionally unrecovered debt

Capability availability remains observed physical state and Kernel selects only
explicitly available mechanisms. Existing adapters and their prior acceptance evidence
remain intact, including native Windows AF_UNIX llama.cpp qualification and live
WebSearch acceptance recorded on PR #47.

This slice does not endorse the current generic Capability architecture as final. The
known recovery debt is explicit:

- generic `Capability` must ultimately narrow to reasoning-only `ReasoningCapability`;
- SearXNG/WebSearch placement remains to be corrected;
- the universal physical-action Kernel dispatch path remains to be removed from
  ordinary Module/application behavior.

No new Memory, Knowledge, Communication, WebSearch, marketplace, model-management,
container, MCP, Kubernetes or Linux-only abstraction was introduced while materializing
the executable Module boundary.

`docs/master-development-plan.md` remains a historical record of the earlier delivery
plan. Its statements that executable Module loading/public invocation are future work
are superseded by this baseline and the current executable architecture documents.

## Verification and acceptance state

Deterministic tests cover Security Algebra ranks, executable binding validation,
PUBLIC call resolution/rejection, mandatory public-result minimization, optional CORE,
boot with no connectors, SQLite Kernel runtime, physical Capability selection/recovery,
and the existing adapter/Module behavior.

The previous physical acceptance evidence on this PR remains valid: cross-platform
mechanical build/package runs, native Windows AF_UNIX llama.cpp transport and real
model inference, and the live SearXNG/WebSearch/deep-search acceptance are unchanged by
this Module installation slice.

The final cross-platform run for the executable Module installation/public invocation
slice is recorded in the PR handoff/comment once its exact head has completed. Do not
infer live evidence from documentation alone.
