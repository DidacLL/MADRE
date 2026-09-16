package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.directory.ReachableOperation;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class TextOperationCompositionTest {
    private static final ModuleId CALLER = OwnerInteractionModule.ID;
    private static final ModuleId TARGET = new ModuleId("independent.application");
    private static final MaterialTypeDefinition COMMAND = new MaterialTypeDefinition(
            new MaterialTypeId(TARGET, "command"), "text/plain; charset=utf-8");
    private static final MaterialTypeDefinition RESULT = new MaterialTypeDefinition(
            new MaterialTypeId(TARGET, "result"), "text/plain; charset=utf-8");

    @Test void selectsOneHonestTextContractAndPreservesForeignResult() {
        OperationId id = new OperationId(TARGET, "save-note");
        EffectProfile profile = new EffectProfile(new EffectProfileId(id, "write"),
                Risk.WRITE, Autonomy.LIVE_INTERACTION);
        OperationDefinition operation = new OperationDefinition(id,
                "Save this workspace note in durable application state",
                Map.of(COMMAND.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S2),
                Map.of(profile.id(), profile));
        ReachableOperation reachable = reachable(operation);
        TextOperationDiscovery discovery = new TextOperationDiscovery(directory(List.of(reachable)));
        var candidate = discovery.select("Save this workspace note: research evidence",
                Sensitivity.S5).orElseThrow();

        MaterialType<String> resultType = new MaterialType<>(RESULT, String.class,
                MaterialCodecs.utf8String());
        Material<String> foreign = new Material<>(new MaterialId(TARGET, "foreign-result"),
                resultType, "Saved 1 workspace note.", Sensitivity.S2);
        AtomicReference<OperationCall<?, ?>> captured = new AtomicReference<>();
        ModuleInvoker invoker = new ModuleInvoker() {
            @Override public <I, O> CompletionStage<Material<O>> invoke(
                    OperationCall<I, O> call) {
                captured.set(call);
                @SuppressWarnings("unchecked")
                Material<O> output = (Material<O>) foreign;
                return CompletableFuture.completedFuture(output);
            }
        };

        Material<String> returned = new TextOperationInvocation(CALLER, invoker)
                .invoke(candidate, "Save this workspace note: research evidence", Sensitivity.S5,
                        Integrity.I2)
                .toCompletableFuture().join();

        assertSame(foreign, returned);
        assertEquals(CALLER, captured.get().input().id().moduleId());
        assertEquals(COMMAND.id(), captured.get().input().type().id());
        assertEquals(Sensitivity.S5, captured.get().input().sensitivity());
        assertEquals(Optional.of(profile), captured.get().effectProfile());
    }

    @Test void declinesAmbiguousTextContractsInsteadOfGuessing() {
        OperationDefinition first = effectFree("save-a",
                "Save this workspace note in durable application state");
        OperationDefinition second = effectFree("save-b",
                "Save this workspace note in durable application state");
        TextOperationDiscovery discovery = new TextOperationDiscovery(directory(List.of(
                reachable(first), reachable(second))));

        assertTrue(discovery.select("Save this workspace note: research evidence", Sensitivity.S5)
                .isEmpty());
    }

    private static OperationDefinition effectFree(String name, String description) {
        return new OperationDefinition(new OperationId(TARGET, name), description,
                Map.of(COMMAND.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S2), Map.of());
    }

    private static ReachableOperation reachable(OperationDefinition operation) {
        return new ReachableOperation(TARGET, "Independent application", operation,
                Map.of(COMMAND.id(), COMMAND, RESULT.id(), RESULT));
    }

    private static ModuleDirectory directory(List<ReachableOperation> operations) {
        return new ModuleDirectory() {
            @Override public List<ReachableModule> reachable(ReachabilityQuery query) {
                return List.of();
            }

            @Override public List<ReachableOperation> reachableOperations(Sensitivity sensitivity) {
                return operations;
            }
        };
    }
}
