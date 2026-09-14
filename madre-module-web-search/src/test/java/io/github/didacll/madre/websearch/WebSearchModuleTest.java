package io.github.didacll.madre.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.capability.Capability;
import io.github.didacll.madre.kernel.capability.CapabilityAvailability;
import io.github.didacll.madre.kernel.capability.CapabilityException;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.CapabilityManifest;
import io.github.didacll.madre.kernel.capability.ExecutionContext;
import io.github.didacll.madre.kernel.capability.PhysicalCodec;
import io.github.didacll.madre.kernel.capability.PhysicalContract;
import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.runtime.KernelRuntime;
import io.github.didacll.madre.sdk.execution.PhysicalExecutionException;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.text.TextInferenceCodecs;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import io.github.didacll.madre.web.WebSearchCodecs;
import io.github.didacll.madre.web.WebSearchCommand;
import io.github.didacll.madre.web.WebSearchHit;
import io.github.didacll.madre.web.WebSearchResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebSearchModuleTest {
    @TempDir Path temporary;

    @Test void deepSearchRunsTwoSearchOperationsThenASeparateInferenceReview() {
        AtomicInteger searches = new AtomicInteger();
        AtomicInteger reviews = new AtomicInteger();
        try (KernelRuntime kernel = kernel()) {
            var searchRegistration = kernel.capabilities().register(
                    searchCapability(Privacy.UNKNOWN, searches), 100);
            var inferenceRegistration = kernel.capabilities().register(
                    inferenceCapability(Privacy.SECRET, reviews), 100);
            WebSearchModule module = new WebSearchModule(kernel.execution());
            var moduleRegistration = kernel.modules().register(module.definition());
            try {
                Material<String> first = module.searchQuery("MADRE privacy algebra", Sensitivity.S2);
                Material<String> second = module.searchQuery("agent workflows", Sensitivity.S2);

                Material<String> review = module.deepSearch(first, second)
                        .toCompletableFuture().join();

                assertEquals(WebSearchModule.RESEARCH_REVIEW, review.type());
                assertEquals("grounded review", review.payload());
                assertEquals(Sensitivity.S2, review.sensitivity());
                assertEquals(2, searches.get());
                assertEquals(1, reviews.get());
                var agent = module.definition().agents().values().iterator().next();
                var workflow = agent.workflows().get(WebSearchModule.DEEP_SEARCH);
                assertEquals(3, workflow.operations().size());
                assertEquals(WebSearchModule.SINGLE_SEARCH, workflow.operations().get(0));
                assertEquals(WebSearchModule.SINGLE_SEARCH, workflow.operations().get(1));
                assertNotEquals(WebSearchModule.SINGLE_SEARCH, workflow.operations().get(2));
            } finally {
                moduleRegistration.close();
                inferenceRegistration.close();
                searchRegistration.close();
            }
        }
    }

    @Test void queryMoreSensitiveThanSearchCapabilityIsStructurallyUnavailable() {
        AtomicInteger searches = new AtomicInteger();
        try (KernelRuntime kernel = kernel()) {
            var searchRegistration = kernel.capabilities().register(
                    searchCapability(Privacy.UNKNOWN, searches), 100);
            WebSearchModule module = new WebSearchModule(kernel.execution());
            try {
                CompletionException failure = assertThrows(CompletionException.class, () ->
                        module.singleSearch(module.searchQuery("private research", Sensitivity.S3))
                                .toCompletableFuture().join());
                PhysicalExecutionException physical = assertInstanceOf(
                        PhysicalExecutionException.class, failure.getCause());
                assertEquals(PhysicalFailureCategory.UNAVAILABLE, physical.category());
                assertEquals(0, searches.get());
            } finally {
                searchRegistration.close();
            }
        }
    }

    private KernelRuntime kernel() {
        return new KernelRuntime(new KernelConfiguration(new ModuleId("test.core"),
                temporary.resolve("kernel.sqlite"), Duration.ofMinutes(5), Map.of()));
    }

    private static Capability<WebSearchCommand, WebSearchResult> searchCapability(
            Privacy privacy, AtomicInteger executions) {
        PhysicalContract<WebSearchCommand, WebSearchResult> contract = new PhysicalContract<>(
                WebSearchCodecs.CONTRACT_ID, WebSearchCommand.class, WebSearchResult.class,
                new PhysicalCodec<>() {
                    @Override public byte[] encode(WebSearchCommand value) {
                        return WebSearchCodecs.encodeCommand(value);
                    }
                    @Override public WebSearchCommand decode(byte[] bytes) {
                        return WebSearchCodecs.decodeCommand(bytes);
                    }
                }, new PhysicalCodec<>() {
                    @Override public byte[] encode(WebSearchResult value) {
                        return WebSearchCodecs.encodeResult(value);
                    }
                    @Override public WebSearchResult decode(byte[] bytes) {
                        return WebSearchCodecs.decodeResult(bytes);
                    }
                });
        CapabilityManifest<WebSearchCommand, WebSearchResult> manifest = new CapabilityManifest<>(
                new CapabilityId("fixture-search"), contract, privacy, Optional.of(Integrity.I5),
                PhysicalLocation.REMOTE, Duration.ofMillis(10), List.of());
        return new Capability<>() {
            @Override public CapabilityManifest<WebSearchCommand, WebSearchResult> manifest() {
                return manifest;
            }
            @Override public CapabilityAvailability availability() {
                return CapabilityAvailability.AVAILABLE;
            }
            @Override public WebSearchResult execute(WebSearchCommand command,
                    ExecutionContext context) throws CapabilityException {
                context.requireActive();
                executions.incrementAndGet();
                return new WebSearchResult(List.of(new WebSearchHit(
                        "Result for " + command.query(), "https://example.test/"
                                + executions.get(), "fixture excerpt")));
            }
        };
    }

    private static Capability<TextInferenceCommand, TextInferenceResult> inferenceCapability(
            Privacy privacy, AtomicInteger executions) {
        PhysicalContract<TextInferenceCommand, TextInferenceResult> contract =
                new PhysicalContract<>(TextInferenceCodecs.CONTRACT_ID,
                        TextInferenceCommand.class, TextInferenceResult.class,
                        new PhysicalCodec<>() {
                            @Override public byte[] encode(TextInferenceCommand value) {
                                return TextInferenceCodecs.encodeCommand(value);
                            }
                            @Override public TextInferenceCommand decode(byte[] bytes) {
                                return TextInferenceCodecs.decodeCommand(bytes);
                            }
                        }, new PhysicalCodec<>() {
                            @Override public byte[] encode(TextInferenceResult value) {
                                return TextInferenceCodecs.encodeResult(value);
                            }
                            @Override public TextInferenceResult decode(byte[] bytes) {
                                return TextInferenceCodecs.decodeResult(bytes);
                            }
                        });
        CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest =
                new CapabilityManifest<>(new CapabilityId("fixture-inference"), contract,
                        privacy, Optional.of(Integrity.I5), PhysicalLocation.LOCAL,
                        Duration.ofMillis(10), List.of());
        return new Capability<>() {
            @Override public CapabilityManifest<TextInferenceCommand, TextInferenceResult> manifest() {
                return manifest;
            }
            @Override public CapabilityAvailability availability() {
                return CapabilityAvailability.AVAILABLE;
            }
            @Override public TextInferenceResult execute(TextInferenceCommand command,
                    ExecutionContext context) throws CapabilityException {
                context.requireActive();
                executions.incrementAndGet();
                return new TextInferenceResult("grounded review",
                        TextInferenceResult.CompletionReason.STOP, 5, 4);
            }
        };
    }
}
