package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One intrinsically composable typed Java call to bounded Module-owned behavior. */
public final class OperationCall<I, O> {
    private final OperationDefinition operation;
    private final Optional<EffectProfile> effectProfile;
    private final Material<I> input;

    private OperationCall(OperationDefinition operation,
            Optional<EffectProfile> effectProfile, Material<I> input,
            List<Integrity> nonUserCausalParticipants) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.effectProfile = Objects.requireNonNull(effectProfile, "effectProfile");
        this.input = Objects.requireNonNull(input, "input");
        List<Integrity> participants = List.copyOf(nonUserCausalParticipants);
        if (participants.contains(Integrity.SYSTEM_RESERVED)) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Integrity is not an ordinary causal participant value");
        }

        Privacy receiver = operation.acceptedMaterial().get(input.type().id());
        if (receiver == null || !input.sensitivity().canReach(receiver)) {
            throw new IllegalArgumentException("input Material cannot reach this Operation");
        }
        if (effectProfile.isEmpty()) {
            if (!operation.effectProfiles().isEmpty()) {
                throw new IllegalArgumentException("this Operation requires one of its EffectProfiles");
            }
            if (!participants.isEmpty()) {
                throw new IllegalArgumentException(
                        "non-user causal participants require an EffectProfile");
            }
            return;
        }

        EffectProfile profile = effectProfile.orElseThrow();
        if (!profile.id().operationId().equals(operation.id())
                || !profile.equals(operation.effectProfiles().get(profile.id()))) {
            throw new IllegalArgumentException("EffectProfile is not declared by this Operation");
        }
        Integrity causalIntegrity = participants.stream().reduce(Integrity.I5, Integrity::combine);
        if (!profile.isSupportedBy(causalIntegrity)) {
            throw new IllegalArgumentException(
                    "non-user causal participants do not support this EffectProfile");
        }
    }

    /** Constructs a call for an Operation that has no consequential execution profile. */
    public static <I, O> OperationCall<I, O> withoutEffect(
            OperationDefinition operation, Material<I> input) {
        return new OperationCall<>(operation, Optional.empty(), input, List.of());
    }

    /** Constructs a call for one exact consequential variant and its actual causal values. */
    public static <I, O> OperationCall<I, O> withEffect(
            OperationDefinition operation, EffectProfile effectProfile,
            Material<I> input, List<Integrity> nonUserCausalParticipants) {
        return new OperationCall<>(operation, Optional.of(effectProfile), input,
                nonUserCausalParticipants);
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
        if (!output.id().moduleId().equals(operation.id().moduleId())) {
            throw new IllegalArgumentException("output Material must be owned by the Operation's Module");
        }
        if (output.sensitivity().rank() > maximum.rank()) {
            throw new IllegalArgumentException(
                    "output Material exceeds this Operation's declared Sensitivity");
        }
        return output;
    }
}
