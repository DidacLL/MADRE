package consumer;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.text.TextInferenceCommand;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Independent executable Module compiled only against published MADRE artifacts. */
public final class IndependentDefinition {
    static final ModuleId ID = new ModuleId("phd.module");
    static final MaterialType<String> REQUEST = textType("request");
    static final MaterialType<String> RESULT = textType("result");
    static final OperationId INSPECT = new OperationId(ID, "inspect");
    static final OperationId REASON = new OperationId(ID, "reason");
    private static final OperationDefinition<String, String> INSPECT_OPERATION = operation(
            INSPECT, "Inspect independent Module input without reasoning");
    private static final OperationDefinition<String, String> REASON_OPERATION = operation(
            REASON, "Interpret independently installed reasoning output");
    private static final ModuleDefinition DEFINITION = new ModuleDefinition(ID, "1.0.0",
            "Independent installation, reasoning and public-boundary fixture",
            Map.of(REQUEST.id(), REQUEST, RESULT.id(), RESULT), Set.of(), Map.of(), Map.of(),
            Map.of(INSPECT, INSPECT_OPERATION, REASON, REASON_OPERATION));

    private IndependentDefinition() { }

    static ModuleInstance instance(ReasoningService reasoning) {
        Operation<String, String> inspect = new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                return CompletableFuture.completedFuture(material(RESULT,
                        "private:" + call.input().payload(), Sensitivity.S4));
            }
        };
        Operation<String, String> reason = new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                ReasoningRequest<io.github.didacll.madre.text.TextInferenceResult,
                        TextInferenceCommand> request = ReasoningRequest.immediate(call,
                                new TextInferenceCommand(call.input().payload(), 32, List.of()),
                                0, Duration.ofSeconds(5), ReasoningRetryPolicy.none(),
                                Optional.empty(), ReasoningPreferences.unconstrained());
                return reasoning.execute(request).thenApply(result -> material(RESULT,
                        "private:reasoned:" + result.text(), Sensitivity.S4));
            }
        };
        return new ModuleInstance(DEFINITION, Map.of(
                INSPECT, OperationBinding.publicOperation(INSPECT_OPERATION, inspect,
                        IndependentDefinition::publicResult),
                REASON, OperationBinding.publicOperation(REASON_OPERATION, reason,
                        IndependentDefinition::publicResult)));
    }

    private static OperationDefinition<String, String> operation(OperationId id,
            String description) {
        return new OperationDefinition<>(id, description, OperationVisibility.PUBLIC,
                Map.of(REQUEST.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S4), Map.of());
    }

    private static Material<String> publicResult(Material<String> internal) {
        String payload = internal.payload();
        String minimized = payload.startsWith("private:")
                ? "public:" + payload.substring("private:".length())
                : "public";
        return material(RESULT, minimized, Sensitivity.S1);
    }

    private static Material<String> material(MaterialType<String> type, String payload,
            Sensitivity sensitivity) {
        return new Material<>(new MaterialId(ID, UUID.randomUUID().toString()), type, payload,
                sensitivity);
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(ID, name), String.class,
                "text/plain; charset=utf-8", new MaterialCodec<>() {
                    @Override public byte[] encode(String value) {
                        return value.getBytes(StandardCharsets.UTF_8);
                    }
                    @Override public String decode(byte[] bytes) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                });
    }
}
