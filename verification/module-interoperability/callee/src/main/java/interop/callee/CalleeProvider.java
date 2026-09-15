package interop.callee;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Independently compiled callee used by the installed Module interoperability acceptance. */
public final class CalleeProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("interop.callee");
    private static final ModuleId CALLER = new ModuleId("interop.caller");
    private static final MaterialTypeId CALLER_REQUEST = new MaterialTypeId(CALLER, "request");
    private static final MaterialType<String> REQUEST = textType("request");
    private static final MaterialType<String> RESULT = textType("sensitive-result");
    private static final MaterialType<String> OTHER_RESULT = textType("other-result");

    @Override public ModuleId moduleId() { return ID; }

    @Override public Module create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(configuration, "configuration");
        if (!configuration.moduleId().equals(ID) || !configuration.keys().isEmpty()) {
            throw new IllegalArgumentException("unexpected callee configuration");
        }
        return new CalleeModule();
    }

    private static final class CalleeModule implements Module {
        private final OperationBinding<String, String> sensitive;
        private final OperationBinding<String, String> tooSensitive;
        private final OperationBinding<String, String> undeclared;
        private final OperationBinding<String, String> privateSensitive;

        private CalleeModule() {
            OperationDefinition<String, String> sensitiveContract = operation(
                    "sensitive", Sensitivity.S4, RESULT, true);
            OperationDefinition<String, String> tooSensitiveContract = operation(
                    "too-sensitive", Sensitivity.S5, RESULT, false);
            OperationDefinition<String, String> undeclaredContract = operation(
                    "undeclared-result", Sensitivity.S2, OTHER_RESULT, false);
            OperationDefinition<String, String> privateContract = new OperationDefinition<>(
                    new OperationId(ID, "private-sensitive"), "Private sensitive behavior",
                    OperationVisibility.PRIVATE,
                    Map.of(REQUEST.id(), Privacy.MODULE, CALLER_REQUEST, Privacy.MODULE),
                    Map.of(RESULT.id(), Sensitivity.S4), Map.of());

            sensitive = OperationBinding.publicOperation(sensitiveContract,
                    behavior(RESULT, Sensitivity.S4, "classified:"), CalleeProvider::publicResult);
            tooSensitive = OperationBinding.publicOperation(tooSensitiveContract,
                    behavior(RESULT, Sensitivity.S5, "too-sensitive:"), CalleeProvider::publicResult);
            undeclared = OperationBinding.publicOperation(undeclaredContract,
                    behavior(OTHER_RESULT, Sensitivity.S2, "undeclared:"), CalleeProvider::publicResult);
            privateSensitive = OperationBinding.privateOperation(privateContract,
                    behavior(RESULT, Sensitivity.S4, "private:"));
        }

        @Override public ModuleId id() { return ID; }
        @Override public String version() { return "1.0.0"; }
        @Override public String purpose() { return "Independent interoperability callee"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() {
            return List.of(REQUEST, RESULT, OTHER_RESULT);
        }
        @Override public Set<MaterialTypeId> publicMaterialReferences() {
            return Set.of(CALLER_REQUEST);
        }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() {
            return List.of(sensitive, tooSensitive, undeclared, privateSensitive);
        }
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
        return Operation.of(call -> java.util.concurrent.CompletableFuture.completedFuture(
                new Material<>(new MaterialId(ID,
                        call.operation().id().name() + "-" + UUID.randomUUID()),
                        output, prefix + call.input().payload(), sensitivity)));
    }

    private static Material<String> publicResult(Material<String> internal) {
        return new Material<>(new MaterialId(ID, "public-" + UUID.randomUUID()),
                internal.type(), "public:callee-summary", Sensitivity.S1);
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(ID, name), String.class,
                "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    }
}
