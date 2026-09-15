# MADRE SDK developer guide

This guide describes the current Java 21 Module-development surface. It is written for a developer whose project is not part of MADRE's Gradle multi-project build.

The current public artifacts are verification publications at version `0.1.0-SNAPSHOT`; they are not yet a released Maven Central or other public repository product. The repository's `publish` tasks write them to `build/isolated-repository`, and cross-platform CI copies the independent fixture to a runner-temporary directory and resolves the artifacts from that repository. That proves dependency isolation and the developer journey without claiming external release infrastructure that does not exist yet.

## Artifact roles

Use the smallest artifact set your software owns:

- `madre-bom` aligns compatible versions of public MADRE artifacts. It does not pull Kernel, the installed application, or adapters into a Module.
- `madre-algebra` contains the nominal Security Algebra values.
- `madre-sdk` is the stable Module SDK: Material, Module, Agent, Skill, Workflow, Operation, provider/registration, caller-bound composition, codecs and Module-facing reasoning contracts.
- `madre-sdk-testkit` is a public deterministic semantic test harness over stable SDK contracts. It does not implement Kernel scheduling, mechanism selection, receiver-boundary Security Algebra, resource coordination or SQLite durability.
- `madre-sdk-experimental` is a 0.x incubation artifact. Its APIs may change or disappear. Today it contains only `ModuleDefinitionBuilder`, a typed construction helper that produces the existing stable `ModuleDefinition`.
- `madre-reasoning-spi` is for independently installed reasoning-provider/adaptor authors, not ordinary Module implementation.
- computation-contract artifacts such as `madre-text-inference` define portable request/result semantics shared by multiple reasoning mechanisms.
- concrete adapters such as the llama.cpp and OpenAI-compatible artifacts are independently installed reasoning mechanisms. A Module should not compile against one merely to request text inference.
- `madre-kernel` and `madre-app` are runtime/product implementation artifacts. Ordinary Module projects do not depend on them.

The dependency direction is intentional: experimental SDK depends on stable SDK; stable SDK, testkit, Kernel, application and reasoning SPI do not depend on experimental SDK.

## Start an independent Gradle project

First publish the current verification artifacts from a MADRE checkout:

```text
./gradlew --no-daemon publish
```

On Windows use `gradlew.bat`. Then create a separate Java project with its own `settings.gradle.kts` and point it at the absolute verification repository. A minimal `build.gradle.kts` is:

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

    // Add the portable computation contracts that the Module actually requests.
    implementation("io.github.didacll:madre-text-inference")

    testImplementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    testImplementation("io.github.didacll:madre-sdk-testkit")
    // Optional incubation API; keeping it test-only is a useful default.
    testImplementation("io.github.didacll:madre-sdk-experimental")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

tasks.test { useJUnitPlatform() }
```

Run it with an absolute repository path, for example:

```text
./gradlew -PmadreRepository=/absolute/path/to/MADRE/build/isolated-repository test jar
```

The repository fixture `verification/sdk-consumer` is the executable version of this example. CI physically copies it outside the MADRE checkout before building it.

## Module anatomy

A Module is an independently installed semantic/application boundary, not a plug-in callback around Kernel internals.

`MaterialType<T>` and `Material<T>` are nominal typed information. Material types have an owning `ModuleId`; the owner interprets their payload semantics. Sensitivity belongs to the Material. A Module that receives foreign Material through composition receives it only through the caller-bound runtime contracts and the declared receiver constraints.

`OperationDefinition<I,O>` declares bounded Module behavior. Visibility controls who may reach the Operation; Security Algebra declarations remain explicit. The executable `Operation<I,O>` is bound to that exact definition in an `OperationBinding`.

Agents, Skills and Workflows are Module-owned semantic definitions. An Agent references the Skills, Workflows and Operations it is allowed to use. A Workflow is a Module/Agent-owned blueprint; it is not a Kernel scheduler or a universal planner language.

A `ModuleDefinition` is the immutable canonical declaration. A `ModuleInstance` combines that definition with exact executable bindings. The stable SDK deliberately prefers nominal IDs, immutable values and explicit Java generics over maps, reflection or annotation-driven hidden semantics.

The optional experimental builder only reduces collection boilerplate:

```java
ModuleDefinition definition = ModuleDefinitionBuilder
        .module(MODULE_ID, "0.1.0", "Example Module")
        .materialType(REQUEST)
        .materialType(RESULT)
        .operation(INSPECT_OPERATION)
        .build();
```

The result is the same stable `ModuleDefinition`; there is no experimental runtime model.

## ModuleProvider lifecycle and configuration ownership

Every installable JAR provides one or more `ModuleProvider` implementations. A provider declares canonical identity before materialization:

```java
public final class ExampleProvider implements ModuleProvider {
    @Override
    public ModuleId moduleId() {
        return ExampleDefinition.ID;
    }

    @Override
    public ModuleInstance create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        if (!configuration.moduleId().equals(ExampleDefinition.ID)) {
            throw new IllegalArgumentException("wrong Module configuration identity");
        }
        // Parse only this Module's supported keys here.
        return ExampleDefinition.instance(context, configuration);
    }
}
```

The host scopes immutable string configuration to the canonical Module identity. The Module owns key names, validation, parsing, typed settings and defaults. There is no generic Module-configuration metadata framework in the current public SDK.

`ModuleContext` supplies only Module-facing ports: `ReasoningService`, caller-bound `ModuleDirectory`/`ModuleInvoker`, and the Module's state directory. It does not expose owner-local/PUBLIC host invocation ports or Kernel implementation classes.

## ServiceLoader packaging

A normal installable Module is an ordinary JAR. Add the Java ServiceLoader descriptor:

```text
src/main/resources/META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

Its content is the provider's fully qualified class name, one per line:

```text
example.ExampleProvider
```

No MADRE-specific archive format is required. The independent verification project checks this descriptor in the built JAR and the packaged-product acceptance then proves actual ServiceLoader discovery, so the descriptor check is not the sole discovery claim.

A Gradle Module plugin is intentionally not part of this slice. Once the BOM removes manual version alignment, the remaining demonstrated build-specific convention is Java 21 plus standard ServiceLoader metadata. Adding plugin-marker publication, another versioned build API and hidden validation rules would be disproportionate to the current boilerplate. A small build plugin remains a reasonable future tooling increment if repeated external projects demonstrate more friction.

## Deterministic semantic tests

`madre-sdk-testkit` exists so semantic/agentic Module behavior does not require an installed product for every unit test.

A programmable reasoning service is generic over arbitrary nominal computations:

```java
ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
        .respond(TextInferenceCommand.class,
                command -> new TextInferenceResult(
                        "deterministic:" + command.prompt(),
                        TextInferenceResult.CompletionReason.STOP,
                        -1, -1));
```

Create a temporary Module context and materialize the real provider:

```java
try (ModuleTestContext fixture = ModuleTestContext.create(reasoning)) {
    ModuleTestHarness module = ModuleTestHarness.materialize(
            new ExampleProvider(), fixture.context(),
            new ModuleProviderConfiguration(ExampleDefinition.ID, Map.of()));

    Material<String> result = module.invoke(
            ExampleDefinition.OPERATION_ID, inputMaterial)
            .toCompletableFuture().join();

    // Assert the Module's semantic result.
}
```

`ModuleTestContext` creates and deletes a temporary state directory. Its default composition doubles expose no foreign Modules; an overload accepts explicit caller-bound `ModuleDirectory` and `ModuleInvoker` doubles when a semantic test genuinely needs composition.

`ModuleTestHarness` verifies provider/configuration/definition canonical identity and executable bindings. Its direct Operation invocation is deliberately a semantic test. It does not claim to reproduce the installed host's receiver-boundary Security Algebra.

For durable semantic continuation, `ProgrammableReasoningService.submit` starts work in `QUEUED`. Tests explicitly call `start`, `complete` or `fail`, then use the normal `inspect`, `collect`, `cancel` and `acknowledge` public methods. This gives deterministic state transitions without copying Kernel scheduling, retries, resource coordination or SQLite persistence into a fake runtime.

The testkit is not text-specific. The independent verification consumer also defines an integer-valued `ReasoningComputation<Integer>`, submits it as durable work and deterministically collects an integer result. This is an acceptance property, not merely a package-name assertion.

Integration tests against the real Kernel/host remain necessary for scheduling, persistence/recovery, mechanism selection, receiver enforcement and installed discovery.

## ReasoningService: immediate and durable work

A Module requests reasoning through `ReasoningService`; it does not select a concrete adapter.

A computation contract owns portable request semantics. For example, `TextInferenceCommand` belongs to `madre-text-inference` because multiple text mechanisms can implement those semantics. A Module can execute an immediate request and semantically interpret the returned typed result:

```java
ReasoningRequest<TextInferenceResult, TextInferenceCommand> request =
        ReasoningRequest.immediate(
                operationCall,
                new TextInferenceCommand(prompt, 128, List.of()),
                0,
                Duration.ofSeconds(10),
                ReasoningRetryPolicy.none(),
                Optional.empty(),
                ReasoningPreferences.unconstrained());

return context.reasoning().execute(request);
```

For work that must outlive the foreground call, use `ReasoningRequest.durable(...)`, then retain the returned `WorkId` in Module-owned semantic state. `inspect` observes physical work state; `collect` retrieves a successful typed result; the Module interprets that result and owns any semantic continuation; `acknowledge` tells the reasoning runtime the physical result no longer needs to be retained.

The separation for future inference experiments is strict:

- portable request/result semantics shared by implementations belong in a common computation-contract artifact;
- mechanism/model/runtime tuning belongs to the installed provider/adapter;
- shared mechanism selection, resource coordination, immediate/durable execution, retry, cancellation, physical persistence and opaque delivery belong to Kernel.

Provider-specific controls such as a future llama.cpp thread count, GPU-layer placement or mmap choice therefore do not become Kernel fields merely because they matter for local-model performance.

## Module-to-Module composition

`ModuleContext.directory()` and `ModuleContext.invoker()` are caller-bound by the installed runtime. Module code never supplies its own caller identity or receiver Privacy.

A Module discovers compatible installed PUBLIC behavior with `ReachabilityQuery`, for example:

```java
List<ReachableModule> reachable = context.directory().query(
        new ReachabilityQuery(FOREIGN_INPUT_TYPE, input.sensitivity()));
```

Use the returned canonical target/Operation information to form the exact bounded call expected by `ModuleInvoker`. PRIVATE Operations are not reachable. A foreign result must use a Material type the caller canonically references and must satisfy the fixed installed-Module receiver boundary. The callee's raw Material crosses unchanged when it is valid; the external `PublicResultTransformer` is not run for Module-to-Module calls.

The repository's independent `verification/module-interoperability` projects and installed-host acceptance prove this real boundary. Testkit composition doubles are for Module semantic tests only and do not replace that proof.

## Security Algebra concerns for Module authors

Keep the five public dimensions distinct:

- Sensitivity classifies information carried by Material and composes by maximum.
- Privacy is a receiver boundary and composes by minimum.
- Integrity describes causal support and composes by minimum.
- Risk describes consequence of one Module effect.
- Autonomy describes how much owner involvement that effect requires.

Information may reach a receiver only when accumulated Sensitivity is no greater than accumulated Privacy. `Privacy.MODULE` is the structural installed-Module receiver boundary; Module code does not choose it per call. External/PUBLIC disclosure is a different host boundary and requires Module-owned semantic transformation when sensitive internal output must be minimized.

Do not hide these declarations behind a builder, annotation, metadata map or generic tool abstraction. The testkit intentionally does not emulate the runtime enforcement path.

## Install and invoke the JAR

Build the Module:

```text
./gradlew -PmadreRepository=/absolute/path/to/MADRE/build/isolated-repository clean jar
```

Copy the resulting JAR into the owner-writable Module directory.

Windows:

```text
%LOCALAPPDATA%\MADRE\modules
```

Linux:

```text
${XDG_DATA_HOME:-~/.local/share}/madre/modules
```

On the next normal startup, packaged MADRE scans the shipped Module directory plus this owner directory. For deterministic developer/test isolation an explicit `modules.directory` selects exactly one directory instead.

The current generic non-interactive host receiver paths are useful for installation verification:

```text
madre --config <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre --config <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

They are existing legitimate receiver paths, not a proposed universal interaction protocol. The external/PUBLIC form exercises semantic public transformation; owner-local invocation preserves the Module-created Material when the owner receiver is permitted to receive it.

## What the SDK deliberately does not provide yet

This first SDK tooling slice does not claim a community-ready 0.x release. In particular, it does not provide:

- a public remote artifact repository/release/signing/versioning process;
- a Gradle Module plugin or project generator;
- Module marketplace/download/update/removal management;
- a generic Module configuration metadata/settings framework;
- provider accounts, OAuth or credential storage;
- a universal Agent loop, conversation/message protocol, tool abstraction or workflow language;
- generic prompt/context engineering, planning, memory, RAG, semantic database or knowledge-graph frameworks;
- production embedding or multimodal computation contracts;
- audio/voice or MCP integration;
- a desktop/web GUI;
- an external-process Module transport;
- a CORE-specific privileged API or a standardized replacement for current `interaction.*` behavior.

Those omissions are intentional. Experimental semantic facilities should enter `madre-sdk-experimental` only when a real Module/CORE experiment demonstrates a small useful abstraction. Graduation into the stable SDK or another stable public artifact requires an explicit decision and evidence from repeated use.

## Executable reference

`verification/sdk-consumer` is the canonical small external-project example for this baseline. CI publishes MADRE artifacts, copies that project to a runner-temporary directory outside the checkout, runs its tests and JAR build, copies only the produced Module JAR back into the packaged-product acceptance fixture, installs it through the ordinary owner-writable Module path, discovers it through ServiceLoader and invokes its bounded behavior through the real host receiver path.

That journey intentionally combines fast semantic tests with separate installed-host integration evidence rather than pretending either layer proves the other.
