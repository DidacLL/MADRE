package consumer;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.text.TextInferenceCommand;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Independent executable application Module compiled only against packaged MADRE artifacts. */
public final class IndependentDefinition implements Module {
    static final ModuleId ID = new ModuleId("phd.module");
    static final MaterialType<String> REQUEST = textType("request");
    static final MaterialType<String> RESULT = textType("result");
    static final MaterialType<String> WORKSPACE_COMMAND = textType("workspace-command");
    static final MaterialType<String> WORKSPACE_RESULT = textType("workspace-result");
    static final MaterialType<String> PRIVATE_RESULT = textType("workspace-private-result");

    static final OperationId INSPECT = new OperationId(ID, "inspect");
    static final OperationId REASON = new OperationId(ID, "reason");
    static final OperationId SAVE_NOTE = new OperationId(ID, "save-workspace-note");
    static final OperationId COUNT_NOTES = new OperationId(ID, "count-workspace-notes");
    static final OperationId RESET_NOTES = new OperationId(ID, "reset-workspace-notes");
    static final OperationId PRIVATE_NOTES = new OperationId(ID, "private-workspace-notes");

    private static final OperationDefinition INSPECT_OPERATION = operation(
            INSPECT, "Inspect independent Module input without reasoning");
    static final OperationDefinition REASON_OPERATION = operation(
            REASON, "Interpret independently installed reasoning output");

    static final EffectProfile SAVE_PROFILE = new EffectProfile(
            new EffectProfileId(SAVE_NOTE, "store-workspace-note"), Risk.WRITE,
            Autonomy.LIVE_INTERACTION);
    static final EffectProfile RESET_PROFILE = new EffectProfile(
            new EffectProfileId(RESET_NOTES, "clear-workspace-notes"), Risk.DELETE,
            Autonomy.LIVE_INTERACTION);

    static final OperationDefinition SAVE_OPERATION = new OperationDefinition(SAVE_NOTE,
            "Save this workspace note in durable application state",
            Map.of(WORKSPACE_COMMAND.id(), Privacy.SECRET),
            Map.of(WORKSPACE_RESULT.id(), Sensitivity.S2),
            Map.of(SAVE_PROFILE.id(), SAVE_PROFILE));
    static final OperationDefinition COUNT_OPERATION = new OperationDefinition(COUNT_NOTES,
            "Report how many workspace notes are saved in durable application state",
            Map.of(WORKSPACE_COMMAND.id(), Privacy.SECRET),
            Map.of(WORKSPACE_RESULT.id(), Sensitivity.S2), Map.of());
    static final OperationDefinition RESET_OPERATION = new OperationDefinition(RESET_NOTES,
            "Delete every workspace note from durable application state",
            Map.of(WORKSPACE_COMMAND.id(), Privacy.SECRET),
            Map.of(WORKSPACE_RESULT.id(), Sensitivity.S2),
            Map.of(RESET_PROFILE.id(), RESET_PROFILE));
    static final OperationDefinition PRIVATE_OPERATION = new OperationDefinition(PRIVATE_NOTES,
            "Show private workspace details reserved for this application",
            Map.of(WORKSPACE_COMMAND.id(), Privacy.SECRET),
            Map.of(PRIVATE_RESULT.id(), Sensitivity.S5), Map.of());

    private final OperationBinding<String, String> inspect;
    private final OperationBinding<String, String> reason;
    private final OperationBinding<String, String> saveNote;
    private final OperationBinding<String, String> countNotes;
    private final OperationBinding<String, String> resetNotes;
    private final OperationBinding<String, String> privateNotes;
    private final String artifactBehavior;

    private IndependentDefinition(ReasoningService reasoning, WorkspaceStateStore workspace,
            String resultPrefix, String artifactBehavior) {
        java.util.Objects.requireNonNull(reasoning, "reasoning");
        WorkspaceStateStore state = java.util.Objects.requireNonNull(workspace, "workspace");
        String prefix = java.util.Objects.requireNonNull(resultPrefix, "resultPrefix");
        String behavior = java.util.Objects.requireNonNull(artifactBehavior, "artifactBehavior");
        this.artifactBehavior = behavior;

        Operation<String, String> inspectBehavior = Operation.of(call ->
                CompletableFuture.completedFuture(material(RESULT,
                        "private:" + behavior + ":" + prefix + call.input().payload(),
                        Sensitivity.S4)));
        Operation<String, String> reasonBehavior = Operation.of(call -> {
            ReasoningRequest<io.github.didacll.madre.text.TextInferenceResult,
                    TextInferenceCommand> request = ReasoningRequest.immediate(call,
                            new TextInferenceCommand(call.input().payload(), 32, List.of()),
                            0, Duration.ofSeconds(5), ReasoningRetryPolicy.none(),
                            Optional.empty(), ReasoningPreferences.unconstrained());
            return reasoning.execute(request).thenApply(result -> material(RESULT,
                    "private:reasoned:" + prefix + result.text(), Sensitivity.S4));
        });
        Operation<String, String> saveBehavior = Operation.of(call -> {
            int count = state.add(call.input().payload());
            return CompletableFuture.completedFuture(material(WORKSPACE_RESULT,
                    "Saved " + count + " workspace " + (count == 1 ? "note." : "notes."),
                    Sensitivity.S2));
        });
        Operation<String, String> countBehavior = Operation.of(call -> {
            int count = state.snapshot().size();
            return CompletableFuture.completedFuture(material(WORKSPACE_RESULT,
                    count == 1 ? "There is 1 saved workspace note."
                            : "There are " + count + " saved workspace notes.",
                    Sensitivity.S2));
        });
        Operation<String, String> resetBehavior = Operation.of(call -> {
            int count = state.clear();
            return CompletableFuture.completedFuture(material(WORKSPACE_RESULT,
                    "Removed " + count + " workspace " + (count == 1 ? "note." : "notes."),
                    Sensitivity.S2));
        });
        Operation<String, String> privateBehavior = Operation.of(call ->
                CompletableFuture.completedFuture(material(PRIVATE_RESULT,
                        "Private workspace note count: " + state.snapshot().size(),
                        Sensitivity.S5)));

        inspect = OperationBinding.publicDisclosure(INSPECT_OPERATION, inspectBehavior,
                IndependentDefinition::publicResult);
        reason = OperationBinding.publicDisclosure(REASON_OPERATION, reasonBehavior,
                IndependentDefinition::publicResult);
        saveNote = OperationBinding.operation(SAVE_OPERATION, saveBehavior);
        countNotes = OperationBinding.operation(COUNT_OPERATION, countBehavior);
        resetNotes = OperationBinding.operation(RESET_OPERATION, resetBehavior);
        privateNotes = OperationBinding.operation(PRIVATE_OPERATION, privateBehavior);
    }

    static Module create(ReasoningService reasoning, String resultPrefix) {
        return create(reasoning, resultPrefix, "baseline");
    }

    static Module create(ReasoningService reasoning, String resultPrefix, String artifactBehavior) {
        Path state = Path.of(System.getProperty("java.io.tmpdir"),
                "madre-independent-module-" + UUID.randomUUID());
        return create(reasoning, state, resultPrefix, artifactBehavior);
    }

    static Module create(ReasoningService reasoning, Path stateDirectory, String resultPrefix,
            String artifactBehavior) {
        return new IndependentDefinition(reasoning, new WorkspaceStateStore(stateDirectory),
                resultPrefix, artifactBehavior);
    }

    @Override public ModuleId id() { return ID; }
    @Override public String version() { return "1.0.0-" + artifactBehavior; }
    @Override public String purpose() {
        return "Independent durable workspace application and SDK acceptance fixture";
    }
    @Override public Collection<? extends MaterialType<?>> materialTypes() {
        return List.of(REQUEST, RESULT, WORKSPACE_COMMAND, WORKSPACE_RESULT, PRIVATE_RESULT);
    }
    @Override public Collection<? extends OperationBinding<?, ?>> operations() {
        return List.of(inspect, reason, saveNote, countNotes, resetNotes, privateNotes);
    }
    @Override public Set<OperationId> exposedOperations() {
        return Set.of(SAVE_NOTE, COUNT_NOTES, PRIVATE_NOTES);
    }

    private static OperationDefinition operation(OperationId id, String description) {
        return new OperationDefinition(id, description,
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
                "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    }
}
