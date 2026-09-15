# MADRE SDK developer guide

This guide describes the current Java 21 Module-development surface for projects outside MADRE's Gradle multi-project build. The current public artifacts are verification publications at version `0.1.0-SNAPSHOT`; repository `publish` tasks write them to `build/isolated-repository`. This proves dependency isolation and the external developer journey without claiming a public remote release channel that does not yet exist.

## Artifact roles

Use the smallest public artifact set the Module genuinely needs.

- `madre-bom` aligns compatible public MADRE artifact versions.
- `madre-algebra` contains the nominal Security Algebra values.
- `madre-sdk` is the stable Module SDK: Material, Module, Agent, Skill, Workflow, Operation, provider/registration, caller-bound composition, codecs, Module-facing reasoning contracts, and the stable Module installation-configuration contract.
- `madre-sdk-testkit` provides deterministic Module semantic tests over public contracts. It has no `madre-app` or Kernel implementation dependency.
- `madre-sdk-experimental` is the explicit 0.x incubation artifact. Today it contains only `ModuleDefinitionBuilder`; stable/runtime surfaces do not depend on it.
- `madre-reasoning-spi` is for independently installed reasoning-provider authors, not ordinary Module implementation.
- `madre-text-inference`, `madre-text-generation`, and `madre-embeddings` are stable provider-independent computation contracts.
- concrete reasoning adapters, `madre-kernel`, and `madre-app` are runtime/product implementation artifacts and are not ordinary Module dependencies.

Technical reuse does not create semantic ownership. Search clients, storage libraries, transports, databases, model APIs, and other reusable mechanisms can be used by Modules without becoming Module-domain types.

## Start an independent Gradle project

Publish the verification artifacts from a MADRE checkout:

```text
./gradlew --no-daemon publish
```

On Windows use `gradlew.bat`. A minimal independent project can then use:

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

    // Add only computation contracts the Module actually uses.
    implementation("io.github.didacll:madre-text-inference")

    testImplementation(platform("io.github.didacll:madre-bom:0.1.0-SNAPSHOT"))
    testImplementation("io.github.didacll:madre-sdk-testkit")
    testImplementation("io.github.didacll:madre-sdk-experimental")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.test { useJUnitPlatform() }
```

Run it with an absolute verification repository path:

```text
./gradlew -PmadreRepository=/absolute/path/to/MADRE/build/isolated-repository clean test jar
```

`verification/sdk-consumer` is the executable external-project reference. CI copies it to a runner-temporary directory outside the MADRE checkout before building it.

## Module anatomy

A Module is an independently installed semantic/application boundary, not a generic plug-in callback around Kernel internals.

`MaterialType<T>` and `Material<T>` are nominal typed information owned and interpreted by a Module. `OperationDefinition<I,O>` declares bounded behavior and its Security Algebra constraints. `OperationBinding` binds the exact executable `Operation<I,O>`. `ModuleDefinition` is the immutable canonical semantic declaration; `ModuleInstance` combines it with exact executable bindings.

Agents, Skills, and Workflows remain Module-owned semantics. A Workflow is a semantic blueprint, not a Kernel scheduler or universal planner language.

The stable SDK includes two demonstrated syntax conveniences only: `MaterialCodecs.utf8String()` for the standard UTF-8 String codec and `Operation.of(...)` for functional Operation bodies. They do not hide Material identity, Sensitivity, EffectProfiles, binding, or reasoning semantics.

The optional experimental builder only reduces immutable collection assembly:

```java
ModuleDefinition definition = ModuleDefinitionBuilder
        .module(MODULE_ID, "0.1.0", "Example Module")
        .materialType(REQUEST)
        .materialType(RESULT)
        .operation(INSPECT_OPERATION)
        .build();
```

It still produces the stable `ModuleDefinition`; there is no second runtime/domain model.

## ModuleProvider lifecycle and owner configuration

Every installable Module JAR exposes one or more `ModuleProvider` implementations through Java ServiceLoader. A provider declares canonical identity before normal Module materialization. The stable owner-configuration methods are:

```java
ModuleId moduleId();
ModuleConfigurationDescriptor configurationDescriptor();
ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration);
ModuleInstance create(ModuleContext context,
        ModuleProviderConfiguration configuration);
```

`configurationDescriptor()` is installation metadata, not Module semantics. It is available from the discovered provider without calling `create(...)`. Providers that do not declare owner-configurable settings remain source-compatible through the default empty descriptor.

The descriptor is deliberately small and Module-specific:

```java
private static final ModuleConfigurationDescriptor CONFIGURATION =
        new ModuleConfigurationDescriptor(
                ID,
                "Example Module",
                "Owner-facing installation settings for the Example Module.",
                List.of(
                        ModuleConfigurationField.text(
                                "result-prefix", "Result prefix",
                                "Optional prefix applied to semantic results.",
                                false, null),
                        ModuleConfigurationField.integer(
                                "maximum-attempts", "Maximum attempts",
                                "Positive attempt limit.",
                                false, "3", OptionalLong.of(1), OptionalLong.empty()),
                        ModuleConfigurationField.choice(
                                "location", "Location",
                                "Preferred execution location.",
                                false, null, List.of("LOCAL", "REMOTE"))));

@Override
public ModuleConfigurationDescriptor configurationDescriptor() {
    return CONFIGURATION;
}
```

Only three field kinds are stable today because they are the only shapes demonstrated by real Modules: `TEXT`, `INTEGER`, and `CHOICE`. Integer fields can declare minimum/maximum bounds. Choice fields declare finite allowed values. Fields can declare required/default information. There are no secret, floating-point, list, nested-object, arbitrary-JSON, visibility-expression, or dynamic-schema field kinds.

The provider's Java code remains the ultimate semantic validator. `validateConfiguration(...)` receives one complete `ModuleProviderConfiguration` scoped to exactly `moduleId()`. It may reject the proposal or return a canonicalized configuration for the same identity. It must not materialize the Module or execute Operations.

A real provider can therefore centralize parsing between edit-time validation and normal startup:

```java
@Override
public ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration) {
    return canonical(configuration);
}

@Override
public ModuleInstance create(ModuleContext context,
        ModuleProviderConfiguration configuration) {
    ModuleProviderConfiguration canonical = canonical(configuration);
    return ExampleDefinition.instance(context, canonical);
}
```

The host applies generic field-shape checks to newly supplied owner edits, then invokes provider-owned validation on the complete candidate. Existing raw values remain provider-interpreted for compatibility; metadata does not replace the Module's parser.

`ModuleProviderConfiguration` remains immutable and identity-scoped. No raw `Properties` object is exposed in the public SDK. `ModuleContext` still supplies only Module-facing runtime ports: `ReasoningService`, caller-bound `ModuleDirectory`/`ModuleInvoker`, and the Module state directory.

This API is intentionally separate from `madre-reasoning-spi` configuration types. Reasoning providers own repeatable named mechanism instances and enable/disable state; an installed Module has one canonical Module identity. No shared `SettingsSchema`, `ConfigurableProvider`, generic property tree, or cross-domain settings service exists.

## ServiceLoader packaging

A normal installable Module is an ordinary JAR. Add:

```text
src/main/resources/META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

with one provider class name per line, for example:

```text
example.ExampleProvider
```

No MADRE-specific archive format is required. The external SDK acceptance checks the built descriptor and then proves actual ServiceLoader discovery through the packaged product.

## Deterministic semantic tests

`madre-sdk-testkit` supports fast semantic tests without pretending to be Kernel.

```java
ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
        .respond(TextInferenceCommand.class,
                command -> new TextInferenceResult(
                        "deterministic:" + command.prompt(),
                        TextInferenceResult.CompletionReason.STOP,
                        -1, -1));

try (ModuleTestContext fixture = ModuleTestContext.create(reasoning)) {
    ModuleTestHarness module = ModuleTestHarness.materialize(
            new ExampleProvider(), fixture.context(),
            new ModuleProviderConfiguration(ExampleDefinition.ID, Map.of()));
    // invoke semantic Operations and assert Module behavior
}
```

The testkit does not copy Kernel scheduling, mechanism selection, retry timing, SQLite durability, resource coordination, or receiver-boundary Security Algebra. Installed-host integration tests remain authoritative for those claims.

`ProgrammableReasoningService` is generic over arbitrary `ReasoningComputation<R>`. Durable test work is explicitly driven through queued/running/succeeded/failed/cancelled states by the test.

## Module-facing reasoning

Modules request reasoning through `ReasoningService`; they do not select concrete adapters. Portable request/result semantics live in the computation-contract artifacts. Runtime/model/provider tuning remains adapter-owned. Kernel owns compatible mechanism selection, resources, immediate/durable execution, retry/cancellation, persistence, and opaque result delivery. Domain meaning remains Module-owned.

For durable work, retain the returned `WorkId` in Module-owned semantic state, inspect/collect the physical result later, interpret it inside the Module, then acknowledge when physical retention is no longer needed.

## Module-to-Module composition and Security Algebra

`ModuleContext.directory()` and `ModuleContext.invoker()` are caller-bound. Module code supplies neither caller identity nor receiver Privacy. PRIVATE Operations are not reachable through the public Module directory. A foreign result must use a Material type canonically referenced by the caller and satisfy fixed `Privacy.MODULE`.

Keep the public Security Algebra dimensions distinct: Sensitivity classifies information, Privacy is a receiver boundary, Integrity describes causal support, Risk describes consequence, and Autonomy describes owner involvement. External/PUBLIC disclosure is a separate host receiver and requires Module-owned semantic public transformation when sensitive internal output must be minimized.

## Install, inspect, configure, and invoke a Module

Build the JAR and copy it to the owner-writable Module directory.

Windows:

```text
%LOCALAPPDATA%\MADRE\modules
```

Linux:

```text
${XDG_DATA_HOME:-~/.local/share}/madre/modules
```

Packaged MADRE scans its shipped Module directory plus that owner directory. An explicit `modules.directory` selects exactly one directory for deterministic development/test isolation.

Discover providers without requiring successful Module materialization:

```text
madre modules list
```

Inspect provider-owned metadata and current values:

```text
madre modules inspect <module-id>
```

Configure non-interactively:

```text
madre modules configure <module-id> \
  --set <field>=<value> \
  --set <field>=<value>
```

Calling `madre modules configure <module-id>` without `--set` uses the same simple descriptor-driven prompt style as reasoning configuration.

The host persists the existing raw compatibility representation:

```properties
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The Module owns the keys and their meaning. The host constructs a complete identity-scoped candidate, validates the proposed edit through the provider before persistence, replaces only that exact Module prefix, preserves all unrelated host/reasoning/other-Module settings, and updates its in-memory configuration only after the safe same-directory replacement succeeds. A rejected edit leaves the previous configuration file unchanged.

Configuration commands use provider discovery only. They do not call `ModuleProvider.create(...)` and do not start `MadreApplication`, so they can serve as a recovery path for a Module whose runtime configuration cannot currently materialize.

Configuration does not install, enable, disable, or uninstall a Module artifact. There is intentionally no Module `remove` command in this slice because clearing settings and artifact removal are different lifecycle operations.

A subsequent normal startup receives the persisted values through the same `ModuleProviderConfiguration` passed to `create(...)` as before.

Generic host invocation remains useful for installation verification:

```text
madre --config <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre --config <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

Owner-local invocation preserves valid Module-created Material. The external/PUBLIC path exercises the Module-owned semantic public transformer.

## Executable external reference

`verification/sdk-consumer` declares exactly one real owner setting, `result-prefix`. The same provider contract is built from a directory outside the MADRE checkout against only the published public artifacts. Cross-platform SDK acceptance then:

1. builds its installable JAR externally;
2. copies the JAR to the ordinary owner Module directory;
3. discovers `phd.module` through `madre modules list`;
4. exposes `result-prefix` through `madre modules inspect phd.module`;
5. configures it through `madre modules configure phd.module --set result-prefix=configured-`;
6. verifies only the exact raw Module property is persisted;
7. rejects an invalid prefix while leaving the file byte-for-byte unchanged;
8. performs a normal startup, which materializes the Module with the persisted setting;
9. proves owner-local `private:configured-hello` and external/PUBLIC `public:configured-hello` behavior.

The shipped owner-interaction Module uses the same stable configuration contract for its existing ten installation settings. Its established `OwnerInteractionSettings.fromInstallation(...)` parser remains the semantic validator and runtime parser, preserving defaults, integer/duration interpretation, location semantics, retry behavior, reasoning preferences, and Module behavior.

## What the SDK deliberately does not provide yet

The current foundation does not claim a community-ready public SDK 0.x release. It still does not provide:

- a public remote artifact repository/release/signing/versioning process;
- a Gradle Module plugin or project generator;
- Module JAR marketplace/download/install/update/removal management;
- reasoning JAR download/install/update/removal management;
- provider accounts, OAuth, or credential storage;
- a graphical settings UI;
- a universal Agent loop, conversation protocol, tool abstraction, or workflow language;
- generic planning, memory, RAG, semantic-database, or knowledge-graph frameworks;
- multimodal/audio/voice/MCP abstractions;
- an external-process Module transport;
- a CORE-specific privileged API or standardized replacement for current `interaction.*` behavior.

Those omissions are intentional. Stable public APIs are recovered from demonstrated interoperability needs. Experimental semantic facilities belong in `madre-sdk-experimental` only when repeated real code establishes a small reusable abstraction.
