package fixture;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningException;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.text.TextInferenceCodecs;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/** Deterministic independently compiled text-inference mechanism for installation proof. */
final class IndependentTextReasoningCapability
        implements ReasoningCapability<TextInferenceResult, TextInferenceCommand> {
    private static final String BACKGROUND_PREFIX =
            "Analyze the owner's request below after the immediate response has already been shown.";
    private static final String BARRIER_PROMPT = "await-background-completion";
    private static final ReasoningContract<TextInferenceResult, TextInferenceCommand> CONTRACT =
            new ReasoningContract<>(TextInferenceCodecs.CONTRACT_ID, TextInferenceCommand.class,
                    TextInferenceResult.class, new ReasoningCodec<>() {
                        @Override public byte[] encode(TextInferenceCommand value) {
                            return TextInferenceCodecs.encodeCommand(value);
                        }
                        @Override public TextInferenceCommand decode(byte[] bytes) {
                            return TextInferenceCodecs.decodeCommand(bytes);
                        }
                    }, new ReasoningCodec<>() {
                        @Override public byte[] encode(TextInferenceResult value) {
                            return TextInferenceCodecs.encodeResult(value);
                        }
                        @Override public TextInferenceResult decode(byte[] bytes) {
                            return TextInferenceCodecs.decodeResult(bytes);
                        }
                    });

    private final ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand> manifest;
    private final ReasoningAvailability availability;
    private final Optional<Path> backgroundGate;
    private final Optional<Path> backgroundCompletion;

    IndependentTextReasoningCapability(ReasoningCapabilityId id, Privacy privacy,
            ReasoningLocation location, Duration expectedLatency,
            ReasoningAvailability availability, Optional<Path> backgroundGate,
            Optional<Path> backgroundCompletion) {
        this.manifest = new ReasoningCapabilityManifest<>(id, CONTRACT, privacy, location,
                expectedLatency, List.of());
        this.availability = availability;
        this.backgroundGate = backgroundGate.map(Path::toAbsolutePath);
        this.backgroundCompletion = backgroundCompletion.map(Path::toAbsolutePath);
    }

    @Override public ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand>
            manifest() {
        return manifest;
    }

    @Override public ReasoningAvailability availability() { return availability; }

    @Override public TextInferenceResult execute(TextInferenceCommand computation,
            ReasoningExecutionContext context) throws ReasoningException {
        context.requireActive();
        String prompt = computation.prompt();
        if (prompt.startsWith(BACKGROUND_PREFIX)) {
            if (backgroundGate.isPresent() && !Files.exists(backgroundGate.orElseThrow())) {
                throw new ReasoningException(ReasoningFailureCategory.CONNECTION,
                        "deterministic background gate is closed");
            }
            TextInferenceResult result = result("independent:background-useful:" + prompt);
            backgroundCompletion.ifPresent(this::writeCompletion);
            return result;
        }
        if (prompt.equals(BARRIER_PROMPT) && backgroundCompletion.isPresent()) {
            Path marker = backgroundCompletion.orElseThrow();
            while (!Files.exists(marker)) {
                context.requireActive();
                LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
            }
        }
        return result("independent:" + prompt);
    }

    private static TextInferenceResult result(String text) {
        return new TextInferenceResult(text, TextInferenceResult.CompletionReason.STOP, -1, -1);
    }

    private void writeCompletion(Path marker) {
        try {
            Path parent = marker.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(marker, "complete");
        } catch (IOException exception) {
            throw new IllegalStateException("cannot write deterministic completion marker", exception);
        }
    }
}
