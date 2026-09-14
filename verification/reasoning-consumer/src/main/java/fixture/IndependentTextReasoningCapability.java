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
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import io.github.didacll.madre.text.TextInferenceCodecs;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.time.Duration;
import java.util.List;

/** Deterministic independently compiled text-inference mechanism for installation proof. */
final class IndependentTextReasoningCapability
        implements ReasoningCapability<TextInferenceResult, TextInferenceCommand> {
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

    IndependentTextReasoningCapability(ReasoningCapabilityId id, Privacy privacy,
            ReasoningLocation location, Duration expectedLatency,
            ReasoningAvailability availability) {
        this.manifest = new ReasoningCapabilityManifest<>(id, CONTRACT, privacy, location,
                expectedLatency, List.of());
        this.availability = availability;
    }

    @Override public ReasoningCapabilityManifest<TextInferenceResult, TextInferenceCommand>
            manifest() {
        return manifest;
    }

    @Override public ReasoningAvailability availability() { return availability; }

    @Override public TextInferenceResult execute(TextInferenceCommand computation,
            ReasoningExecutionContext context) throws ReasoningException {
        context.requireActive();
        return new TextInferenceResult("independent:" + computation.prompt(),
                TextInferenceResult.CompletionReason.STOP, -1, -1);
    }
}
