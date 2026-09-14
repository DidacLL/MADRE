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

/** Independently compiled caller used by the installed Module interoperability acceptance. */
public final class CallerProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("interop.caller");
    private static final ModuleId CALLEE = new ModuleId("interop.callee");
    private static final MaterialTypeId CALLEE_RESULT =
            new MaterialTypeId(CALLEE, "sensitive-result");
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
    private static final MaterialType<String> ADAPTED = new MaterialType<>(
            new MaterialTypeId(ID, "adapted-result"), String.class, "text/plain", STRINGS);

    @Override public ModuleId moduleId() { return ID; }

    @Override public ModuleInstance create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(configuration, "configuration");
        if (!configuration.moduleId().equals(ID) || !configuration.keys().isEmpty()) {
            throw new IllegalArgumentException("unexpected caller configuration");
        }

        OperationDefinition<String, String> compose = operation("compose", Sensitivity.S4);
        OperationDefinition<String, String> probeTooSensitive =
                operation("probe-too-sensitive", Sensitivity.S2);
        OperationDefinition<String, String> probeUndeclared =
                operation("probe-undeclared", Sensitivity.S2);
        OperationDefinition<String, String> probePrivate =
                operation("probe-private", Sensitivity.S1);
        Map<OperationId, OperationDefinition<?, ?>> definitions = Map.of(
                compose.id(), compose,
                probeTooSensitive.id(), probeTooSensitive,
                probeUndeclared.id(), probeUndeclared,
                probePrivate.id(), probePrivate);
        ModuleDefinition definition = new ModuleDefinition(ID, "1.0.0",
                "Independent interoperability caller",
                Map.of(REQUEST.id(), REQUEST, ADAPTED.id(), ADAPTED),
                Set.of(CALLEE_RESULT), Map.of(), Map.of(), definitions);
        Map<OperationId, OperationBinding<?, ?>> bindings = Map.of(
                compose.id(), OperationBinding.publicOperation(compose, compose(context),
                        CallerProvider::publicResult),
                probeTooSensitive.id(), OperationBinding.publicOperation(probeTooSensitive,
                        blockedProbe(context, "too-sensitive", "blocked:too-sensitive"),
                        CallerProvider::publicResult),
                probeUndeclared.id(), OperationBinding.publicOperation(probeUndeclared,
                        blockedProbe(context, "undeclared-result", "blocked:undeclared-type"),
                        CallerProvider::publicResult),
                probePrivate.id(), OperationBinding.publicOperation(probePrivate,
                        privateProbe(context), CallerProvider::publicResult));
        return new ModuleInstance(definition, bindings);
    }

    private static OperationDefinition<String, String> operation(String name,
            Sensitivity maximum) {
        return new OperationDefinition<>(new OperationId(ID, name), name,
                OperationVisibility.PUBLIC, Map.of(REQUEST.id(), Privacy.MODULE),
                Map.of(ADAPTED.id(), maximum), Map.of());
    }

    private static Operation<String, String> compose(ModuleContext context) {
        return new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                OperationDefinition<String, String> remote = find(context, call.input(), "sensitive");
                OperationCall<String, String> remoteCall =
                        OperationCall.withoutEffect(remote, call.input());
                return context.invoker().invoke(remoteCall).thenApply(received -> {
                    MaterialId adaptedId = new MaterialId(ID, "adapted-" + UUID.randomUUID());
                    String interpreted = received.payload().replaceFirst(
                            "^classified:", "interpreted:");
                    String payload = "receivedOwner=" + received.id().moduleId().value()
                            + ";receivedId=" + received.id().value()
                            + ";receivedSensitivity=" + received.sensitivity().name()
                            + ";adaptedOwner=" + ID.value()
                            + ";adaptedId=" + adaptedId.value()
                            + ";interpreted=" + interpreted;
                    return new Material<>(adaptedId, ADAPTED, payload, Sensitivity.S4);
                });
            }
        };
    }

    private static Operation<String, String> blockedProbe(ModuleContext context,
            String operationName, String expected) {
        return new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                OperationDefinition<String, String> remote =
                        find(context, call.input(), operationName);
                OperationCall<String, String> remoteCall =
                        OperationCall.withoutEffect(remote, call.input());
                return context.invoker().invoke(remoteCall).handle((received, failure) -> {
                    if (failure == null) {
                        return own("unsafe-exposure:" + received.payload(), Sensitivity.S2);
                    }
                    return own(expected, Sensitivity.S2);
                });
            }
        };
    }

    private static Operation<String, String> privateProbe(ModuleContext context) {
        return new Operation<>() {
            @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                    OperationCall<String, String> call) {
                OperationId privateId = new OperationId(CALLEE, "private-sensitive");
                boolean reachable = context.directory().reachable(
                                new ReachabilityQuery(call.input().type().id(),
                                        call.input().sensitivity())).stream()
                        .filter(module -> module.id().equals(CALLEE))
                        .anyMatch(module -> module.operations().containsKey(privateId));
                return CompletableFuture.completedFuture(
                        own("privateReachable=" + reachable, Sensitivity.S1));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static OperationDefinition<String, String> find(ModuleContext context,
            Material<String> input, String operationName) {
        ReachableModule module = context.directory().reachable(
                        new ReachabilityQuery(input.type().id(), input.sensitivity())).stream()
                .filter(candidate -> candidate.id().equals(CALLEE))
                .findFirst().orElseThrow(() -> new IllegalStateException("callee is not reachable"));
        OperationDefinition<?, ?> operation = module.operations().get(
                new OperationId(CALLEE, operationName));
        if (operation == null) {
            throw new IllegalStateException("callee Operation is not reachable: " + operationName);
        }
        return (OperationDefinition<String, String>) operation;
    }

    private static Material<String> own(String payload, Sensitivity sensitivity) {
        return new Material<>(new MaterialId(ID, "result-" + UUID.randomUUID()),
                ADAPTED, payload, sensitivity);
    }

    private static Material<String> publicResult(Material<String> internal) {
        return new Material<>(new MaterialId(ID, "public-" + UUID.randomUUID()),
                ADAPTED, "public:caller-summary", Sensitivity.S1);
    }
}
