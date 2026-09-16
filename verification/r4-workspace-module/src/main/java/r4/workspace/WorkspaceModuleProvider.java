package r4.workspace;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
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
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Independently built, agentless R4 proving application Module. */
public final class WorkspaceModuleProvider implements ModuleProvider {
    private static final ModuleId ID = new ModuleId("verification.workspace");
    private static final MaterialType<String> COMMAND = textType("workspace-command");
    private static final MaterialType<String> RESULT = textType("workspace-result");
    private static final MaterialType<String> PRIVATE_AUDIT = textType("private-audit");

    private static final OperationId RECORD = new OperationId(ID, "record-note");
    private static final OperationId SUMMARIZE = new OperationId(ID, "summarize-notes");
    private static final OperationId AUDIT = new OperationId(ID, "private-audit");
    private static final OperationId RESET = new OperationId(ID, "reset-notes");

    private static final EffectProfile RECORD_EFFECT = new EffectProfile(
            new EffectProfileId(RECORD, "store-note"), Risk.WRITE, Autonomy.ASK_ALWAYS);
    private static final EffectProfile RESET_EFFECT = new EffectProfile(
            new EffectProfileId(RESET, "delete-notes"), Risk.DELETE, Autonomy.ASK_ALWAYS);

    private static final OperationDefinition RECORD_OPERATION = new OperationDefinition(RECORD,
            "Store one owner-provided project or workspace note in Module-owned local state",
            Map.of(COMMAND.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S2),
            Map.of(RECORD_EFFECT.id(), RECORD_EFFECT));
    private static final OperationDefinition SUMMARIZE_OPERATION = new OperationDefinition(SUMMARIZE,
            "Read project or workspace notes stored by this Module and return a concise state summary",
            Map.of(COMMAND.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S2), Map.of());
    private static final OperationDefinition AUDIT_OPERATION = new OperationDefinition(AUDIT,
            "Read a highly sensitive workspace audit value owned by this Module",
            Map.of(COMMAND.id(), Privacy.SECRET),
            Map.of(PRIVATE_AUDIT.id(), Sensitivity.S5), Map.of());
    private static final OperationDefinition RESET_OPERATION = new OperationDefinition(RESET,
            "Delete every workspace note stored by this Module",
            Map.of(COMMAND.id(), Privacy.SECRET), Map.of(RESULT.id(), Sensitivity.S2),
            Map.of(RESET_EFFECT.id(), RESET_EFFECT));

    @Override public ModuleId moduleId() { return ID; }

    @Override public Module create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        ModuleProviderConfiguration candidate = java.util.Objects.requireNonNull(
                configuration, "configuration");
        if (!candidate.moduleId().equals(ID) || !candidate.keys().isEmpty()) {
            throw new IllegalArgumentException("unexpected workspace Module configuration");
        }
        return new WorkspaceModule(context.stateDirectory().resolve("verification-workspace.state"),
                artifactBehavior());
    }

    private static final class WorkspaceModule implements Module {
        private final Path stateFile;
        private final String behavior;
        private final OperationBinding<String, String> record;
        private final OperationBinding<String, String> summarize;
        private final OperationBinding<String, String> audit;
        private final OperationBinding<String, String> reset;

        private WorkspaceModule(Path stateFile, String behavior) {
            this.stateFile = stateFile.toAbsolutePath();
            this.behavior = behavior;
            record = OperationBinding.operation(RECORD_OPERATION, Operation.of(this::record));
            summarize = OperationBinding.operation(SUMMARIZE_OPERATION, Operation.of(this::summarize));
            audit = OperationBinding.operation(AUDIT_OPERATION, Operation.of(this::audit));
            reset = OperationBinding.operation(RESET_OPERATION, Operation.of(this::reset));
        }

        @Override public ModuleId id() { return ID; }
        @Override public String version() { return "1.0.0-" + behavior; }
        @Override public String purpose() {
            return "Owner-local project and workspace note application used only as R4 verification evidence";
        }
        @Override public Collection<? extends MaterialType<?>> materialTypes() {
            return List.of(COMMAND, RESULT, PRIVATE_AUDIT);
        }
        @Override public Set<OperationId> exposedOperations() {
            return Set.of(RECORD, SUMMARIZE, AUDIT);
        }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() {
            return List.of(record, summarize, audit, reset);
        }

        private synchronized CompletionStage<Material<String>> record(
                OperationCall<String, String> call) {
            if (!call.effectProfile().orElseThrow().equals(RECORD_EFFECT)) {
                throw new IllegalArgumentException("record-note requires its store-note effect");
            }
            if (!call.nonUserCausalParticipants().equals(List.of(Integrity.I2))) {
                throw new IllegalStateException(
                        "record-note requires the actual CORE Agent I2 causal participant");
            }
            List<String> notes = new ArrayList<>(readNotes());
            notes.add(call.input().payload().strip());
            writeNotes(notes);
            return CompletableFuture.completedFuture(result(
                    "behavior=" + behavior + "; action=recorded; count=" + notes.size()
                            + "; latest=" + notes.getLast() + "; causal=I2",
                    RESULT, Sensitivity.S2));
        }

        private synchronized CompletionStage<Material<String>> summarize(
                OperationCall<String, String> call) {
            List<String> notes = readNotes();
            String latest = notes.isEmpty() ? "none" : notes.getLast();
            return CompletableFuture.completedFuture(result(
                    "behavior=" + behavior + "; action=summary; count=" + notes.size()
                            + "; latest=" + latest,
                    RESULT, Sensitivity.S2));
        }

        private CompletionStage<Material<String>> audit(OperationCall<String, String> call) {
            return CompletableFuture.completedFuture(result(
                    "behavior=" + behavior + "; private-audit=workspace-owner-only",
                    PRIVATE_AUDIT, Sensitivity.S5));
        }

        private synchronized CompletionStage<Material<String>> reset(
                OperationCall<String, String> call) {
            if (!call.effectProfile().orElseThrow().equals(RESET_EFFECT)) {
                throw new IllegalArgumentException("reset-notes requires its delete-notes effect");
            }
            writeNotes(List.of());
            return CompletableFuture.completedFuture(result(
                    "behavior=" + behavior + "; action=reset", RESULT, Sensitivity.S2));
        }

        private List<String> readNotes() {
            if (!Files.exists(stateFile)) return List.of();
            try {
                return Files.readAllLines(stateFile, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isBlank())
                        .map(line -> new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8))
                        .toList();
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("cannot read workspace Module state", exception);
            }
        }

        private void writeNotes(List<String> notes) {
            try {
                Path parent = stateFile.getParent();
                if (parent != null) Files.createDirectories(parent);
                Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
                List<String> encoded = notes.stream().map(value -> Base64.getEncoder()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8))).toList();
                Files.write(temporary, encoded, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("cannot write workspace Module state", exception);
            }
        }
    }

    private static Material<String> result(String payload, MaterialType<String> type,
            Sensitivity sensitivity) {
        return new Material<>(new MaterialId(ID, UUID.randomUUID().toString()), type,
                payload, sensitivity);
    }

    private static String artifactBehavior() {
        try (var input = WorkspaceModuleProvider.class.getResourceAsStream(
                "/workspace-build.properties")) {
            if (input == null) throw new IllegalStateException("workspace build metadata is missing");
            Properties properties = new Properties();
            properties.load(input);
            String behavior = properties.getProperty("artifact-behavior");
            if (behavior == null || !behavior.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalStateException("workspace artifact behavior is missing or invalid");
            }
            return behavior;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read workspace build metadata", exception);
        }
    }

    private static MaterialType<String> textType(String name) {
        return new MaterialType<>(new MaterialTypeId(ID, name), String.class,
                "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    }
}
