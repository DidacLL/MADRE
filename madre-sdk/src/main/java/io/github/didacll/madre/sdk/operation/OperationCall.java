package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.List;
import java.util.Objects;

/** One intrinsically composable call to bounded Module-owned behavior. */
public record OperationCall<I, O>(OperationDefinition<I, O> operation,
        EffectProfile effectProfile, Material<I> input,
        List<Integrity> nonUserCausalParticipants) {
    public OperationCall {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(effectProfile, "effectProfile");
        Objects.requireNonNull(input, "input");
        nonUserCausalParticipants = List.copyOf(nonUserCausalParticipants);
        Privacy receiver = operation.acceptedMaterial().get(input.type().id());
        if (receiver == null || !input.sensitivity().canReach(receiver)) {
            throw new IllegalArgumentException("input Material cannot reach this Operation");
        }
        if (!effectProfile.id().operationId().equals(operation.id())
                || !effectProfile.equals(operation.effectProfiles().get(effectProfile.id()))) {
            throw new IllegalArgumentException("EffectProfile is not declared by this Operation");
        }
        Integrity causalIntegrity = nonUserCausalParticipants.stream()
                .reduce(Integrity.I5, Integrity::combine);
        if (effectProfile.nonUserCausalDemandRank() > causalIntegrity.rank()) {
            throw new IllegalArgumentException(
                    "non-user causal participants do not support this EffectProfile");
        }
    }
}
