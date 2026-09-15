# MADRE SDK developer guide

This guide describes the current Java 21 Module-development surface for projects outside MADRE's own Gradle build. The verification publications are currently `0.1.0-SNAPSHOT` artifacts written by the repository `publish` tasks to `build/isolated-repository`; this is an executable dependency-isolation proof, not yet a public remote release channel.

## Design rule

A Java Module is ordinary software first. Its executable `Module` and optional `Agent` objects are the Java author's source of truth. MADRE derives the portable semantic/security description from those objects rather than asking the developer to maintain a second definition graph.

The public split is deliberate:

- `Module`, `Agent`, `MaterialType<T>`, `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>` are Java execution-side objects;
- `ModuleDefinition`, `AgentDefinition`, `MaterialTypeDefinition`, `OperationDefinition`, `SkillDefinition`, `WorkflowDefinition` and `EffectProfile` are portable semantic/security descriptions;
- `OperationDefinition` is intentionally non-generic. Java payload typing belongs to executable Java bindings, not to a language-neutral declaration;
- `MaterialTypeDefinition` contains nominal identity and content type. Java `Class<T>` and codecs belong to `MaterialType<T>`;
- `ModuleInstance` is the validated runtime/adaptor assembly joining one portable definition to exact Java Material and Operation bindings. Ordinary Module authors normally do not construct it.

This keeps the SDK suitable for ordinary developers, generated code and future non-Java adapters without weakening MADRE's nominal identities or Security Algebra boundaries.

## Artifact roles

Use only the artifacts the Module genuinely needs:

- `madre-bom`: compatible public artifact versions;
- `madre-algebra`: Security Algebra carriers;
- `madre-sdk`: stable Module authoring, composition and reasoning-facing contracts;
- `madre-sdk-testkit`: deterministic semantic tests without Kernel/application internals;
- `madre-sdk-experimental`: explicit 0.x incubation namespace. It currently exposes no public authoring helper;
- `madre-reasoning-spi`: independently installed reasoning-provider SPI, not an ordinary Module dependency;
- `madre-text-inference`, `madre-text-generation`, `madre-embeddings`: provider-independent reasoning computation contracts.

Concrete adapters, `madre-kernel` and `madre-app` are runtime/product implementation artifacts and are not normal Module dependencies.

Technical reuse does not create semantic ownership. A Module may use files, databases, HTTP, search, MCP, ML libraries, applications, operating-system APIs or devices directly when those belong to its application responsibility. Such mechanisms do not become new Modules merely because they are reusable.

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

    // Add only the reasoning computation contracts actually used by the Module.
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

## Minimal code-first Module

A Module can have zero Agents. Agents are semantic actors, not mandatory wrappers around every piece of code. A Module can also use arbitrary private implementation classes and libraries that MADRE never sees.

A small executable Module can look like this:

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
            INSPECT,
            "Inspect one request",
            OperationVisibility.PUBLIC,
            Map.of(REQUEST.id(), Privacy.SECRET),
            Map.of(RESULT.id(), Sensitivity.S4),
            Map.of());

    private final OperationBinding<String, String> inspect =
            OperationBinding.publicOperation(
                    INSPECT_CONTRACT,
                    Operation.of(call -> CompletableFuture.completedFuture(
                            new Material<>(new MaterialId(ID, UUID.randomUUID().toString()),
                                    RESULT, "private:" + call.input().payload(),
                                    call.input().sensitivity()))),
                    internal -> new Material<>(
                            new MaterialId(ID, UUID.randomUUID().toString()),
                            RESULT, "public:summary", Sensitivity.S1));

    @Override public ModuleId id() { return ID; }
    @Override public String version() { return "1.0.0"; }
    @Override public String purpose() { return "Example semantic application"; }
    @Override public Collection<? extends MaterialType<?>> materialTypes() {
        return List.of(REQUEST, RESULT);
    }
    @Override public Collection<? extends OperationBinding<?, ?>> operations() {
        return List.of(inspect);
    }
}
```

`module.definition()` derives the language-neutral `ModuleDefinition`; `module.instance()` derives the validated runtime assembly. Authors do not repeat the same Material/Operation declarations in another builder.

`Material` validates Java payload type, ordinary Sensitivity and identity/type ownership at construction. `OperationCall` validates the input receiver boundary and selected effect profile. `Operation.invoke(...)` validates produced Material against the declared output contract.

## Agents, Skills and Workflows

Use an `Agent` when the Module genuinely owns an intelligent semantic actor. It is optional and defines no universal loop, memory model, planner, prompt format or execution context.

```java
final class ExampleAgent implements Agent {
    @Override public AgentId id() { return new AgentId(ExampleModule.ID, "assistant"); }
    @Override public String purpose() { return "Coordinate example behavior"; }
    @Override public Set<OperationId> operations() { return Set.of(ExampleModule.INSPECT); }
}
```

If the author cannot justify a stronger causal-integrity claim, `Agent.integrity()` defaults conservatively to `Integrity.I1`. A Module may explicitly return a stronger value when that claim is actually established.

`SkillDefinition` remains lightweight semantic ability/knowledge/instruction metadata. `WorkflowDefinition` is currently only an Agent-owned ordered Operation description; it is not a Kernel scheduler or universal agent-loop language.

## Owner configuration and provider lifecycle

An installable Module JAR exposes one `ModuleProvider` through Java `ServiceLoader`.

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

The stable provider surface is:

```java
ModuleId moduleId();
default ModuleConfigurationDescriptor configurationDescriptor();
default ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration);
Module create(ModuleContext context,
        ModuleProviderConfiguration configuration);
```

Provider configuration is identity-scoped and Module-owned. The host currently supports only the demonstrated owner-facing field kinds `TEXT`, `INTEGER` and `CHOICE`; provider Java code remains the final semantic validator. Configuration discovery/validation does not materialize the Module.

The service descriptor is:

```text
META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

with the provider class name as its content.

## Composition and Security Algebra

`ModuleContext.directory()` and `ModuleContext.invoker()` are caller-bound. Module code cannot claim another caller identity or choose receiver Privacy.

A Module-to-Module call targets an installed `PUBLIC` Operation. The fixed receiver is `Privacy.MODULE`. A returned foreign Material must use a type listed by the caller in `publicMaterialReferences` and its Sensitivity must be able to reach that receiver. Successful receipt preserves the callee Material identity, owner, type and Sensitivity; caller interpretation creates a new caller-owned Material.

Owner-local invocation is a host-only boundary and returns valid Module Material unchanged. External/PUBLIC invocation is a different host-only boundary and requires the Module's explicit `PublicResultTransformer` to create new PUBLIC-capable Material. Runtime validates; it does not invent semantic sanitization.

MADRE does not claim to sandbox arbitrary installed Java code. The Owner chooses local software to install. Security Algebra governs MADRE-mediated information/effect composition; it is not an operating-system permission model. Unknown or unproven semantic facts should be represented conservatively rather than by fabricated certainty.

## Reasoning

A Module requests reasoning through `ReasoningService`. It submits a typed `ReasoningComputation<R>` plus execution controls; it does not select a concrete adapter. Kernel owns compatible mechanism selection, resources, immediate/durable execution, retry/cancellation and opaque durable persistence. The Module owns semantic interpretation and continuation.

`ReasoningRequest` derives originating Module and carried Sensitivity from its bounded `OperationCall`. If a Module combines several semantic inputs into reasoning context, it must first construct the actual contextual Material with the combined Sensitivity and derive the reasoning request from a call over that Material. There is no raw Sensitivity override.

Provider/model/runtime-specific tuning belongs to the reasoning provider/adapter. A new inference family does not imply a new Module or high-level semantic SDK abstraction.

## Deterministic semantic tests

`madre-sdk-testkit` materializes a real `ModuleProvider` and exercises public SDK contracts without simulating Kernel policy:

```java
ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
        .respond(TextInferenceCommand.class,
                command -> new TextInferenceResult(
                        "deterministic:" + command.prompt(),
                        TextInferenceResult.CompletionReason.STOP, -1, -1));

try (ModuleTestContext fixture = ModuleTestContext.create(reasoning)) {
    ModuleTestHarness module = ModuleTestHarness.materialize(
            new ExampleProvider(), fixture.context());
    // Invoke exact Operations and assert semantic behavior.
}
```

`ProgrammableReasoningService` is generic over arbitrary `ReasoningComputation<R>` and exposes a controlled durable lifecycle. The testkit deliberately does not reproduce Kernel mechanism selection, receiver-boundary enforcement, resource coordination, retry timing or SQLite behavior.

## Install and operate a local Module

Current owner lifecycle commands are deliberately local-file based:

```text
madre modules install /absolute/path/module.jar
madre modules install /absolute/path/replacement.jar --replace
madre modules list
madre modules inspect <module-id>
madre modules configure <module-id> [--set <field>=<value>]...
madre modules uninstall <module-id>
madre modules uninstall <module-id> --purge-configuration
```

MADRE-managed owner artifacts occupy deterministic managed slots. Shipped artifacts are protected. Manually copied JARs remain discoverable for compatibility but are not adopted, replaced or deleted by the managed lifecycle. Module semantic state is distinct from artifact bytes and is not deleted by uninstall.

Reasoning-provider installation/configuration remains a separate product domain under `madre reasoning ...`.

## Current experimentation posture

The stable SDK should stay small. Add a stable abstraction only after repeated real Module code demonstrates that it is generic, ownership-correct and materially reduces friction. `madre-sdk-experimental` exists so higher-level ideas can incubate without contaminating stable SDK or Kernel; it currently contains no public authoring helper.

Do not introduce a universal Agent loop, planner/tool framework, memory model, semantic database, workflow scheduler, arbitrary metadata tree or new Module merely because an experiment uses one of those techniques. Build the semantic application first; extract only what repeated implementations prove to be reusable.
