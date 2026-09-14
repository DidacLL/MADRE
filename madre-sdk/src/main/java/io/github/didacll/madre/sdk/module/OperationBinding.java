package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Executable binding for one exact declared Operation. */
public final class OperationBinding<I, O> {
    private final OperationDefinition<I, O> definition;
    private final Operation<I, O> implementation;
    private final Optional<PublicResultTransformer<O>> publicResultTransformer;

    private OperationBinding(OperationDefinition<I, O> definition,
            Operation<I, O> implementation,
            Optional<PublicResultTransformer<O>> publicResultTransformer) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.implementation = Objects.requireNonNull(implementation, "implementation");
        this.publicResultTransformer = Objects.requireNonNull(
                publicResultTransformer, "publicResultTransformer");
    }

    /** Binds a PUBLIC declaration and its mandatory external-boundary transformation. */
    public static <I, O> OperationBinding<I, O> publicOperation(
            OperationDefinition<I, O> definition, Operation<I, O> implementation,
            PublicResultTransformer<O> publicResultTransformer) {
        if (Objects.requireNonNull(definition, "definition").visibility()
                != OperationVisibility.PUBLIC) {
            throw new IllegalArgumentException("a public binding requires a PUBLIC declaration");
        }
        return new OperationBinding<>(definition, implementation,
                Optional.of(Objects.requireNonNull(publicResultTransformer,
                        "publicResultTransformer")));
    }

    /** Binds a PRIVATE declaration that is not invocable through the public runtime port. */
    public static <I, O> OperationBinding<I, O> privateOperation(
            OperationDefinition<I, O> definition, Operation<I, O> implementation) {
        if (Objects.requireNonNull(definition, "definition").visibility()
                != OperationVisibility.PRIVATE) {
            throw new IllegalArgumentException("a private binding requires a PRIVATE declaration");
        }
        return new OperationBinding<>(definition, implementation, Optional.empty());
    }

    public OperationDefinition<I, O> definition() { return definition; }

    /** Executes inside the owning Module and validates the Module-created output contract. */
    public CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
        OperationCall<I, O> exactCall = Objects.requireNonNull(call, "call");
        if (exactCall.operation() != definition) {
            throw new IllegalArgumentException(
                    "OperationCall must use the exact installed Operation declaration");
        }
        return implementation.invoke(exactCall).thenApply(exactCall::acceptOutput);
    }

    /**
     * Executes a PUBLIC Operation and applies the Module-owned semantic boundary before
     * returning Material to the external/public caller.
     */
    public CompletionStage<Material<O>> invokePublic(OperationCall<I, O> call) {
        if (definition.visibility() != OperationVisibility.PUBLIC
                || publicResultTransformer.isEmpty()) {
            throw new IllegalArgumentException("Operation is not PUBLIC");
        }
        OperationCall<I, O> exactCall = Objects.requireNonNull(call, "call");
        return invoke(exactCall).thenApply(internalResult -> {
            Material<O> externalResult = Objects.requireNonNull(
                    publicResultTransformer.orElseThrow().transform(internalResult),
                    "public result transformer returned null");
            if (externalResult.id().equals(internalResult.id())) {
                throw new IllegalStateException(
                        "PUBLIC result transformation must create new Material identity");
            }
            externalResult = exactCall.acceptOutput(externalResult);
            if (!externalResult.sensitivity().canReach(Privacy.PUBLIC)) {
                throw new IllegalStateException(
                        "PUBLIC Operation result must be semantically minimized to Privacy.PUBLIC");
            }
            return externalResult;
        });
    }
}
