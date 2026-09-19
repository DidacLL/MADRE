package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Executable Java binding for one exact portable Operation contract. */
public final class OperationBinding<I, O> {
    private final OperationDefinition definition;
    private final Operation<I, O> implementation;

    private OperationBinding(OperationDefinition definition,
            Operation<I, O> implementation) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.implementation = Objects.requireNonNull(implementation, "implementation");
    }

    /** Binds an ordinary bounded Operation with no special presentation receiver boundary. */
    public static <I, O> OperationBinding<I, O> operation(
            OperationDefinition definition, Operation<I, O> implementation) {
        return new OperationBinding<>(definition, implementation);
    }

    public OperationDefinition definition() { return definition; }

    /** Executes inside the owning Module with an explicit semantic actor. */
    public CompletionStage<Material<O>> invoke(AgentContext context, OperationCall<I, O> call) {
        AgentContext execution = Objects.requireNonNull(context, "context");
        Agent actingAgent = execution.actor();
        OperationCall<I, O> exactCall = Objects.requireNonNull(call, "call");
        if (!definition.equals(exactCall.operation())) {
            throw new IllegalArgumentException(
                    "OperationCall contract differs from the installed Operation binding");
        }
        if (actingAgent.id().moduleId().equals(definition.id().moduleId())
                && !actingAgent.operations().contains(definition.id())) {
            throw new IllegalArgumentException("acting Agent does not own this Operation");
        }
        return implementation.invoke(execution, exactCall);
    }
}
