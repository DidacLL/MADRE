# MADRE SDK developer guide

This guide describes the current Java 21 Module-development surface for projects outside MADRE's own Gradle build. Verification publications are currently `0.1.0-SNAPSHOT` artifacts written by repository `publish` tasks to `build/isolated-repository`; this proves dependency isolation and is not yet a public remote release channel.

## Design rule

A Java Module is ordinary software first. Its executable `Module` and optional `Agent` objects are the author's source of truth. MADRE derives portable descriptions rather than asking the developer to maintain a second definition graph.

Execution-side objects:

- `Module`;
- optional `Agent` / `StatefulAgent<S>`;
- `MaterialType<T>` / `Material<T>`;
- `Operation<I,O>`;
- `OperationBinding<I,O>`;
- `OperationCall<I,O>`.

Portable descriptions:

- `ModuleDefinition`;
- `AgentDefinition`;
- `MaterialTypeDefinition`;
- `OperationDefinition`;
- `SkillDefinition`;
- `WorkflowDefinition`;
- `EffectProfile`.

`OperationDefinition` is deliberately non-generic and contains bounded execution facts only. Java payload typing belongs to `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>`. `ModuleInstance` is validated runtime/adaptor assembly and normally is not constructed by a Module author.

## Operations are bounded Module logic

An Operation is not an LLM primitive and not a universal workflow construct. It is one bounded callable execution that the Module places under MADRE arbitration.

An Operation may calculate, read/write application state, call HTTP or a database, run a process, invoke reasoning, call another Module through its exposed interface, or combine ordinary Java code.

MADRE arbitrates the declared boundary: typed Material, accepted receiving Privacy, output Sensitivity bounds and an EffectProfile when that exact consequential variant requires Risk/Autonomy arbitration.

Use `OperationCall.withoutEffect(...)` when no EffectProfile is declared. Use `OperationCall.withEffect(...)` with one exact declared profile and actual non-user causal Integrity participants when consequential behavior is being selected.

PUBLIC/PRIVATE is not part of `OperationDefinition` and there is no `OperationVisibility` type.

## Exposure and executable binding choices

Cross-Module exposure belongs to the Module:

```java
@Override
public Set<OperationId> exposedOperations() {
    return Set.of(INSPECT);
}
```

Use executable bindings for their actual receiver/product responsibilities:

```java
OperationBinding.operation(definition, implementation);
OperationBinding.ownerInteractionOperation(definition, implementation);
OperationBinding.publicDisclosure(definition, implementation, publicTransformer);
```

`operation(...)` is ordinary bounded execution.

`ownerInteractionOperation(...)` marks an exact Operation as eligible for the host's selected owner-interaction surface. The runtime additionally requires that the owning Module be the installed Module assigned `roles.core`. This does not expose the Operation to other Modules, does not make it externally public and does not change Security Algebra.

`publicDisclosure(...)` provides the Module-owned transformation required when information actually crosses the external/public receiver boundary. The transformer must create new declared Material with a new identity and Sensitivity capable of reaching `Privacy.PUBLIC`. Whether that Operation is also in `exposedOperations()` is independent.

Most domain Operations need only `operation(...)`. Add Module exposure when other installed Modules should discover/invoke the capability. Add owner-interaction or public-disclosure bindings only for those distinct boundaries.

## Artifact roles

Use only artifacts the Module genuinely needs:

- `madre-bom`: compatible public artifact versions;
- `madre-algebra`: Security Algebra carriers;
- `madre-sdk`: stable Module authoring/composition/reasoning-facing contracts;
- `madre-sdk-testkit`: deterministic contract tests without Kernel/application internals;
- `madre-sdk-experimental`: explicit 0.x incubation namespace;
- `madre-reasoning-spi`: reasoning-provider SPI, normally not a Module dependency;
- `madre-text-inference`, `madre-text-generation`, `madre-embeddings`: provider-independent reasoning computation contracts.

Concrete adapters, `madre-kernel` and `madre-app` are runtime/product implementation artifacts and are not normal Module dependencies.

Technical reuse does not create Module ownership. Files, databases, HTTP, search, MCP, ML libraries, application APIs and devices may remain ordinary implementation facilities inside the Module that owns their semantic use.

## Independent Gradle project

Publish verification artifacts from a MADRE checkout:

```text
./gradlew --no-daemon publish
```

Then an independent project can use:

```kotlin
plugins { java }

val madreRepository = providers.gradleProperty("madreRepository")
repositories {
    maven { url = uri(madreRepository.get()) }
    mavenCentral()
}

dependencies {
    implementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    implementation("io.github.didacll:madre-sdk")
    implementation("io.github.didacll:madre-text-inference")

    testImplementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    testImplementation("io.github.didacll:madre-sdk-testkit")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.test { useJUnitPlatform() }
```

`verification/sdk-consumer` is the executable external-project reference.

## Minimal agentless Module

A Module can have zero Agents. This example exposes one ordinary Operation to other installed Modules and independently defines a public-disclosure boundary for host external/public invocation.

```java
final class ExampleModule implements Module {
    static final ModuleId ID = new ModuleId("example.module");
    static final MaterialType<String> REQUEST = new MaterialType<>(
            new MaterialTypeId(ID, "request"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    static final MaterialType<String> RESULT = new MaterialType<>(
            new MaterialTypeId(ID, "result"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());

    static final OperationId INSPECT = new OperationId(ID, "inspect");
    static final OperationDefinition INSPECT_CONTRACT = new OperationDefinition(
            INSPECT, "Inspect one request",
            Map.of(REQUEST.id(), Privacy.SECRET),
            Map.of(RESULT.id(), Sensitivity.S4), Map.of());

    private final Operation<String, String> inspectImplementation = Operation.of(call ->
            CompletableFuture.completedFuture(new Material<>(
                    new MaterialId(ID, UUID.randomUUID().toString()),
                    RESULT, "internal:" + call.input().payload(),
                    call.input().sensitivity())));

    private final OperationBinding<String, String> inspect =
            OperationBinding.publicDisclosure(
                    INSPECT_CONTRACT,
                    inspectImplementation,
                    internal -> new Material<>(
                            new MaterialId(ID, UUID.randomUUID().toString()),
                            RESULT, "public:summary", Sensitivity.S1));

    @Override public ModuleId id() { return ID; }
    @Override public String version() { return "1.0.0"; }
    @Override public String purpose() { return "Example application"; }

    @Override public Collection<? extends MaterialType<?>> materialTypes() {
        return List.of(REQUEST, RESULT);
    }

    @Override public Set<OperationId> exposedOperations() {
        return Set.of(INSPECT);
    }

    @Override public Collection<? extends OperationBinding<?, ?>> operations() {
        return List.of(inspect);
    }
}
```

`module.definition()` derives the language-neutral contract and `module.instance()` derives the validated runtime assembly.

Cross-Module callers execute the ordinary binding and receive the contract-valid internal result unchanged when the Module receiver boundary permits it. Only host external/public invocation runs the `PublicResultTransformer` and receives the S1 summary.

If the Operation should not be cross-Module discoverable, omit it from `exposedOperations()`. That does not make the Operation a different kind of bounded execution.

## Foreign nominal Material contracts

Concrete Material value ownership is independent from nominal type ownership:

```text
MaterialId.moduleId      value creator/owner
MaterialTypeId.moduleId  nominal contract definer/owner
```

A Module may explicitly reference a foreign Material type contract and create a value conforming to it. For Java authors this is declared with `foreignMaterialReferences()`. The set is a structural foreign nominal-contract reference; it is not `Privacy.PUBLIC` disclosure and does not transfer concrete value ownership.

For example, a callee may produce a callee-owned `MaterialId` whose `MaterialTypeId` is defined by the caller. If that type is declared structurally and Sensitivity can reach `Privacy.MODULE`, the exact callee-owned value can cross unchanged.

## Agents, typed state, Skills and Workflows

Use an `Agent` when the Module genuinely owns a semantic actor. Agent is optional and defines no universal loop, planner, memory model, prompt format or execution context.

If the author cannot justify stronger causal assurance, `Agent.integrity()` defaults to `Integrity.I1`.

A stateful Agent may extend `StatefulAgent<S>` to reuse serialized typed state reads/transitions and optional commit-before-publish persistence. The concrete Module owns state meaning, representation and persistence semantics.

Agent state does not create another MADRE-arbitrated execution path. Behavior that should participate in MADRE arbitration still executes through `OperationDefinition`, `OperationBinding` and `OperationCall`.

`SkillDefinition` remains lightweight ability/knowledge/instruction metadata. `WorkflowDefinition` is currently an Agent-owned ordered Operation description, not a Kernel scheduler language.

## Owner interaction

The normal owner semantic path is separate from generic host/debug invocation and Module composition.

A Module that owns owner-facing semantic interaction can bind exact Operations using `ownerInteractionOperation(...)`. The host's `OwnerInteractionInvoker` may call such entries only when that Module is currently assigned `roles.core`.

An Agent in CORE may reach capabilities of an agentless Module through the target Module's ordinary `exposedOperations`. CORE interprets owner intent and continuation; the callee does not need an Agent merely to be callable.

Current console Operation names and `interaction.*` settings are product wiring, not universal SDK protocol.

## Provider lifecycle and configuration

An installable Module JAR exposes one canonical `ModuleProvider` through Java `ServiceLoader`.

```java
public final class ExampleProvider implements ModuleProvider {
    @Override public ModuleId moduleId() { return ExampleModule.ID; }

    @Override
    public Module create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        return new ExampleModule();
    }
}
```

Provider configuration is identity-scoped and Module-owned. The host currently supports the demonstrated field kinds `TEXT`, `INTEGER` and `CHOICE`; provider Java code is the final validator. Configuration discovery/validation does not materialize the Module.

The service descriptor is:

```text
META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

## Composition and Security Algebra

`ModuleContext.directory()` and `ModuleContext.invoker()` are caller-bound. Module code cannot forge caller identity or choose receiver Privacy.

Directory discovery/invocation targets only Operations in the target Module's exposed interface. Accepted input Privacy still determines whether the offered Material can reach a particular Operation.

Returned Material crosses the fixed Module receiver `Privacy.MODULE` only when the calling Module structurally declares the nominal contract and the result Sensitivity can reach that receiver. The exact concrete Material value remains owned by the Module that produced it.

Generic owner/debug invocation is a separate host-only expert path and may invoke an exact installed Operation without depending on cross-Module exposure. External/public invocation is another host-only path and requires an explicit `publicDisclosure(...)` binding. Owner interaction is a third host-only path through exact CORE interaction bindings.

Security Algebra governs MADRE-mediated information/effect composition. Module exposure, host entry and CORE designation are not Algebra carriers.

## Reasoning

A Module requests reasoning through `ReasoningService` using a typed `ReasoningComputation<R>` derived from a valid bounded `OperationCall`. The Module does not select a concrete provider implementation.

`ReasoningRequest` derives originating Module and carried Sensitivity from the call. If several inputs contribute to context, construct the actual contextual Material at combined maximum Sensitivity first. There is no raw Sensitivity override.

Any Operation may request reasoning. Mechanism eligibility depends on computation compatibility, actual Material Sensitivity, mechanism receiving Privacy, availability/preferences and resources—not on Module exposure or host entry.

Kernel owns compatible selection, resources, immediate/durable execution, retry/cancellation and opaque persistence. The Module owns interpretation and continuation. Provider/model/runtime-specific tuning remains in the provider/adapter.

## Deterministic contract tests

`madre-sdk-testkit` materializes a real `ModuleProvider` and invokes public SDK contracts without pretending to reproduce Kernel policy. `ProgrammableReasoningService` is generic over arbitrary `ReasoningComputation<R>` and exposes controllable immediate/durable behavior.

Use ordinary unit/testkit tests for Module semantics and installed/integration tests when a claim depends on Module receiver exposure, Kernel mechanism selection, durable recovery or package/product mechanics.

## Local Module lifecycle

Current owner lifecycle commands are local-file based:

```text
madre modules install /absolute/path/module.jar
madre modules install /absolute/path/replacement.jar --replace
madre modules list
madre modules inspect <module-id>
madre modules configure <module-id> [--set <field>=<value>]...
madre modules uninstall <module-id>
madre modules uninstall <module-id> --purge-configuration
```

Shipped artifacts are protected. MADRE-managed owner artifacts occupy deterministic slots. Manually copied JARs remain discoverable but are not silently adopted/replaced/deleted. Module semantic state is distinct from artifact bytes.

Reasoning-provider lifecycle remains a separate product domain under `madre reasoning ...`.

## Experimentation posture

The stable SDK should remain small but not artificially lowest-common-denominator. Optional OOP conveniences can stabilize when real software proves one generic responsibility and focused tests preserve MADRE invariants.

Do not introduce a universal Agent loop, planner/tool framework, memory system, semantic database, workflow scheduler, arbitrary metadata tree or Module taxonomy merely because one experiment uses those techniques. Build substantial Modules first, then extract the smallest orthogonal SDK concepts that make them easier to author.
