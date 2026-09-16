# MADRE SDK developer guide

This guide describes the current Java 21 Module/reasoning-development surface for projects outside MADRE's own Gradle build. A released/installed MADRE carries the compatible public Maven development repository in its application payload under `developer/repository`, plus this guide as `developer/README.md`. An independent developer does not need the MADRE source checkout or a repository produced by running MADRE's source build.

For a jpackage app image the repository is relative to the image root as:

```text
Windows: <madre-app-image>/app/developer/repository
Linux:   <madre-app-image>/lib/app/developer/repository
```

Native installers preserve the same application payload inside the installed product. Point the independent build at that product-carried directory. MADRE currently has no remote artifact registry/catalog/update channel; this local version-matched repository is the supported source-independent acquisition surface.

## Design rule

A Java Module is ordinary software first. Its executable `Module` and optional `Agent` objects are the author's source of truth. MADRE derives portable descriptions rather than asking the developer to maintain a second definition graph.

Execution-side objects:

- `Module`;
- optional `Agent` / `StatefulAgent<S>`;
- optional `OwnerInteractionAgent` for a Module that can own the default CORE semantic surface;
- `OwnerMessage` for Agent-approved owner-visible semantic messages;
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

`OperationDefinition` is deliberately non-generic and contains bounded execution facts only. Java payload typing belongs to `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>`. `ModuleInstance` is validated runtime/adaptor assembly and normally is not constructed by a Module author. `OwnerInteractionAgent` is execution-side only; it is not serialized into the portable Module definition.

## Operations are bounded Module logic

An Operation is not an LLM primitive and not a universal workflow construct. It is one bounded callable execution that the Module places under MADRE arbitration.

An Operation may calculate, read/write application state, call HTTP or a database, run a process, invoke reasoning, call another Module through its exposed interface, or combine ordinary Java code.

MADRE arbitrates the declared boundary: typed Material, accepted receiving Privacy, output Sensitivity bounds and an EffectProfile when that exact consequential variant requires Risk/Autonomy arbitration.

Use `OperationCall.withoutEffect(...)` when no EffectProfile is declared. Use `OperationCall.withEffect(...)` with one exact declared profile and actual non-user causal Integrity participants when consequential behavior is being selected.

PUBLIC/PRIVATE is not part of `OperationDefinition` and there is no `OperationVisibility` type. Ordinary cross-Module exposure is Module-exposed behavior, not `Privacy.PUBLIC` behavior.

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

`ownerInteractionOperation(...)` marks an exact Operation as eligible for the host/runtime selected-CORE interaction invoker. It is a lower-level bounded execution mechanism that an `OwnerInteractionAgent` may use internally. The runtime additionally requires that the owning Module be the installed Module assigned `roles.core`. This does not expose the Operation to other Modules, does not make it externally public, does not define the normal owner API and does not change Security Algebra.

`publicDisclosure(...)` provides the Module-owned transformation required when information actually crosses the external/public receiver boundary. The transformer must create new declared Material with a new identity and Sensitivity capable of reaching `Privacy.PUBLIC`. Whether that Operation is also in `exposedOperations()` is independent.

Most domain Operations need only `operation(...)`. Add Module exposure when other installed Modules should discover/invoke the capability. Add an owner-interaction binding only when a CORE Agent genuinely needs that bounded entry; add public disclosure only for the external receiver boundary.

## Artifact roles

The packaged `developer/repository` contains exactly the public MADRE development artifacts for that product version:

- `madre-bom`: compatible public artifact versions;
- `madre-algebra`: Security Algebra carriers;
- `madre-sdk`: stable Module authoring/composition/reasoning-facing contracts;
- `madre-sdk-testkit`: deterministic contract tests without Kernel/application internals;
- `madre-sdk-experimental`: explicit 0.x incubation namespace;
- `madre-reasoning-spi`: reasoning-provider SPI, normally not a Module dependency;
- `madre-text-inference`, `madre-text-generation`, `madre-embeddings`: provider-independent reasoning computation contracts.

Concrete adapters, `madre-kernel` and `madre-app` are runtime/product implementation artifacts. They are not published into the packaged development repository and are not normal extension dependencies.

Technical reuse does not create Module ownership. Files, databases, HTTP, search, MCP, ML libraries, application APIs and devices may remain ordinary implementation facilities inside the Module that owns their semantic use.

## Independent Gradle project

Choose the `developer/repository` directory from the installed/released MADRE you intend to extend. For example:

```text
MADRE_REPOSITORY=/path/to/installed/madre/application-payload/developer/repository
```

Then an independent Gradle project can use:

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

Build with the product repository explicitly:

```text
gradle -PmadreRepository="$MADRE_REPOSITORY" clean check jar
```

`verification/sdk-consumer` is the executable external-Module reference. `verification/reasoning-consumer` is the corresponding independently built reasoning-provider reference. `verification/r4-workspace-module` is an additional independently built agentless application used to prove runtime composition with a Module that the shipped CORE did not know at build time. Cross-platform extended acceptance first builds the packaged MADRE, then copies its `developer/repository` into a source-free workspace, removes checkout-local publication output, builds the independent artifacts from that packaged public surface, and installs/uses them against that same already-built MADRE.

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

A Module may explicitly reference a foreign Material type contract and create a value conforming to it. For Java authors a structural compile-time reference is declared with `foreignMaterialReferences()`. The set is a foreign nominal-contract reference; it is not `Privacy.PUBLIC` disclosure and does not transfer concrete value ownership.

Runtime discovery also supports an exact target-owned contract that the caller did not know at build time. `ModuleDirectory.exposed(Sensitivity)` returns exposed Operation definitions together with the target-owned `MaterialTypeDefinition`s needed to understand their contracts. A caller may then create caller-owned concrete Material conforming exactly to a target-owned input definition published by that target. This dynamic relationship is scoped to the target being invoked and does not add the contract to the caller's portable definition.

Likewise, an invoked target may return target-owned Material using one of its own canonical nominal definitions even when the caller did not statically declare that type. If its Sensitivity can reach the fixed `Privacy.MODULE` receiver, the exact target-created Material crosses unchanged. A foreign type owned by some unrelated third Module still requires the caller's structural `foreignMaterialReferences()` declaration.

For the static case, a callee may also produce a callee-owned `MaterialId` whose `MaterialTypeId` is defined by the caller. If that type is declared structurally and Sensitivity can reach `Privacy.MODULE`, the exact callee-owned value can cross unchanged.

## Agents, typed state, Skills and Workflows

Use an `Agent` when the Module genuinely owns a semantic actor. Agent is optional and defines no universal loop, planner, memory model, prompt format or execution context.

If the author cannot justify stronger causal assurance, `Agent.integrity()` defaults to `Integrity.I1`. A stronger value must have a concrete assurance basis; locality, shipped status, CORE assignment or being built into the product are not such a basis.

A stateful Agent may extend `StatefulAgent<S>` to reuse serialized typed state reads/transitions and optional commit-before-publish persistence. The concrete Module owns state meaning, representation and persistence semantics.

Agent state does not create another MADRE-arbitrated execution path. Behavior that should participate in MADRE arbitration still executes through `OperationDefinition`, `OperationBinding` and `OperationCall`.

`SkillDefinition` remains lightweight ability/knowledge/instruction metadata. `WorkflowDefinition` is currently an Agent-owned ordered Operation description, not a Kernel scheduler language.

## Owner interaction

The normal owner semantic path is separate from generic host/debug invocation, external/public disclosure and Module composition.

A Module that can serve as the default CORE semantic application may expose one `OwnerInteractionAgent` from its ordinary Agent collection. The host selects only the Module through `roles.core`, transports ordinary owner text to that Agent and presents returned `OwnerMessage` values. It does not need to know which private Operation, Material type, reasoning path or continuation mode represents that turn.

`OwnerInteractionAgent.respond(OwnerInteractionInvoker, ownerText, sensitivity)` owns the immediate semantic decision. `followUps(...)` lets the same Agent surface delayed messages it has semantically approved. The supplied invoker is already restricted to exact `ownerInteractionOperation(...)` bindings in the selected CORE Module; it is not a general host privilege and is not present in `ModuleContext`.

An Agent in CORE may reach capabilities of an agentless Module through the target Module's ordinary exposed interface using the same caller-bound `ModuleContext.directory()` and `ModuleContext.invoker()` available to every Module. CORE interprets owner intent, selects only among structurally exposed contracts, supplies its actual causal Integrity when selecting a consequential target EffectProfile, interprets the returned foreign Material, and decides continuation. The callee does not need an Agent merely to be callable, and CORE designation grants no additional discovery or invocation authority.

Do not copy the shipped CORE's private coordination Operation, selection prompt, knowledge parser/categories or background protocol into a new CORE contract. Current console representation, private semantic knowledge experiment, coordination strategy and polling are implementation evidence, not universal SDK protocol.

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

There are two directory queries for two authoring situations:

```java
List<ReachableModule> exact = context.directory().reachable(
        new ReachabilityQuery(knownTypeId, carriedSensitivity));
List<ReachableModule> exposed = context.directory().exposed(carriedSensitivity);
```

`reachable(...)` is the exact path when the nominal input type is already known. `exposed(...)` is structural discovery for independently installed Modules that may not have existed when the caller was compiled. It returns only Module-exposed Operations whose declared input receiver can accept the supplied carried Sensitivity, includes target-owned Material definitions needed to understand those contracts, and performs no semantic ranking. Neither form means `Privacy.PUBLIC`.

Invocation still targets an exact discovered `OperationDefinition`. The caller may use one of its own/static foreign nominal contracts or an exact target-owned input definition obtained from that target's runtime discovery result. Concrete input Material remains owned by the caller through its `MaterialId` even when the nominal `MaterialTypeId` is target-owned.

Returned Material crosses the fixed Module receiver `Privacy.MODULE` only if its Sensitivity can reach that receiver and its nominal contract is structurally valid. Statically referenced foreign/third-party contracts remain governed by `foreignMaterialReferences()`. A target-owned result from the invoked target can instead be validated against that target's installed canonical Material definition, allowing a caller compiled earlier to receive a newly installed target's own result type. The exact Material remains owned by the Module that produced it; the runtime does not relabel or public-transform it.

Consequential target calls must select one exact target-declared `EffectProfile` and supply the actual non-user causal Integrity participants. Semantic selection/reasoning before that target call does not itself acquire the target's Risk or Autonomy.

Generic owner/debug invocation is a separate host-only expert path and may invoke an exact installed Operation without depending on cross-Module exposure. External/public invocation is another host-only path and requires an explicit `publicDisclosure(...)` binding. Ordinary owner interaction is a third product path through the selected CORE Agent; the restricted owner-interaction invoker remains that Agent's lower-level bounded execution route for its own Module.

Security Algebra governs MADRE-mediated information/effect composition. Module exposure, host entry and CORE designation are not Algebra carriers.

## Reasoning

A Module requests reasoning through `ReasoningService` using a typed `ReasoningComputation<R>` derived from a valid bounded `OperationCall`. The Module does not select a concrete provider implementation.

`ReasoningRequest` derives originating Module and carried Sensitivity from the call. If several inputs contribute to context, construct the actual contextual Material at combined maximum Sensitivity first. There is no raw Sensitivity override.

Any Operation may request reasoning. Mechanism eligibility depends on computation compatibility, actual Material Sensitivity, mechanism receiving Privacy, availability/preferences and resources—not on Module exposure or host entry.

Kernel owns compatible selection, resources, immediate/durable execution, retry/cancellation and opaque persistence. The Module owns interpretation and continuation. Provider/model/runtime-specific tuning remains in the provider/adapter.

## Independent reasoning provider

A reasoning provider uses the same product-carried repository but depends on the public provider surface rather than runtime implementation:

```kotlin
dependencies {
    implementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    implementation("io.github.didacll:madre-reasoning-spi")
    implementation("io.github.didacll:madre-text-inference")
}
```

The provider JAR exposes `ReasoningMechanismProvider` through `ServiceLoader`. It must not compile against `madre-app`, Kernel implementation or concrete adapters. Install it through the ordinary reasoning lifecycle, then configure/enable a provider-owned mechanism instance with `madre reasoning ...`.

## Deterministic contract tests

`madre-sdk-testkit` materializes a real `ModuleProvider` and invokes public SDK contracts without pretending to reproduce Kernel policy. `ProgrammableReasoningService` is generic over arbitrary `ReasoningComputation<R>` and exposes controllable immediate/durable behavior.

Use ordinary unit/testkit tests for Module semantics and installed/integration tests when a claim depends on Module receiver exposure, dynamic discovery, Kernel mechanism selection, durable recovery or package/product mechanics.

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

## MADRE source-development note

A MADRE source checkout still uses the repository's own Gradle publication tasks for implementation verification:

```text
./gradlew --no-daemon publish
```

That checkout-local `build/isolated-repository` is an implementation verification facility, not the independent developer acquisition path. Extension authors should consume the `developer/repository` carried by the installed/released product they target.

## Experimentation posture

The stable SDK should remain small but not artificially lowest-common-denominator. Optional OOP conveniences can stabilize when real software proves one generic responsibility and focused tests preserve MADRE invariants.

Do not introduce a universal Agent loop, planner/tool framework, memory system, semantic database, workflow scheduler, arbitrary metadata tree or Module taxonomy merely because one experiment uses those techniques. Build substantial Modules first, then extract the smallest orthogonal SDK concepts that make them easier to author.
