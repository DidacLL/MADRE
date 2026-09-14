package consumer;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Independent executable Module compiled only against the published MADRE SDK. */
public final class IndependentDefinition {
    static final ModuleId ID = new ModuleId("phd.module");
    static final MaterialType<String> REQUEST = textType("request");
    static final MaterialType<String> RESULT = textType("result");
    static final OperationId INSPECT = new OperationId(ID, "inspect");
    private static final OperationDefinition<String, String> OPERATION = new OperationDefinition<>(
            INSPECT, "Inspect independent Module input", OperationVisibility.PUBLIC,
            Map.of(REQUEST.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S4), Map.of());
    private static final ModuleDefinition DEFINITION = new ModuleDefinition(ID, "1.0.0",
            "Independent installation and invocation fixture",
            Map.of(REQUEST.id(), REQUEST, RESULT.id(), RESULT), Set.of(), Map.of(), Map.of(),
            Map.of(INSPECT, OPERATION));

    private IndependentDefinition() { }

    static ModuleInstance instance() {
        Operation<String, String> implementation = new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                return CompletableFuture.completedFuture(material(RESULT,
                        "private:" + call.input().payload(), Sensitivity.S4));
            }
        };
        return new ModuleInstance(DEFINITION, Map.of(INSPECT,
                OperationBinding.publicOperation(OPERATION, implementation, internal -> {
                    String payload = internal.payload();
                    String minimized = payload.startsWith("private:")
                            ? "public:" + payload.substring("private:".length())
                            : "public";
                    return material(RESULT, minimized, Sensitivity.S1);
                })));
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
