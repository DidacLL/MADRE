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

The host's new local JAR lifecycle does not add a public `Artifact`, `Plugin` or filesystem API. Provider/source-path tracking and owner-directory mutation remain `madre-app` responsibilities.

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

`verification/sdk-consumer` is the executable external-project reference. The manually invoked `Extended SDK developer acceptance` copies it to a runner-temporary directory outside the MADRE checkout before building it.

## Module anatomy

A Module is an independently installed semantic/application boundary, not a generic plug-in callback around Kernel internals.

`MaterialType<T>` and `Material<T>` are nominal typed information owned and interpreted by a Module. `OperationDefinition<I,O>` declares bounded behavior and its Security Algebra constraints. `OperationBinding` binds the exact executable `Operation<I,O>`. `ModuleDefinition` is the immutable canonical semantic declaration; `ModuleInstance` combines it with exact executable bindings.

Agents and Skills are Module-owned semantics. Workflows are Agent-owned semantic blueprints within that Module, not Kernel schedulers or a universal planner language.

The stable SDK includes demonstrated syntax conveniences such as `MaterialCodecs.utf8String()` and `Operation.of(...)`. They do not hide Material identity, Sensitivity, EffectProfiles, binding, or reasoning semantics.

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

A managed installable Module JAR exposes exactly one canonical `ModuleProvider` through Java ServiceLoader. Direct/developer discovery remains compatible with historical manually placed JARs, but the owner-managed 0.x install command deliberately requires one provider per JAR so artifact identity and replacement are unambiguous.

A provider declares canonical identity before normal Module materialization. The stable owner-configuration methods are:

```java
ModuleId moduleId();
ModuleConfigurationDescriptor configurationDescriptor();
ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration);
ModuleInstance create(ModuleContext context,
        ModuleProviderConfiguration configuration);
```

`configurationDescriptor()` is installation/configuration metadata, not Module semantics. It is available from the discovered provider without calling `create(...)`. Providers that do not declare owner-configurable settings remain source-compatible through the default empty descriptor.

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

`ModuleProviderConfiguration` remains immutable and identity-scoped. No raw `Properties` object or artifact path is exposed in the public SDK. `ModuleContext` still supplies only Module-facing runtime ports: `ReasoningService`, caller-bound `ModuleDirectory`/`ModuleInvoker`, and the Module state directory. A Module cannot install or uninstall another Module.

This API is intentionally separate from `madre-reasoning-spi` configuration types. Reasoning providers own repeatable named mechanism instances and enable/disable state; an installed Module has one canonical Module identity. No shared `SettingsSchema`, `ConfigurableProvider`, generic property tree, cross-domain settings service or universal artifact interface exists.

## ServiceLoader packaging

A normal managed Module is an ordinary single JAR. Add:

```text
src/main/resources/META-INF/services/io.github.didacll.madre.sdk.registration.ModuleProvider
```

containing the single provider class name, for example:

```text
example.ExampleProvider
```

No MADRE-specific archive format is required. The managed installer validates that the provider class is actually supplied by the candidate JAR. A JAR with no Module provider, multiple Module providers, a reasoning provider instead, or both installation domains is rejected by the owner lifecycle command.

The external SDK acceptance checks the built descriptor and proves actual ServiceLoader discovery through packaged MADRE.

The current managed contract assumes the independently built provider and its runtime implementation are self-contained in that ordinary JAR apart from public MADRE artifacts already supplied by the host. No ZIP bundle, companion-JAR resolver, lockfile or Maven dependency installation protocol exists.

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

The testkit does not copy Kernel scheduling, mechanism selection, retry timing, SQLite durability, resource coordination, receiver-boundary Security Algebra, or host artifact lifecycle. Installed-host integration tests remain authoritative for those claims.

`ProgrammableReasoningService` is generic over arbitrary `ReasoningComputation<R>`. Durable test work is explicitly driven through queued/running/succeeded/failed/cancelled states by the test.

## Module-facing reasoning

Modules request reasoning through `ReasoningService`; they do not select concrete adapters. Portable request/result semantics live in the computation-contract artifacts. Runtime/model/provider tuning remains adapter-owned. Kernel owns compatible mechanism selection, resources, immediate/durable execution, retry/cancellation, persistence, and opaque result delivery. Domain meaning remains Module-owned.

For durable work, retain the returned `WorkId` in Module-owned semantic state, inspect/collect the physical result later, interpret it inside the Module, then acknowledge when physical retention is no longer needed.

## Module-to-Module composition and Security Algebra

`ModuleContext.directory()` and `ModuleContext.invoker()` are caller-bound. Module code supplies neither caller identity nor receiver Privacy. PRIVATE Operations are not reachable through the public Module directory. A foreign result must use a Material type canonically referenced by the caller and satisfy fixed `Privacy.MODULE`.

Keep the public Security Algebra dimensions distinct: Sensitivity classifies information, Privacy is a receiver boundary, Integrity describes causal support, Risk describes consequence, and Autonomy describes owner involvement. External/PUBLIC disclosure is a separate host receiver and requires Module-owned semantic public transformation when sensitive internal output must be minimized.

Artifact install/uninstall is host administration, not Material flow. It is intentionally absent from `ModuleContext` and does not acquire fabricated Security Algebra values.

## Install, replace, inspect, configure, and uninstall a Module

Build the independent JAR, then let packaged MADRE install that local file:

```text
madre modules install /absolute/path/example-module.jar
```

The host validates the candidate without calling `ModuleProvider.create(...)`, stages it inside the conventional owner-writable Module root, revalidates the staged bytes, closes its temporary classloader, then commits the JAR. On packaged hosts those managed roots are:

```text
Windows: %LOCALAPPDATA%\MADRE\modules
Linux:   ${XDG_DATA_HOME:-~/.local/share}/madre/modules
```

Do not pre-copy the JAR there in the ordinary installed-product journey. Direct directory placement remains an advanced/developer compatibility path only. A manually placed JAR under a non-reserved filename remains discoverable as `manual`, but the managed lifecycle will not adopt, replace or delete it. The deterministic managed filename for an installation domain and canonical identity is a reserved host slot.

An explicit replacement requires another local JAR with the same canonical `ModuleId`:

```text
madre modules install /absolute/path/replacement.jar --replace
```

A same-identity lifecycle-managed owner artifact must opt into `--replace`; a shipped identity cannot be overridden. A same-identity manual JAR blocks managed installation until the operator removes that manual file explicitly. Replacement is staged/revalidated before the active managed JAR is touched and uses a same-filesystem atomic move where supported with safe replacement fallback.

Discover providers without requiring successful Module materialization:

```text
madre modules list
```

The list classifies each provider as `shipped`, lifecycle-managed `owner`, manually placed `manual`, or external-override `development`. Lifecycle-managed owner entries also show their managed artifact filename.

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

The Module owns the keys and their meaning. The host constructs a complete identity-scoped candidate, validates the proposed edit through the provider before persistence, replaces only the exact Module prefix, preserves all unrelated host/reasoning/other-Module settings, and updates its in-memory configuration only after the safe same-directory replacement succeeds. A rejected edit leaves the previous configuration file unchanged.

Configuration commands use provider discovery only. They do not call `ModuleProvider.create(...)` and do not start `MadreApplication`, so they can serve as a recovery path for a Module whose runtime configuration cannot currently materialize.

A subsequent normal startup receives the persisted values through the same `ModuleProviderConfiguration` passed to `create(...)` as before.

Generic host invocation remains useful for installation verification:

```text
madre --config <properties> --invoke-owner <module> <operation> <material-type> <S1..S5> <payload>
madre --config <properties> --invoke-public <module> <operation> <material-type> <S1..S5> <payload>
```

Owner-local invocation preserves valid Module-created Material. The external/PUBLIC path exercises the Module-owned semantic public transformer.

Uninstall keeps configuration and semantic state separate from artifact bytes:

```text
madre modules uninstall <module-id>
madre modules uninstall <module-id> --purge-configuration
```

Plain uninstall refuses when exact `modules.config[<id>].*` settings remain. The explicit purge form removes only that exact Module configuration while uninstalling. It does not remove `<state>/module-state`, does not rewrite `roles.core`, and does not remove unrelated configuration. A Module referenced by current `interaction.module` is refused until that binding is changed. Shipped Modules cannot be removed through this command.

If a Module is discovered only through an explicit `modules.directory` outside MADRE's conventional owner root, the lifecycle command will not delete that arbitrary developer file. The same non-destructive rule applies to a manual JAR copied into the conventional owner discovery directory: even `--purge-configuration` refuses before changing configuration or artifact bytes. Remove direct-placement files manually if that setup is intentional.

## Reasoning-provider lifecycle is separate

Reasoning providers use the analogous local-file commands but remain a distinct product concept:

```text
madre reasoning install /absolute/path/provider.jar
madre reasoning install /absolute/path/provider-replacement.jar --replace
madre reasoning uninstall <provider-id>
madre reasoning uninstall <provider-id> --purge-configuration
```

Installation validates the provider descriptor/configurator and current provider-owned configuration without materializing a mechanism. It never invents endpoint/model/privacy values or enables an instance.

`madre reasoning remove <provider>/<instance>` still removes one configured instance. Provider artifact uninstall is deliberately a separate `uninstall` command. Plain provider uninstall refuses while configured instances remain. The purge form removes them through the provider's own configurator before deleting a lifecycle-managed owner JAR; it does not guess provider raw-property namespaces and does not erase Kernel durable work. A manually placed reasoning-provider JAR is discoverable as `manual` but is never replaced or deleted by the managed lifecycle, and purge refusal occurs before provider configuration is changed.

## Executable external reference

`verification/sdk-consumer` declares exactly one real owner setting, `result-prefix`. The same provider contract is built from a directory outside the MADRE checkout against only the published public artifacts. The manually invoked cross-platform `Extended SDK developer acceptance` then:

1. builds its installable JAR externally;
2. verifies the conventional owner Module directory initially contains no external JAR;
3. runs packaged `madre modules install <external-jar>`;
4. discovers `phd.module` as lifecycle-managed `source=owner`;
5. exposes `result-prefix` through `madre modules inspect phd.module`;
6. configures it through `madre modules configure phd.module --set result-prefix=configured-`;
7. performs normal owner-local and external/PUBLIC semantic invocation with the configured value;
8. creates a byte-different, semantically identical fixture JAR and proves explicit same-identity `--replace` without manually editing the owner directory;
9. proves invalid replacement candidates leave the previously installed Module and reasoning artifacts byte-for-byte unchanged;
10. proves shipped owner-interaction install collision/uninstall attempts fail without changing shipped bytes;
11. keeps all prior owner/PUBLIC, CORE/interaction, zero-reasoning, and durable-restart evidence;
12. proves plain uninstall refuses with retained configuration;
13. proves `--purge-configuration` removes only the exact `phd.module` settings and owner JAR while unrelated configuration and semantic Module state remain;
14. proves `phd.module` is no longer discoverable;
15. proves manually placed Module/reasoning JARs remain discoverable as `manual`, while replace and purge-uninstall refuse before mutating either the files or their persisted configuration.

The shipped owner-interaction Module uses the same stable configuration contract for its existing installation settings. Its established semantic parser remains both edit-time and runtime authority.

## What the SDK deliberately does not provide yet

The current foundation does not claim a community-ready public SDK 0.x release. It still does not provide:

- a public remote artifact repository/release/signing/versioning process;
- a Gradle Module plugin or project generator;
- remote URL/GitHub/Maven Module installation or marketplace/update management;
- dependency/bundle formats beyond the demonstrated single-JAR managed contract;
- provider accounts, OAuth, or credential storage;
- a graphical settings UI;
- a universal Agent loop, conversation protocol, tool abstraction, or workflow language;
- generic planning, memory, RAG, semantic-database, or knowledge-graph frameworks;
- multimodal/audio/voice/MCP abstractions;
- an external-process Module transport;
- a CORE-specific privileged API or standardized replacement for current `interaction.*` behavior.

Those omissions are intentional. Stable public APIs are recovered from demonstrated interoperability needs. Experimental semantic facilities belong in `madre-sdk-experimental` only when repeated real code establishes a small reusable abstraction.
