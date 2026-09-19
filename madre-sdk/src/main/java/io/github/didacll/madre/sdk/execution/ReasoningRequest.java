package io.github.didacll.madre.sdk.execution;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Semantic reasoning intent owned by an acting Agent before physical Work exists.
 *
 * <p>The request carries the Security Algebra values accumulated from the actual current
 * construction. The bounded call contributes its real input Material and accepted Privacy
 * boundary, while the acting Agent contributes its Integrity. Additional current Materials,
 * Privacy boundaries and causal participants may be supplied when the computation really includes
 * them. Composition remains dimension-specific; this object performs no authorization or
 * cross-dimensional evaluation.</p>
 *
 * <p>Operation Risk and Autonomy are not propagated merely because an Operation declares an
 * {@code EffectProfile}: an inference mechanism does not itself realize that external effect.</p>
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
            OperationCall<?, ?> call,
            Collection<? extends Material<?>> additionalMaterials,
            Collection<Privacy> additionalPrivacyBoundaries,
            Collection<Integrity> additionalCausalParticipants,
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
        OperationCall<?, ?> boundedCall = Objects.requireNonNull(call, "call");
        Objects.requireNonNull(additionalMaterials, "additionalMaterials");
        Objects.requireNonNull(additionalPrivacyBoundaries, "additionalPrivacyBoundaries");
        Objects.requireNonNull(additionalCausalParticipants, "additionalCausalParticipants");

        Sensitivity accumulatedSensitivity = boundedCall.input().sensitivity();
        for (Material<?> material : additionalMaterials) {
            accumulatedSensitivity = accumulatedSensitivity.combine(
                    Objects.requireNonNull(material, "additional material").sensitivity());
        }

        Privacy acceptedBoundary = boundedCall.operation().acceptedMaterial()
                .get(boundedCall.input().type().id());
        if (acceptedBoundary == null) {
            throw new IllegalArgumentException(
                    "bounded Operation call has no Privacy boundary for its actual input");
        }
        Privacy accumulatedPrivacy = acceptedBoundary;
        for (Privacy boundary : additionalPrivacyBoundaries) {
            Privacy value = Objects.requireNonNull(boundary, "additional Privacy boundary");
            if (value == Privacy.SYSTEM_RESERVED) {
                throw new IllegalArgumentException(
                        "SYSTEM_RESERVED Privacy is not an ordinary reasoning constituent");
            }
            accumulatedPrivacy = accumulatedPrivacy.combine(value);
        }

        Integrity actorIntegrity = Objects.requireNonNull(
                actingAgent.integrity(), "actor.integrity()");
        if (actorIntegrity == Integrity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Integrity is not an ordinary reasoning constituent");
        }
        Integrity accumulatedIntegrity = actorIntegrity;
        for (Integrity participant : additionalCausalParticipants) {
            Integrity value = Objects.requireNonNull(
                    participant, "additional causal participant");
            if (value == Integrity.SYSTEM_RESERVED) {
                throw new IllegalArgumentException(
                        "SYSTEM_RESERVED Integrity is not an ordinary reasoning constituent");
            }
            accumulatedIntegrity = accumulatedIntegrity.combine(value);
        }

        this.computation = Objects.requireNonNull(computation, "computation");
        Objects.requireNonNull(computation.resultType(), "computation.resultType()");
        sensitivity = accumulatedSensitivity;
        privacy = accumulatedPrivacy;
        integrity = accumulatedIntegrity;
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
            OperationCall<?, ?> call,
            C computation,
            int priority,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return immediate(actor, call, List.of(), List.of(), List.of(), computation, priority,
                timeout, retryPolicy, cancellationKey, preferences);
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> immediate(
            Agent actor,
            OperationCall<?, ?> call,
            Collection<? extends Material<?>> additionalMaterials,
            Collection<Privacy> additionalPrivacyBoundaries,
            Collection<Integrity> additionalCausalParticipants,
            C computation,
            int priority,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return new ReasoningRequest<>(actor, call, additionalMaterials,
                additionalPrivacyBoundaries, additionalCausalParticipants, computation,
                ExecutionMode.IMMEDIATE, priority, Instant.now(), timeout, retryPolicy,
                cancellationKey, preferences);
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> durable(
            Agent actor,
            OperationCall<?, ?> call,
            C computation,
            int priority,
            Instant eligibleAt,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return durable(actor, call, List.of(), List.of(), List.of(), computation, priority,
                eligibleAt, timeout, retryPolicy, cancellationKey, preferences);
    }

    public static <R, C extends ReasoningComputation<R>> ReasoningRequest<R, C> durable(
            Agent actor,
            OperationCall<?, ?> call,
            Collection<? extends Material<?>> additionalMaterials,
            Collection<Privacy> additionalPrivacyBoundaries,
            Collection<Integrity> additionalCausalParticipants,
            C computation,
            int priority,
            Instant eligibleAt,
            Duration timeout,
            ReasoningRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return new ReasoningRequest<>(actor, call, additionalMaterials,
                additionalPrivacyBoundaries, additionalCausalParticipants, computation,
                ExecutionMode.DURABLE, priority, eligibleAt, timeout, retryPolicy,
                cancellationKey, preferences);
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
