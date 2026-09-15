package io.github.didacll.madre.sdk.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkState;
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
import org.junit.jupiter.api.Test;

final class ProgrammableReasoningServiceTest {
    private static final ModuleId MODULE = new ModuleId("test.module");
    private static final MaterialType<String> INPUT = textType("input");
    private static final MaterialType<String> OUTPUT = textType("output");
    private static final OperationDefinition OPERATION =
            new OperationDefinition(new OperationId(MODULE, "compute"), "test computation",
                    OperationVisibility.PRIVATE, Map.of(INPUT.id(), Privacy.SECRET),
                    Map.of(OUTPUT.id(), Sensitivity.S3), Map.of());

    @Test
    void executesArbitraryNonTextComputation() {
        ProgrammableReasoningService service = new ProgrammableReasoningService()
                .respond(ScalarComputation.class, computation -> computation.value() * 3);
        ReasoningRequest<Integer, ScalarComputation> request = ReasoningRequest.immediate(
                call(), new ScalarComputation(14), 0, Duration.ofSeconds(1),
                ReasoningRetryPolicy.none(), Optional.empty(), ReasoningPreferences.unconstrained());

        assertEquals(42, service.execute(request).toCompletableFuture().join());
    }

    @Test
    void exposesControllableDurableLifecycleWithoutPretendingToBeKernel() {
        ProgrammableReasoningService service = new ProgrammableReasoningService()
                .respond(ScalarComputation.class, computation -> computation.value() + 1);
        ReasoningRequest<Integer, ScalarComputation> request = ReasoningRequest.durable(
                call(), new ScalarComputation(9), 0, Instant.EPOCH, Duration.ofSeconds(1),
                ReasoningRetryPolicy.none(), Optional.empty(), ReasoningPreferences.unconstrained());

        var id = service.submit(request);
        assertEquals(WorkState.QUEUED, service.inspect(id).orElseThrow().state());
        assertTrue(service.start(id));
        assertEquals(WorkState.RUNNING, service.inspect(id).orElseThrow().state());
        assertTrue(service.complete(id));
        assertEquals(WorkState.SUCCEEDED, service.inspect(id).orElseThrow().state());
        assertEquals(10, service.collect(id, Integer.class).orElseThrow());
        assertTrue(service.acknowledge(id));
        assertFalse(service.inspect(id).isPresent());
    }

    private static OperationCall<String, String> call() {
        return OperationCall.withoutEffect(OPERATION,
                new Material<>(new MaterialId(MODULE, "input-1"), INPUT, "payload",
                        Sensitivity.S2));
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(MODULE, name), String.class,
                "text/plain; charset=utf-8", new MaterialCodec<>() {
                    @Override public byte[] encode(String value) {
                        return value.getBytes(StandardCharsets.UTF_8);
                    }
                    @Override public String decode(byte[] bytes) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                });
    }

    private record ScalarComputation(int value) implements ReasoningComputation<Integer> {
        @Override public Class<Integer> resultType() { return Integer.class; }
    }
}
