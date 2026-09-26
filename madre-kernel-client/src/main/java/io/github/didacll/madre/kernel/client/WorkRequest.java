package io.github.didacll.madre.kernel.client;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public record WorkRequest(
        List<ConcretePhysicalInvocation> candidates,
        byte[] input,
        Urgency urgency,
        OptionalLong eligibleAtMs,
        OptionalLong deadlineMs,
        OptionalLong attemptTimeoutMs,
        RetryPolicy retry) {

    public WorkRequest {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(eligibleAtMs, "eligibleAtMs");
        Objects.requireNonNull(deadlineMs, "deadlineMs");
        Objects.requireNonNull(attemptTimeoutMs, "attemptTimeoutMs");
        Objects.requireNonNull(retry, "retry");
        if (candidates.isEmpty() || candidates.size() > 32) {
            throw new IllegalArgumentException("Work requires between 1 and 32 already-approved invocation candidates");
        }
        Set<InvocationId> ids = new HashSet<>();
        for (ConcretePhysicalInvocation candidate : candidates) {
            Objects.requireNonNull(candidate, "candidates entry");
            if (!ids.add(candidate.id())) {
                throw new IllegalArgumentException("candidate ids must be unique within Work");
            }
        }
        if (input.length > Protocol.MAX_BOUNDED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("input exceeds the 1 MiB bounded physical payload limit");
        }
        validateNonnegative(eligibleAtMs, "eligibleAtMs");
        validateNonnegative(deadlineMs, "deadlineMs");
        validateNonnegative(attemptTimeoutMs, "attemptTimeoutMs");
        candidates = List.copyOf(candidates);
        input = input.clone();
    }

    public WorkRequest(List<ConcretePhysicalInvocation> candidates, byte[] input) {
        this(candidates, input, Urgency.NORMAL, OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), RetryPolicy.noRetry());
    }

    @Override
    public byte[] input() {
        return input.clone();
    }

    private static void validateNonnegative(OptionalLong value, String field) {
        if (value.isPresent() && value.getAsLong() < 0) {
            throw new IllegalArgumentException(field + " must be >= 0 when supplied");
        }
    }
}
