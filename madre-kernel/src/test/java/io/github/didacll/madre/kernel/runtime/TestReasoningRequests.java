package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.CancellationKey;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public-SDK reasoning request construction used by Kernel behavior fixtures. */
final class TestReasoningRequests {
    private static final ModuleId MODULE = new ModuleId("test.owner");
    private static final MaterialCodec<String> CODEC = new MaterialCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    private TestReasoningRequests() { }

    record FixtureComputation(String value) implements ReasoningComputation<String> {
        @Override public Class<String> resultType() { return String.class; }
    }

    static ReasoningRequest<String, FixtureComputation> immediate(String value,
            Sensitivity sensitivity, int priority, Duration timeout,
            ReasoningRetryPolicy retryPolicy, ReasoningPreferences preferences) {
        return ReasoningRequest.immediate(call(sensitivity), new FixtureComputation(value),
                priority, timeout, retryPolicy, Optional.empty(), preferences);
    }

    static ReasoningRequest<String, FixtureComputation> durable(String value,
            Sensitivity sensitivity, int priority, Instant eligibleAt, Duration timeout,
            ReasoningRetryPolicy retryPolicy, Optional<CancellationKey> cancellationKey,
            ReasoningPreferences preferences) {
        return ReasoningRequest.durable(call(sensitivity), new FixtureComputation(value),
                priority, eligibleAt, timeout, retryPolicy, cancellationKey, preferences);
    }

    private static OperationCall<String, String> call(Sensitivity sensitivity) {
        String suffix = UUID.randomUUID().toString();
        MaterialType<String> inputType = new MaterialType<>(
                new MaterialTypeId(MODULE, "input-" + suffix), String.class,
                "text/plain", CODEC);
        Material<String> input = new Material<>(new MaterialId(MODULE, suffix), inputType,
                "fixture", sensitivity);
        OperationDefinition<String, String> operation = new OperationDefinition<>(
                new OperationId(MODULE, "operation-" + suffix), "Fixture reasoning",
                OperationVisibility.PRIVATE, Map.of(inputType.id(), Privacy.SECRET),
                Map.of(), Map.of());
        return OperationCall.withoutEffect(operation, input);
    }
}
