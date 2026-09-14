package interop.callee;

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
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Independently compiled callee used by the installed Module interoperability acceptance. */
public final class CalleeProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("interop.callee");
    private static final ModuleId CALLER = new ModuleId("interop.caller");
    private static final MaterialTypeId CALLER_REQUEST = new MaterialTypeId(CALLER, "request");
    private static final MaterialCodec<String> STRINGS = new MaterialCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };
    private static final MaterialType<String> REQUEST = new MaterialType<>(
            new MaterialTypeId(ID, "request"), String.class, "text/plain", STRINGS);
    private static final MaterialType<String> RESULT = new MaterialType<>(
            new MaterialTypeId(ID, "sensitive-result"), String.class, "text/plain", STRINGS);
    private static final MaterialType<String> OTHER_RESULT = new MaterialType<>(
            new MaterialTypeId(ID, "other-result"), String.class, "text/plain", STRINGS);

    @Override public ModuleId moduleId() { return ID; }

    @Override public ModuleInstance create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(configuration, "configuration");
        if (!configuration.moduleId().equals(ID) || !configuration.keys().isEmpty()) {
            throw new IllegalArgumentException("unexpected callee configuration");
        }

        OperationDefinition<String, String> sensitive = operation("sensitive", Sensitivity.S4,
                RESULT, true);
        OperationDefinition<String, String> tooSensitive = operation("too-sensitive",
                Sensitivity.S5, RESULT, false);
        OperationDefinition<String, String> undeclared = operation("undeclared-result",
                Sensitivity.S2, OTHER_RESULT, false);
        OperationDefinition<String, String> privateSensitive = new OperationDefinition<>(
                new OperationId(ID, "private-sensitive"), "Private sensitive behavior",
                OperationVisibility.PRIVATE,
                Map.of(REQUEST.id(), Privacy.MODULE, CALLER_REQUEST, Privacy.MODULE),
                Map.of(RESULT.id(), Sensitivity.S4), Map.of());

        Map<OperationId, OperationDefinition<?, ?>> definitions = Map.of(
                sensitive.id(), sensitive,
                tooSensitive.id(), tooSensitive,
                undeclared.id(), undeclared,
                privateSensitive.id(), privateSensitive);
        ModuleDefinition definition = new ModuleDefinition(ID, "1.0.0",
                "Independent interoperability callee",
                Map.of(REQUEST.id(), REQUEST, RESULT.id(), RESULT,
                        OTHER_RESULT.id(), OTHER_RESULT),
                Set.of(CALLER_REQUEST), Map.of(), Map.of(), definitions);

        Map<OperationId, OperationBinding<?, ?>> bindings = Map.of(
                sensitive.id(), OperationBinding.publicOperation(sensitive,
                        behavior(RESULT, Sensitivity.S4, "classified:"),
                        CalleeProvider::publicResult),
                tooSensitive.id(), OperationBinding.publicOperation(tooSensitive,
                        behavior(RESULT, Sensitivity.S5, "too-sensitive:"),
                        CalleeProvider::publicResult),
                undeclared.id(), OperationBinding.publicOperation(undeclared,
                        behavior(OTHER_RESULT, Sensitivity.S2, "undeclared:"),
                        CalleeProvider::publicResult),
                privateSensitive.id(), OperationBinding.privateOperation(privateSensitive,
                        behavior(RESULT, Sensitivity.S4, "private:")));
        return new ModuleInstance(definition, bindings);
    }

    private static OperationDefinition<String, String> operation(String name,
            Sensitivity sensitivity, MaterialType<String> output, boolean acceptOwnerInput) {
        Map<MaterialTypeId, Privacy> accepted = acceptOwnerInput
                ? Map.of(REQUEST.id(), Privacy.MODULE, CALLER_REQUEST, Privacy.MODULE)
                : Map.of(CALLER_REQUEST, Privacy.MODULE);
        return new OperationDefinition<>(new OperationId(ID, name), name,
                OperationVisibility.PUBLIC, accepted, Map.of(output.id(), sensitivity), Map.of());
    }

    private static Operation<String, String> behavior(MaterialType<String> output,
            Sensitivity sensitivity, String prefix) {
        return new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                return CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(ID, call.operation().id().name() + "-" + UUID.randomUUID()),
                        output, prefix + call.input().payload(), sensitivity));
            }
        };
    }

    private static Material<String> publicResult(Material<String> internal) {
        return new Material<>(new MaterialId(ID, "public-" + UUID.randomUUID()),
                internal.type(), "public:callee-summary", Sensitivity.S1);
    }
}
