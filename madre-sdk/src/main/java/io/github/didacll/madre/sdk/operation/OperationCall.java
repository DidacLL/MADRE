package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed invocation of an Operation after applying the MADRE facts declared for that bounded
 * execution: accepted Material type and, when consequential, one exact EffectProfile.
 *
 * <p>The call does not care whether the Operation implementation is arithmetic, file/network I/O,
 * a script/process, reasoning-backed behavior or any other Module-owned code. It is the generic
 * typed invocation boundary, not a description of the implementation technique.</p>
 */
public final class OperationCall<I, O> {
    private final OperationDefinition operation;
    private final Optional<EffectProfile> effectProfile;
    private final Material<I> input;

    private OperationCall(OperationDefinition operation,
            Optional<EffectProfile> effectProfile, Material<I> input) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.effectProfile = Objects.requireNonNull(effectProfile, "effectProfile");
        this.input = Objects.requireNonNull(input, "input");
        if (!operation.acceptedMaterial().containsKey(input.type().id())) {
            throw new IllegalArgumentException("input Material type is not accepted by this Operation");
        }
        if (effectProfile.isEmpty()) {
            if (!operation.effectProfiles().isEmpty()) {
                throw new IllegalArgumentException("this Operation requires one of its EffectProfiles");
            }
            return;
        }

        EffectProfile profile = effectProfile.orElseThrow();
        if (!profile.id().operationId().equals(operation.id())
                || !profile.equals(operation.effectProfiles().get(profile.id()))) {
            throw new IllegalArgumentException("EffectProfile is not declared by this Operation");
        }
    }

    /** Constructs a call for an Operation that declares no consequential execution profile. */
    public static <I, O> OperationCall<I, O> withoutEffect(
            OperationDefinition operation, Material<I> input) {
        return new OperationCall<>(operation, Optional.empty(), input);
    }

    /** Constructs a call for one exact consequential variant and its actual causal values. */
    public static <I, O> OperationCall<I, O> withEffect(
            OperationDefinition operation, EffectProfile effectProfile,
            Material<I> input) {
        return new OperationCall<>(operation, Optional.of(effectProfile), input);
    }

    public OperationDefinition operation() { return operation; }
    public Optional<EffectProfile> effectProfile() { return effectProfile; }
    public Material<I> input() { return input; }

    /** Confirms that Module-created output stays inside this Operation's declared contract. */
    public Material<O> acceptOutput(Material<O> output) {
        Objects.requireNonNull(output, "output");
        Sensitivity maximum = operation.producedMaterial().get(output.type().id());
        if (maximum == null) {
            throw new IllegalArgumentException("output Material is not declared by this Operation");
        }
        if (output.sensitivity().rank() > maximum.rank()) {
            throw new IllegalArgumentException(
                    "output Material exceeds this Operation's declared Sensitivity");
        }
        return output;
    }
}
