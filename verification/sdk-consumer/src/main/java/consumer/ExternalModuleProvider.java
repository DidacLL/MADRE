package consumer;

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
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Source-independent proof that a Module needs only the consolidated SDK. */
public final class ExternalModuleProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("external-example");

    @Override public ModuleId moduleId() { return ID; }
    @Override public Module create(ModuleContext context) {
        java.util.Objects.requireNonNull(context, "context");
        return new ExternalModule();
    }

    private static final class ExternalModule implements Module {
        private final MaterialType<String> text = new MaterialType<>(
                new MaterialTypeId(ID, "text"), String.class, "text/plain",
                MaterialCodecs.utf8String());
        private final OperationDefinition definition = new OperationDefinition(
                new OperationId(ID, "echo"), "Echo text", Map.of(text.id(), Privacy.SECRET),
                Map.of(text.id(), Sensitivity.S5), Map.of());
        private final OperationBinding<String, String> echo = OperationBinding.operation(definition,
                Operation.of((context, call) -> CompletableFuture.completedFuture(new Material<>(
                        new MaterialId(ID, "echo-result"), text, call.input().payload(),
                        call.input().sensitivity()))));

        @Override public ModuleId id() { return ID; }
        @Override public String version() { return "1"; }
        @Override public String purpose() { return "External SDK build proof"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() { return Set.of(text); }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() { return Set.of(echo); }
        @Override public Set<OperationId> exposedOperations() { return Set.of(definition.id()); }
    }
}
