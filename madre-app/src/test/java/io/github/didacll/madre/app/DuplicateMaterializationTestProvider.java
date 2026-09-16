package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityManifest;
import io.github.didacll.madre.kernel.reasoning.ReasoningCodec;
import io.github.didacll.madre.kernel.reasoning.ReasoningContract;
import io.github.didacll.madre.kernel.reasoning.ReasoningExecutionContext;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Startup fixture that produces two mechanisms with the same installation identity. */
public final class DuplicateMaterializationTestProvider implements ReasoningMechanismProvider {
    static final AtomicBoolean CLOSED = new AtomicBoolean();
    private static final ReasoningCodec<FixtureComputation> INPUT = new ReasoningCodec<>() {
        @Override public byte[] encode(FixtureComputation value) {
            return value.value().getBytes(StandardCharsets.UTF_8);
        }
        @Override public FixtureComputation decode(byte[] bytes) {
            return new FixtureComputation(new String(bytes, StandardCharsets.UTF_8));
        }
    };
    private static final ReasoningCodec<String> OUTPUT = new ReasoningCodec<>() {
        @Override public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        @Override public String decode(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };
    private static final ReasoningContract<String, FixtureComputation> CONTRACT =
            new ReasoningContract<>("duplicate-startup.v1", FixtureComputation.class,
                    String.class, INPUT, OUTPUT);

    public DuplicateMaterializationTestProvider() {
        CLOSED.set(false);
    }

    @Override public ReasoningProviderDescriptor descriptor() {
        return TestReasoningProviderSupport.descriptor("test-duplicate-materialization");
    }

    @Override public ReasoningProviderConfigurator configurator() {
        return TestReasoningProviderSupport.EMPTY_CONFIGURATOR;
    }

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        return List.of(new ReasoningMechanism<>(capability(), 0),
                new ReasoningMechanism<>(capability(), 0));
    }

    @Override public void close() { CLOSED.set(true); }

    private static ReasoningCapability<String, FixtureComputation> capability() {
        ReasoningCapabilityManifest<String, FixtureComputation> manifest =
                new ReasoningCapabilityManifest<>(new ReasoningCapabilityId("duplicate-startup"),
                        CONTRACT, Privacy.SECRET, ReasoningLocation.LOCAL,
                        Duration.ofMillis(1), List.of());
        return new ReasoningCapability<>() {
            @Override public ReasoningCapabilityManifest<String, FixtureComputation> manifest() {
                return manifest;
            }
            @Override public ReasoningAvailability availability() {
                return ReasoningAvailability.AVAILABLE;
            }
            @Override public String execute(FixtureComputation computation,
                    ReasoningExecutionContext context) {
                return computation.value();
            }
        };
    }

    public record FixtureComputation(String value) implements ReasoningComputation<String> {
        @Override public Class<String> resultType() { return String.class; }
    }
}
