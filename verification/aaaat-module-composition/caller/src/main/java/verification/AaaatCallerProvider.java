package verification;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
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
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Independently compiled Module proving normal composition with AAAAT by nominal SDK contracts. */
public final class AaaatCallerProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("verification.aaaat-caller");
    private static final ModuleId AAAAT = new ModuleId("io.github.didacll.madre.aaaat");
    private static final MaterialTypeId AAAAT_REQUEST_ID =
            new MaterialTypeId(AAAAT, "opportunity-research-request");
    private static final MaterialTypeId AAAAT_RESULT_ID =
            new MaterialTypeId(AAAAT, "opportunity-research-context");
    private static final OperationId AAAAT_READ_ID =
            new OperationId(AAAAT, "opportunity-research-context-read");

    @Override public ModuleId moduleId() { return ID; }

    @Override public ModuleConfigurationDescriptor configurationDescriptor() {
        return ModuleConfigurationDescriptor.none(ID);
    }

    @Override public ModuleProviderConfiguration validateConfiguration(
            ModuleProviderConfiguration configuration) {
        return ModuleProvider.super.validateConfiguration(configuration);
    }

    @Override public Module create(ModuleContext context, ModuleProviderConfiguration configuration) {
        validateConfiguration(configuration);
        return new CallerModule(context);
    }

    private static final class CallerModule implements Module {
        private static final MaterialType<String> INPUT = new MaterialType<>(
                new MaterialTypeId(ID, "probe-request"), String.class, "text/plain",
                MaterialCodecs.utf8String());
        private static final MaterialType<String> OUTPUT = new MaterialType<>(
                new MaterialTypeId(ID, "probe-result"), String.class, "application/json",
                MaterialCodecs.utf8String());
        private static final MaterialType<String> AAAAT_REQUEST = new MaterialType<>(
                AAAAT_REQUEST_ID, String.class, "application/json", MaterialCodecs.utf8String());
        private static final OperationId PROBE_ID = new OperationId(ID, "probe");

        private final ModuleContext context;
        private final OperationBinding<String, String> probe;

        CallerModule(ModuleContext context) {
            this.context = java.util.Objects.requireNonNull(context, "context");
            OperationDefinition definition = new OperationDefinition(PROBE_ID,
                    "Discover and invoke AAAAT opportunity research context",
                    OperationVisibility.PUBLIC,
                    Map.of(INPUT.id(), Privacy.MODULE),
                    Map.of(OUTPUT.id(), Sensitivity.S4), Map.of());
            Operation<String, String> implementation = Operation.of(call -> {
                var reachable = this.context.directory().reachable(
                        new ReachabilityQuery(AAAAT_REQUEST_ID, Sensitivity.S1));
                OperationDefinition target = reachable.stream()
                        .filter(module -> module.id().equals(AAAAT))
                        .map(module -> module.operations().get(AAAAT_READ_ID))
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "AAAAT exposed research Operation is not reachable"));
                Material<String> request = new Material<>(
                        new MaterialId(ID, "aaaat-request-" + UUID.randomUUID()),
                        AAAAT_REQUEST, "{}", Sensitivity.S1);
                return this.context.invoker()
                        .invoke(OperationCall.<String, String>withoutEffect(target, request))
                        .thenApply(result -> new Material<>(
                                new MaterialId(ID, "probe-result-" + UUID.randomUUID()),
                                OUTPUT, result.payload(), result.sensitivity()));
            });
            this.probe = OperationBinding.publicOperation(definition, implementation,
                    internal -> new Material<>(
                            new MaterialId(ID, "public-probe-" + UUID.randomUUID()),
                            OUTPUT, "{\"composed\":true}", Sensitivity.S1));
        }

        @Override public ModuleId id() { return ID; }
        @Override public String version() { return "1"; }
        @Override public String purpose() { return "Independent AAAAT composition verification"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() {
            return Set.of(INPUT, OUTPUT);
        }
        @Override public Set<MaterialTypeId> publicMaterialReferences() {
            return Set.of(AAAAT_REQUEST_ID, AAAAT_RESULT_ID);
        }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() {
            return Set.of(probe);
        }
    }
}
