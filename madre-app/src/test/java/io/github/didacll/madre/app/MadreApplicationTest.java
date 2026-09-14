package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.interaction.OwnerInteractionModule;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import io.github.didacll.madre.web.WebSearchCommand;
import io.github.didacll.madre.web.WebSearchResult;
import io.github.didacll.madre.websearch.WebSearchModule;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreApplicationTest {
    @TempDir Path temporary;

    @Test void assemblesTwoOrdinaryModulesAndResolvesQualifiedCoreRole() {
        try (MadreApplication application = MadreApplication.start(properties())) {
            assertEquals(OwnerInteractionModule.ID,
                    application.kernel().modules().resolvedCore().orElseThrow());
            assertEquals(OwnerInteractionModule.ID, application.interaction().definition().id());
            assertEquals(WebSearchModule.ID, application.webSearch().definition().id());
        }
    }

    @Test void acceptsUnixSocketAndLoopbackHttpAsCoexistingTextInferenceCapabilities() {
        Properties properties = properties();
        properties.setProperty("connector.llamacpp-unix.enabled", "true");
        properties.setProperty("connector.llamacpp-unix.id", "test-llama-uds");
        properties.setProperty("connector.llamacpp-unix.socket",
                temporary.resolve("llama.sock").toAbsolutePath().toString());
        properties.setProperty("connector.llamacpp-unix.model", "test-model");
        properties.setProperty("connector.llamacpp-unix.privacy", "P5");
        properties.setProperty("connector.llamacpp-unix.integrity", "I5");
        properties.setProperty("connector.llamacpp-unix.expected-latency-ms", "100");
        properties.setProperty("connector.llamacpp-unix.preference", "200");
        properties.setProperty("connector.llamacpp-unix.resource.model-slot", "1");

        try (MadreApplication application = MadreApplication.start(properties)) {
            assertTrue(application.kernel().capabilities()
                    .contractForCommand(TextInferenceCommand.class, TextInferenceResult.class)
                    .isPresent());
        }
    }

    @Test void acceptsCanonicalP1AndP2PrivacyConfigurationRanks() {
        Properties properties = properties();
        properties.setProperty("connector.llamacpp.privacy", "P1");
        properties.setProperty("resources.network-slot", "1");
        properties.setProperty("connector.searxng.enabled", "true");
        properties.setProperty("connector.searxng.id", "test-search");
        properties.setProperty("connector.searxng.endpoint", "http://127.0.0.1:1/search");
        properties.setProperty("connector.searxng.privacy", "P2");
        properties.setProperty("connector.searxng.integrity", "I2");
        properties.setProperty("connector.searxng.expected-latency-ms", "100");
        properties.setProperty("connector.searxng.preference", "100");
        properties.setProperty("connector.searxng.resource.network-slot", "1");

        try (MadreApplication application = MadreApplication.start(properties)) {
            assertTrue(application.kernel().capabilities()
                    .contractForCommand(WebSearchCommand.class, WebSearchResult.class)
                    .isPresent());
        }
    }

    @Test void rejectsRegisteredModuleThatDoesNotProvideCoreInteractionBehavior() {
        Properties properties = properties();
        properties.setProperty("roles.core", WebSearchModule.ID.value());
        assertThrows(IllegalArgumentException.class, () -> MadreApplication.start(properties));
    }

    @Test void rejectsCoreAssignmentThatIsNotARegisteredInstalledModule() {
        Properties properties = properties();
        properties.setProperty("roles.core", "another.module");
        assertThrows(IllegalStateException.class, () -> MadreApplication.start(properties));
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("roles.core", OwnerInteractionModule.ID.value());
        properties.setProperty("kernel.database", temporary.resolve("kernel.sqlite").toString());
        properties.setProperty("kernel.result-retention-seconds", "3600");
        properties.setProperty("module.owner-interaction.state",
                temporary.resolve("owner-interaction.state").toString());
        properties.setProperty("resources.model-slot", "1");
        properties.setProperty("connector.llamacpp.enabled", "true");
        properties.setProperty("connector.llamacpp.id", "test-llama");
        properties.setProperty("connector.llamacpp.endpoint", "http://127.0.0.1:1/");
        properties.setProperty("connector.llamacpp.model", "test-model");
        properties.setProperty("connector.llamacpp.privacy", "P5");
        properties.setProperty("connector.llamacpp.integrity", "I5");
        properties.setProperty("connector.llamacpp.expected-latency-ms", "100");
        properties.setProperty("connector.llamacpp.preference", "100");
        properties.setProperty("connector.llamacpp.resource.model-slot", "1");
        return properties;
    }
}
