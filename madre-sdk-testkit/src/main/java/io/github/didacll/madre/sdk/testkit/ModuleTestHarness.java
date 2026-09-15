package io.github.didacll.madre.sdk.testkit;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Materializes and invokes one Module directly through its public SDK contracts.
 *
 * <p>The harness validates provider/definition identity and executable bindings. Direct invocation
 * exercises Module semantics and declared Operation contracts only; it is not an emulation of the
 * installed host's receiver-boundary Security Algebra.</p>
 */
public final class ModuleTestHarness {
    private final ModuleInstance instance;

    private ModuleTestHarness(ModuleInstance instance) {
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    /** Materializes one provider with empty owner configuration. */
    public static ModuleTestHarness materialize(ModuleProvider provider, ModuleContext context) {
        ModuleProvider exactProvider = Objects.requireNonNull(provider, "provider");
        ModuleId id = Objects.requireNonNull(exactProvider.moduleId(), "provider.moduleId()");
        return materialize(exactProvider, context,
                new ModuleProviderConfiguration(id, Map.of()));
    }

    /** Materializes one provider and asserts its canonical identity/bindings. */
    public static ModuleTestHarness materialize(ModuleProvider provider, ModuleContext context,
            ModuleProviderConfiguration configuration) {
        ModuleProvider exactProvider = Objects.requireNonNull(provider, "provider");
        ModuleContext exactContext = Objects.requireNonNull(context, "context");
        ModuleProviderConfiguration exactConfiguration = Objects.requireNonNull(
                configuration, "configuration");
        ModuleId providerId = Objects.requireNonNull(exactProvider.moduleId(), "provider.moduleId()");
        if (!providerId.equals(exactConfiguration.moduleId())) {
            throw new IllegalArgumentException("Module configuration identity does not match provider");
        }
        ModuleInstance created = Objects.requireNonNull(
                exactProvider.create(exactContext, exactConfiguration),
                "ModuleProvider.create returned null");
        if (!providerId.equals(created.definition().id())) {
            throw new IllegalArgumentException(
                    "materialized Module identity does not match ModuleProvider identity");
        }
        created.validateBindings();
        return new ModuleTestHarness(created);
    }

    public ModuleInstance instance() { return instance; }

    /** Invokes one exact typed effect-free declared Operation. */
    public <I, O> CompletionStage<Material<O>> invoke(OperationDefinition<I, O> operation,
            Material<I> input) {
        OperationDefinition<I, O> exactOperation = Objects.requireNonNull(operation, "operation");
        if (!exactOperation.effectProfiles().isEmpty()) {
            throw new IllegalArgumentException(
                    "Operation requires an explicit EffectProfile; construct an OperationCall instead");
        }
        return invoke(OperationCall.withoutEffect(exactOperation,
                Objects.requireNonNull(input, "input")));
    }

    /** Invokes one exact effect-free declared Operation by canonical identity. */
    public <I, O> CompletionStage<Material<O>> invoke(OperationId operationId, Material<I> input) {
        OperationId exactId = Objects.requireNonNull(operationId, "operationId");
        OperationDefinition<?, ?> rawDefinition = instance.definition().operations().get(exactId);
        if (rawDefinition == null) {
            throw new IllegalArgumentException("unknown Module Operation: " + exactId);
        }
        @SuppressWarnings("unchecked")
        OperationDefinition<I, O> definition = (OperationDefinition<I, O>) rawDefinition;
        return invoke(definition, input);
    }

    /** Invokes one exact pre-constructed bounded Operation call. */
    public <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
        OperationCall<I, O> exactCall = Objects.requireNonNull(call, "call");
        OperationBinding<?, ?> rawBinding = instance.operations().get(exactCall.operation().id());
        if (rawBinding == null || !rawBinding.definition().equals(exactCall.operation())) {
            throw new IllegalArgumentException(
                    "OperationCall contract differs from this materialized Module binding");
        }
        @SuppressWarnings("unchecked")
        OperationBinding<I, O> binding = (OperationBinding<I, O>) rawBinding;
        return binding.invoke(exactCall);
    }
}
