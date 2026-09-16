package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Executable Java binding for one exact portable Operation contract. */
public final class OperationBinding<I, O> {
    private final OperationDefinition definition;
    private final Operation<I, O> implementation;
    private final Optional<PublicResultTransformer<O>> publicResultTransformer;
    private final boolean ownerInteractionEntryPoint;

    private OperationBinding(OperationDefinition definition,
            Operation<I, O> implementation,
            Optional<PublicResultTransformer<O>> publicResultTransformer,
            boolean ownerInteractionEntryPoint) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.implementation = Objects.requireNonNull(implementation, "implementation");
        this.publicResultTransformer = Objects.requireNonNull(
                publicResultTransformer, "publicResultTransformer");
        this.ownerInteractionEntryPoint = ownerInteractionEntryPoint;
    }

    /** Binds an ordinary bounded Operation with no special host receiver boundary. */
    public static <I, O> OperationBinding<I, O> operation(
            OperationDefinition definition, Operation<I, O> implementation) {
        return new OperationBinding<>(definition, implementation, Optional.empty(), false);
    }

    /**
     * Binds an Operation with an explicit Module-owned transformation for actual external/public
     * disclosure. Module-to-Module exposure is declared separately by the owning Module.
     */
    public static <I, O> OperationBinding<I, O> publicDisclosure(
            OperationDefinition definition, Operation<I, O> implementation,
            PublicResultTransformer<O> publicResultTransformer) {
        return new OperationBinding<>(definition, implementation,
                Optional.of(Objects.requireNonNull(publicResultTransformer,
                        "publicResultTransformer")), false);
    }

    /**
     * Binds an Operation that the owning Module intentionally offers to the installed product's
     * selected owner-interaction surface. This is not Module exposure or external/public
     * disclosure and grants no special runtime privilege.
     */
    public static <I, O> OperationBinding<I, O> ownerInteractionOperation(
            OperationDefinition definition, Operation<I, O> implementation) {
        return new OperationBinding<>(definition, implementation, Optional.empty(), true);
    }

    public OperationDefinition definition() { return definition; }

    /** True only for an explicit host owner-interaction entry declared by the owning Module. */
    public boolean ownerInteractionEntryPoint() { return ownerInteractionEntryPoint; }

    /** Executes inside the owning Module. The Operation owns its single output-validation path. */
    public CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
        OperationCall<I, O> exactCall = Objects.requireNonNull(call, "call");
        if (!definition.equals(exactCall.operation())) {
            throw new IllegalArgumentException(
                    "OperationCall contract differs from the installed Operation binding");
        }
        return implementation.invoke(exactCall);
    }

    /**
     * Executes through the actual external/public receiver boundary and applies the Module-owned
     * semantic transformation before disclosure.
     */
    public CompletionStage<Material<O>> invokePublic(OperationCall<I, O> call) {
        if (publicResultTransformer.isEmpty()) {
            throw new IllegalArgumentException(
                    "Operation has no external/public disclosure transformation");
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
                        "external/public result must be semantically minimized to Privacy.PUBLIC");
            }
            return externalResult;
        });
    }
}
