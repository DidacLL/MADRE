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

    /** Binds a PUBLIC declaration and its mandatory external-boundary transformation. */
    public static <I, O> OperationBinding<I, O> publicOperation(
            OperationDefinition definition, Operation<I, O> implementation,
            PublicResultTransformer<O> publicResultTransformer) {
        if (Objects.requireNonNull(definition, "definition").visibility()
                != OperationVisibility.PUBLIC) {
            throw new IllegalArgumentException("a public binding requires a PUBLIC declaration");
        }
        return new OperationBinding<>(definition, implementation,
                Optional.of(Objects.requireNonNull(publicResultTransformer,
                        "publicResultTransformer")), false);
    }

    /** Binds a PRIVATE declaration that has no host/product interaction entry. */
    public static <I, O> OperationBinding<I, O> privateOperation(
            OperationDefinition definition, Operation<I, O> implementation) {
        requirePrivate(definition, "a private binding requires a PRIVATE declaration");
        return new OperationBinding<>(definition, implementation, Optional.empty(), false);
    }

    /**
     * Binds a PRIVATE Operation that the owning Module intentionally offers to the installed
     * product's selected owner-interaction surface. This is not PUBLIC exposure and does not make
     * the Operation reachable to other Modules or arbitrary host/debug invocation.
     */
    public static <I, O> OperationBinding<I, O> ownerInteractionOperation(
            OperationDefinition definition, Operation<I, O> implementation) {
        requirePrivate(definition,
                "an owner-interaction binding requires a PRIVATE declaration");
        return new OperationBinding<>(definition, implementation, Optional.empty(), true);
    }

    private static void requirePrivate(OperationDefinition definition, String message) {
        if (Objects.requireNonNull(definition, "definition").visibility()
                != OperationVisibility.PRIVATE) {
            throw new IllegalArgumentException(message);
        }
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
