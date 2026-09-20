package io.github.didacll.madre.kernel.client;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public record WorkRequest(
        String workType,
        byte[] input,
        Effort effort,
        Urgency urgency,
        Set<String> requiredCapabilities,
        Set<String> eligibleEngineIds,
        Optional<String> exactEngineId,
        Optional<String> exactModelId,
        OptionalLong eligibleAtMs,
        OptionalLong deadlineMs,
        OptionalLong timeoutMs,
        RetryPolicy retry) {

    public WorkRequest {
        Objects.requireNonNull(workType, "workType");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
        Objects.requireNonNull(eligibleEngineIds, "eligibleEngineIds");
        Objects.requireNonNull(exactEngineId, "exactEngineId");
        Objects.requireNonNull(exactModelId, "exactModelId");
        Objects.requireNonNull(eligibleAtMs, "eligibleAtMs");
        Objects.requireNonNull(deadlineMs, "deadlineMs");
        Objects.requireNonNull(timeoutMs, "timeoutMs");
        Objects.requireNonNull(retry, "retry");
        if (workType.isBlank()) {
            throw new IllegalArgumentException("workType must not be blank");
        }
        if (input.length > Protocol.MAX_C1_TEXT_GENERATION_OPAQUE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "C1 text-generation/v1 input exceeds the 1 MiB bounded payload limit; streaming/spooling is deferred");
        }
        requiredCapabilities = copyIdentifiers(requiredCapabilities, "requiredCapabilities");
        eligibleEngineIds = copyIdentifiers(eligibleEngineIds, "eligibleEngineIds");
        exactEngineId = validateOptionalIdentifier(exactEngineId, "exactEngineId");
        exactModelId = validateOptionalIdentifier(exactModelId, "exactModelId");
        validateNonnegative(eligibleAtMs, "eligibleAtMs");
        validateNonnegative(deadlineMs, "deadlineMs");
        if (timeoutMs.isPresent() && timeoutMs.getAsLong() <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0 when supplied");
        }
        input = input.clone();
    }

    public WorkRequest(String workType, byte[] input) {
        this(
                workType,
                input,
                Effort.STANDARD,
                Urgency.NORMAL,
                Set.of(),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                RetryPolicy.noRetry());
    }

    @Override
    public byte[] input() {
        return input.clone();
    }

    private static Set<String> copyIdentifiers(Set<String> values, String field) {
        for (String value : values) {
            validateIdentifier(value, field);
        }
        return Set.copyOf(values);
    }

    private static Optional<String> validateOptionalIdentifier(Optional<String> value, String field) {
        value.ifPresent(item -> validateIdentifier(item, field));
        return value;
    }

    private static void validateIdentifier(String value, String field) {
        Objects.requireNonNull(value, field + " entry");
        if (value.isBlank() || value.indexOf(',') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " entries must be nonblank comma-free identifiers");
        }
    }

    private static void validateNonnegative(OptionalLong value, String field) {
        if (value.isPresent() && value.getAsLong() < 0) {
            throw new IllegalArgumentException(field + " must be >= 0 when supplied");
        }
    }
}
