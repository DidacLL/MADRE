package io.github.didacll.madre.sdk.execution;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.Agent;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

/**
 * Semantic reasoning intent owned by an acting Agent before physical Work exists.
 *
 * <p>The request carries Security Algebra values composed only from the actual current semantic
 * constituents supplied by the Agent. Materials contribute Sensitivity, receiver boundaries
 * contribute Privacy, and the acting Agent plus other current causal participants contribute
 * Integrity. No Operation repertoire, possible output, installed engine or hypothetical future
 * constituent participates in these values.</p>
 *
 * <p>Composition remains dimension-specific. This value performs no authorization, provider
 * policy or cross-dimensional permission evaluation. Risk and Autonomy remain properties of real
 * consequential EffectProfiles and are not copied into inference merely for symmetry.</p>
 */
public final class ReasoningRequest<R, C extends ReasoningComputation<R>> {
    private final AgentId actor;
    private final C computation;
    private final Sensitivity sensitivity;
    private final Privacy privacy;
    private final Integrity integrity;
    private final ExecutionMode mode;
    private final int priority;
    private final Instant eligibleAt;
    private final Duration timeout;
    private final ReasoningRetryPolicy retryPolicy;
    private final Optional<CancellationKey> cancellationKey;
    private final ReasoningPreferences preferences;

    private ReasoningRequest(
            Agent actor,
            Collection<? extends Material<?>> materials,
            Collection<Privacy> receiverBoundaries,
            Collection<Integrity> causalParticipants,
            C computation,
            ExecutionMode mode,
            int priority,
            Instant eligibleAt,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        Agent actingAgent = Objects.requireNonNull(actor, "actor");
        this.actor = Objects.requireNonNull(actingAgent.id(), "actor.id()");
        sensitivity = composeSensitivity(materials);
        privacy = composePrivacy(receiverBoundaries);
        integrity = composeIntegrity(actingAgent, causalParticipants);

        this.computation = Objects.requireNonNull(computation, "computation");
        Objects.requireNonNull(computation.resultType(), "computation.resultType()");
        this.mode = Objects.requireNonNull(mode, "mode");
        if (priority < 0) {
            throw new IllegalArgumentException("priority must not be negative");
        }
        this.priority = priority;
        this.eligibleAt = Objects.requireNonNull(eligibleAt, "eligibleAt");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.cancellationKey = Objects.requireNonNull(cancellationKey, "cancellationKey");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> immediate(
            Agent actor,
            Collection<? extends Material<?>> materials,
            Collection<Privacy> receiverBoundaries,
            Collection<Integrity> causalParticipants,
            C computation,
            int priority,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return new ReasoningRequest<>(
                actor, materials, receiverBoundaries, causalParticipants, computation,
                ExecutionMode.IMMEDIATE, priority, Instant.now(), timeout, retryPolicy,
                cancellationKey, preferences);
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> durable(
            Agent actor,
            Collection<? extends Material<?>> materials,
            Collection<Privacy> receiverBoundaries,
            Collection<Integrity> causalParticipants,
            C computation,
            int priority,
            Instant eligibleAt,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return new ReasoningRequest<>(
                actor, materials, receiverBoundaries, causalParticipants, computation,
                ExecutionMode.DURABLE, priority, eligibleAt, timeout, retryPolicy,
                cancellationKey, preferences);
    }

    private static Sensitivity composeSensitivity(
            Collection<? extends Material<?>> materials) {
        Objects.requireNonNull(materials, "materials");
        Iterator<? extends Material<?>> iterator = materials.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException(
                    "reasoning requires at least one actual Material constituent");
        }
        Material<?> first = Objects.requireNonNull(
                iterator.next(), "material");
        Sensitivity value = first.sensitivity();
        while (iterator.hasNext()) {
            value = value.combine(
                    Objects.requireNonNull(iterator.next(), "material").sensitivity());
        }
        return value;
    }

    private static Privacy composePrivacy(Collection<Privacy> receiverBoundaries) {
        Objects.requireNonNull(receiverBoundaries, "receiverBoundaries");
        Iterator<Privacy> iterator = receiverBoundaries.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException(
                    "reasoning requires at least one actual Privacy boundary");
        }
        Privacy value = ordinaryPrivacy(
                Objects.requireNonNull(iterator.next(), "Privacy boundary"));
        while (iterator.hasNext()) {
            value = value.combine(ordinaryPrivacy(
                    Objects.requireNonNull(iterator.next(), "Privacy boundary")));
        }
        return value;
    }

    private static Integrity composeIntegrity(
            Agent actor, Collection<Integrity> causalParticipants) {
        Objects.requireNonNull(causalParticipants, "causalParticipants");
        Integrity value = ordinaryIntegrity(
                Objects.requireNonNull(actor.integrity(), "actor.integrity()"));
        for (Integrity participant : causalParticipants) {
            value = value.combine(ordinaryIntegrity(
                    Objects.requireNonNull(participant, "causal participant")));
        }
        return value;
    }

    private static Privacy ordinaryPrivacy(Privacy value) {
        if (value == Privacy.SYSTEM_RESERVED) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Privacy is not an ordinary reasoning constituent");
        }
        return value;
    }

    private static Integrity ordinaryIntegrity(Integrity value) {
        if (value == Integrity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Integrity is not an ordinary reasoning constituent");
        }
        return value;
    }

    public AgentId actor() { return actor; }
    public C computation() { return computation; }
    public Class<R> resultType() { return computation.resultType(); }
    public Sensitivity sensitivity() { return sensitivity; }
    public Privacy privacy() { return privacy; }
    public Integrity integrity() { return integrity; }
    public ExecutionMode mode() { return mode; }
    public int priority() { return priority; }
    public Instant eligibleAt() { return eligibleAt; }
    public Duration timeout() { return timeout; }
    public ReasoningRetryPolicy retryPolicy() { return retryPolicy; }
    public Optional<CancellationKey> cancellationKey() { return cancellationKey; }
    public ReasoningPreferences preferences() { return preferences; }
}
