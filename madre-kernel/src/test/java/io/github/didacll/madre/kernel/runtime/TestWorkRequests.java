package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.CancellationKey;
import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.PhysicalRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public-SDK construction used by Kernel behavior fixtures. */
final class TestWorkRequests {
    private static final ModuleId MODULE = new ModuleId("test.owner");
    private static final MaterialCodec<String> CODEC = new MaterialCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    private TestWorkRequests() { }

    static <R> WorkRequest<String, R> immediate(String command, Class<R> resultType,
            Sensitivity sensitivity, Optional<Risk> risk, int priority, Duration timeout,
            PhysicalRetryPolicy retryPolicy, PhysicalPreferences preferences) {
        return WorkRequest.immediate(call(sensitivity, risk), command, resultType, priority,
                timeout, retryPolicy, Optional.empty(), preferences);
    }

    static <R> WorkRequest<String, R> durable(String command, Class<R> resultType,
            Sensitivity sensitivity, Optional<Risk> risk, int priority, Instant eligibleAt,
            Duration timeout, PhysicalRetryPolicy retryPolicy,
            Optional<CancellationKey> cancellationKey, PhysicalPreferences preferences) {
        return WorkRequest.durable(call(sensitivity, risk), command, resultType, priority,
                eligibleAt, timeout, retryPolicy, cancellationKey, preferences);
    }

    private static OperationCall<String, String> call(Sensitivity sensitivity,
            Optional<Risk> risk) {
        String suffix = UUID.randomUUID().toString();
        MaterialType<String> inputType = new MaterialType<>(
                new MaterialTypeId(MODULE, "input-" + suffix), String.class,
                "text/plain", CODEC);
        Material<String> input = new Material<>(new MaterialId(MODULE, suffix), inputType,
                "fixture", sensitivity);
        OperationId operationId = new OperationId(MODULE, "operation-" + suffix);
        if (risk.isEmpty()) {
            OperationDefinition<String, String> operation = new OperationDefinition<>(
                    operationId, "Fixture physical execution", OperationVisibility.PRIVATE,
                    Map.of(inputType.id(), Privacy.SECRET), Map.of(), Map.of());
            return OperationCall.withoutEffect(operation, input);
        }
        EffectProfile profile = new EffectProfile(
                new EffectProfileId(operationId, "profile"), risk.orElseThrow(), Autonomy.A5);
        OperationDefinition<String, String> operation = new OperationDefinition<>(operationId,
                "Fixture physical execution", OperationVisibility.PRIVATE,
                Map.of(inputType.id(), Privacy.SECRET), Map.of(), Map.of(profile.id(), profile));
        return OperationCall.withEffect(operation, profile, input, List.of());
    }
}
