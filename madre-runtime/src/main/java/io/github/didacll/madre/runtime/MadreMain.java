package io.github.didacll.madre.runtime;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.inference.llamacpp.LlamaCppEngine;
import io.github.didacll.madre.inference.openai.OpenAiCompatibleEngine;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.EngineSnapshot;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.sdk.execution.InferenceSelection;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ConversationMessage;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.io.Console;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** Installed MADRE launcher with optional configured inference engines and console presentation. */
public final class MadreMain {
    private MadreMain() { }

    public static void main(String[] args) throws Exception {
        Path state = Path.of(System.getProperty("madre.state", ".madre-state"))
                .toAbsolutePath().normalize();
        Files.createDirectories(state);
        try (InferenceKernel kernel = new InferenceKernel(
                state.resolve("kernel.db"),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                Map.of())) {
            installConfiguredEngines(kernel);
            Optional<InferenceSelection> defaultSelection =
                    configuredDefaultSelection();

            RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
            RuntimeInferenceService inference = new RuntimeInferenceService(
                    kernel,
                    state.resolve("semantic-inference"),
                    defaultSelection);
            MadreRuntime runtime = new MadreRuntime(registry, inference, state);
            ServiceLoader.load(ModuleProvider.class).forEach(runtime::install);

            ModuleId core = new ModuleId(
                    System.getProperty("madre.core", "owner-interaction"));
            if (registry.modules().stream()
                    .anyMatch(module -> module.definition().id().equals(core))) {
                AgentId defaultAgent = new AgentId(
                        core,
                        System.getProperty(
                                "madre.core.agent", "conversation"));
                registry.assignCore(core, defaultAgent);
            }

            inference.recoverPending();
            printInstalledState(registry, kernel, inference);
            runConsole(runtime);
        }
    }

    private static void installConfiguredEngines(InferenceKernel kernel) {
        String llamaEndpoint =
                System.getProperty("madre.llamacpp.endpoint", "").strip();
        if (!llamaEndpoint.isEmpty()) {
            kernel.register(new LlamaCppEngine(
                    new EngineId(System.getProperty(
                            "madre.llamacpp.engine", "llamacpp")),
                    absoluteUri(llamaEndpoint, "madre.llamacpp.endpoint"),
                    System.getProperty("madre.llamacpp.model", "configured"),
                    Duration.ofMillis(Long.getLong(
                            "madre.llamacpp.expected-latency-ms", 2_000L)),
                    Integer.getInteger(
                            "madre.llamacpp.context-tokens", 4096)));
        }

        String openAiEndpoint =
                System.getProperty("madre.openai.endpoint", "").strip();
        if (!openAiEndpoint.isEmpty()) {
            kernel.register(new OpenAiCompatibleEngine(
                    new EngineId(System.getProperty(
                            "madre.openai.engine", "openai-compatible")),
                    absoluteUri(openAiEndpoint, "madre.openai.endpoint"),
                    System.getProperty("madre.openai.key", ""),
                    System.getProperty(
                            "madre.openai.provider", "openai-compatible"),
                    System.getProperty("madre.openai.model", "configured"),
                    Duration.ofMillis(Long.getLong(
                            "madre.openai.expected-latency-ms", 3_000L)),
                    Integer.getInteger(
                            "madre.openai.context-tokens", 8192)));
        }
    }

    private static Optional<InferenceSelection> configuredDefaultSelection() {
        Optional<String> engine = property("madre.inference.default.engine");
        Optional<String> provider =
                property("madre.inference.default.provider");
        Optional<String> model = property("madre.inference.default.model");
        Optional<String> endpoint =
                property("madre.inference.default.endpoint");
        if (engine.isEmpty()
                && provider.isEmpty()
                && model.isEmpty()
                && endpoint.isEmpty()) {
            return Optional.empty();
        }
        endpoint.ifPresent(value ->
                absoluteUri(value, "madre.inference.default.endpoint"));
        return Optional.of(new InferenceSelection(
                engine, provider, model, endpoint));
    }

    private static Optional<String> property(String name) {
        String value = System.getProperty(name, "").strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static URI absoluteUri(String value, String property) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(
                    property + " must be an absolute URI");
        }
        return uri;
    }

    private static void printInstalledState(
            RuntimeModuleRegistry registry,
            InferenceKernel kernel,
            RuntimeInferenceService inference) {
        System.out.printf(
                "MADRE runtime ready: %d module(s), %d inference engine(s), CORE=%s, default=%s%n",
                registry.modules().size(),
                kernel.installedEngines().size(),
                registry.coreModule().map(ModuleId::value).orElse("unassigned"),
                inference.defaultSelection().map(Object::toString)
                        .orElse("semantic-requirements"));
        for (EngineSnapshot snapshot : kernel.engineSnapshots()) {
            System.out.printf(
                    "engine %s: provider=%s model=%s endpoint=%s latency=%s availability=%s context=%s%n",
                    snapshot.id(),
                    snapshot.characteristics().provider(),
                    snapshot.characteristics().model(),
                    snapshot.characteristics().endpoint(),
                    snapshot.characteristics().expectedLatency(),
                    snapshot.availability(),
                    snapshot.characteristics().chatCompletion()
                            .map(capability -> Integer.toString(
                                    capability.maximumContextTokens()))
                            .orElse("unsupported"));
        }
    }

    private static void runConsole(MadreRuntime runtime) {
        Console console = System.console();
        if (console == null) {
            return;
        }
        Sensitivity sensitivity = Sensitivity.valueOf(
                System.getProperty(
                        "madre.owner.input-sensitivity", "S5"));
        console.writer().println(
                "MADRE owner console. Type 'exit' or 'quit' to stop.");
        while (true) {
            String input = console.readLine("madre> ");
            if (input == null) {
                return;
            }
            String text = input.strip();
            if (text.equalsIgnoreCase("exit")
                    || text.equalsIgnoreCase("quit")) {
                return;
            }
            if (text.isEmpty()) {
                continue;
            }
            try {
                ConversationMessage response = runtime.respond(
                        new ConversationMessage(
                                ConversationMessage.Role.HUMAN,
                                text,
                                sensitivity))
                        .toCompletableFuture()
                        .join();
                console.writer().println(response.text());
            } catch (RuntimeException failure) {
                Throwable cause = failure.getCause() == null
                        ? failure
                        : failure.getCause();
                console.writer().println(
                        "MADRE could not complete the response: "
                                + cause.getMessage());
            }
        }
    }
}
