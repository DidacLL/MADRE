package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import java.util.List;
import java.util.Objects;

/** A valid construction of one exact consequential Operation invocation. */
public record ConsequentialOperationInvocation<I, O>(OperationDefinition<I, O> operation,
        EffectProfile effectProfile, Material<I> input,
        List<ActualParticipant<?>> nonUserCausalParticipants,
        List<ActualParticipant<?>> physicalRealizers) {
    public ConsequentialOperationInvocation {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(effectProfile, "effectProfile");
        Objects.requireNonNull(input, "input");
        nonUserCausalParticipants = List.copyOf(nonUserCausalParticipants);
        physicalRealizers = List.copyOf(physicalRealizers);
        Privacy receiver = operation.acceptedMaterial().get(input.type().id());
        if (receiver == null || !input.sensitivity().canReach(receiver)) {
            throw new IllegalArgumentException("input Material cannot reach this Operation");
        }
        if (!effectProfile.id().moduleId().equals(operation.id().moduleId())
                || !effectProfile.equals(operation.effectProfiles().get(effectProfile.id()))) {
            throw new IllegalArgumentException("EffectProfile is not declared by this Operation");
        }
        if (physicalRealizers.isEmpty()) throw new IllegalArgumentException("at least one physical realizer is required");
        Integrity causalIntegrity = nonUserCausalParticipants.stream().map(ActualParticipant::integrity)
                .reduce(Integrity.I5, Integrity::combine);
        Integrity realizerIntegrity = physicalRealizers.stream().map(ActualParticipant::integrity)
                .reduce(Integrity.I5, Integrity::combine);
        if (effectProfile.nonUserCausalDemandRank() > causalIntegrity.rank()) {
            throw new IllegalArgumentException("non-user causal participants do not support this EffectProfile");
        }
        if (effectProfile.risk().rank() > realizerIntegrity.rank()) {
            throw new IllegalArgumentException("physical realizers do not support this EffectProfile Risk");
        }
    }
}
