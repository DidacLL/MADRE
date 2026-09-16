package interop.caller;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableModule;
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
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Independently compiled caller used by the installed Module interoperability acceptance. */
public final class CallerProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("interop.caller");
    private static final ModuleId CALLEE = new ModuleId("interop.callee");
    private static final MaterialTypeId CALLEE_RESULT =
            new MaterialTypeId(CALLEE, "sensitive-result");
    private static final MaterialType<String> REQUEST = textType("request");
    private static final MaterialType<String> ADAPTED = textType("adapted-result");

    @Override public ModuleId moduleId() { return ID; }

    @Override public Module create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(configuration, "configuration");
        if (!configuration.moduleId().equals(ID) || !configuration.keys().isEmpty()) {
            throw new IllegalArgumentException("unexpected caller configuration");
        }
        return new CallerModule(context);
    }

    private static final class CallerModule implements Module {
        private final OperationBinding<String, String> compose;
        private final OperationBinding<String, String> probeTooSensitive;
        private final OperationBinding<String, String> probeUndeclared;
        private final OperationBinding<String, String> probeInternal;

        private CallerModule(ModuleContext context) {
            OperationDefinition composeContract = operation("compose", Sensitivity.S4);
            OperationDefinition tooSensitiveContract = operation("probe-too-sensitive", Sensitivity.S2);
            OperationDefinition undeclaredContract = operation("probe-undeclared", Sensitivity.S2);
            OperationDefinition internalContract = operation("probe-internal", Sensitivity.S1);
            compose = OperationBinding.publicDisclosure(composeContract, compose(context),
                    CallerProvider::publicResult);
            probeTooSensitive = OperationBinding.publicDisclosure(tooSensitiveContract,
                    blockedProbe(context, "too-sensitive", "blocked:too-sensitive"),
                    CallerProvider::publicResult);
            probeUndeclared = OperationBinding.publicDisclosure(undeclaredContract,
                    blockedProbe(context, "undeclared-result", "blocked:undeclared-type"),
                    CallerProvider::publicResult);
            probeInternal = OperationBinding.publicDisclosure(internalContract,
                    internalProbe(context), CallerProvider::publicResult);
        }

        @Override public ModuleId id() { return ID; }
        @Override public String version() { return "1.0.0"; }
        @Override public String purpose() { return "Independent interoperability caller"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() {
            return List.of(REQUEST, ADAPTED);
        }
        @Override public Set<MaterialTypeId> foreignMaterialReferences() {
            return Set.of(CALLEE_RESULT);
        }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() {
            return List.of(compose, probeTooSensitive, probeUndeclared, probeInternal);
        }
    }

    private static OperationDefinition operation(String name, Sensitivity maximum) {
        return new OperationDefinition(new OperationId(ID, name), name,
                Map.of(REQUEST.id(), Privacy.MODULE), Map.of(ADAPTED.id(), maximum), Map.of());
    }

    private static Operation<String, String> compose(ModuleContext context) {
        return Operation.of(call -> {
            OperationDefinition remote = find(context, call.input(), "sensitive");
            OperationCall<String, String> remoteCall = OperationCall.withoutEffect(remote, call.input());
            return context.invoker().invoke(remoteCall).thenApply(received -> {
                MaterialId adaptedId = new MaterialId(ID, "adapted-" + UUID.randomUUID());
                String interpreted = received.payload().replaceFirst("^classified:", "interpreted:");
                String payload = "receivedOwner=" + received.id().moduleId().value()
                        + ";receivedId=" + received.id().value()
                        + ";receivedSensitivity=" + received.sensitivity().name()
                        + ";adaptedOwner=" + ID.value()
                        + ";adaptedId=" + adaptedId.value()
                        + ";interpreted=" + interpreted;
                return new Material<>(adaptedId, ADAPTED, payload, Sensitivity.S4);
            });
        });
    }

    private static Operation<String, String> blockedProbe(ModuleContext context,
            String operationName, String expected) {
        return Operation.of(call -> {
            OperationDefinition remote = find(context, call.input(), operationName);
            OperationCall<String, String> remoteCall = OperationCall.withoutEffect(remote, call.input());
            return context.invoker().invoke(remoteCall).handle((received, failure) -> {
                if (failure == null) {
                    return own("unsafe-exposure:" + received.payload(), Sensitivity.S2);
                }
                return own(expected, Sensitivity.S2);
            });
        });
    }

    private static Operation<String, String> internalProbe(ModuleContext context) {
        return Operation.of(call -> {
            OperationId internalId = new OperationId(CALLEE, "internal-sensitive");
            boolean reachable = context.directory().reachable(
                            new ReachabilityQuery(call.input().type().id(), call.input().sensitivity())).stream()
                    .filter(module -> module.id().equals(CALLEE))
                    .anyMatch(module -> module.operations().containsKey(internalId));
            return CompletableFuture.completedFuture(
                    own("internalReachable=" + reachable, Sensitivity.S1));
        });
    }

    private static OperationDefinition find(ModuleContext context,
            Material<String> input, String operationName) {
        ReachableModule module = context.directory().reachable(
                        new ReachabilityQuery(input.type().id(), input.sensitivity())).stream()
                .filter(candidate -> candidate.id().equals(CALLEE))
                .findFirst().orElseThrow(() -> new IllegalStateException("callee is not reachable"));
        OperationDefinition operation = module.operations().get(new OperationId(CALLEE, operationName));
        if (operation == null) {
            throw new IllegalStateException("callee Operation is not reachable: " + operationName);
        }
        return operation;
    }

    private static Material<String> own(String payload, Sensitivity sensitivity) {
        return new Material<>(new MaterialId(ID, "result-" + UUID.randomUUID()),
                ADAPTED, payload, sensitivity);
    }

    private static Material<String> publicResult(Material<String> internal) {
        return new Material<>(new MaterialId(ID, "public-" + UUID.randomUUID()),
                ADAPTED, "public:caller-summary", Sensitivity.S1);
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(ID, name), String.class,
                "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    }
}
