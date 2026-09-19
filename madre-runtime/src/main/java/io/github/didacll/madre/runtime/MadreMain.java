package io.github.didacll.madre.runtime;

import io.github.didacll.madre.inference.llamacpp.LlamaCppEngine;
import io.github.didacll.madre.inference.openai.OpenAiCompatibleEngine;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.EngineLocation;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.ServiceLoader;

/** Small installed-runtime launcher; provider configuration is optional. */
public final class MadreMain {
    private MadreMain() { }

    public static void main(String[] args) throws Exception {
        Path state = Path.of(System.getProperty("madre.state", ".madre-state"))
                .toAbsolutePath().normalize();
        Files.createDirectories(state);
        try (InferenceKernel kernel = new InferenceKernel(state.resolve("kernel.db"),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2), Map.of())) {
            installConfiguredEngines(kernel);
            RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
            RuntimeInferenceService inference = new RuntimeInferenceService(kernel,
                    state.resolve("semantic-inference"));
            MadreRuntime runtime = new MadreRuntime(registry, inference, state);
            ServiceLoader.load(ModuleProvider.class).forEach(runtime::install);
            ModuleId core = new ModuleId(System.getProperty("madre.core", "owner-interaction"));
            if (registry.modules().stream().anyMatch(module -> module.definition().id().equals(core))) {
                AgentId defaultAgent = new AgentId(core,
                        System.getProperty("madre.core.agent", "conversation"));
                registry.assignCore(core, defaultAgent);
            }
            inference.recoverPending();
            System.out.printf("MADRE runtime ready: %d module(s), %d inference engine(s), CORE=%s%n",
                    registry.modules().size(), kernel.installedEngines().size(),
                    registry.coreModule().map(ModuleId::value).orElse("unassigned"));
        }
    }

    private static void installConfiguredEngines(InferenceKernel kernel) {
        String llamaEndpoint = System.getProperty("madre.llamacpp.endpoint", "");
        if (!llamaEndpoint.isBlank()) {
            kernel.register(new LlamaCppEngine(new EngineId("llamacpp"), URI.create(llamaEndpoint),
                    System.getProperty("madre.llamacpp.model", "local"), Duration.ofSeconds(2),
                    Boolean.getBoolean("madre.llamacpp.demanding-reasoning"),
                    Integer.getInteger("madre.llamacpp.context-tokens", 4096), 0));
        }
        String openAiEndpoint = System.getProperty("madre.openai.endpoint", "");
        if (!openAiEndpoint.isBlank()) {
            kernel.register(new OpenAiCompatibleEngine(new EngineId("openai-compatible"),
                    URI.create(openAiEndpoint), System.getProperty("madre.openai.key", ""),
                    System.getProperty("madre.openai.provider", "openai-compatible"),
                    System.getProperty("madre.openai.model", "configured"),
                    Boolean.getBoolean("madre.openai.local") ? EngineLocation.LOCAL
                            : EngineLocation.REMOTE,
                    Duration.ofSeconds(3), Boolean.getBoolean("madre.openai.demanding-reasoning"),
                    Integer.getInteger("madre.openai.context-tokens", 8192), 0));
        }
    }
}
