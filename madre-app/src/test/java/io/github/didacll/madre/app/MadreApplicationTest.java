package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import io.github.didacll.madre.web.WebSearchCommand;
import io.github.didacll.madre.web.WebSearchResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreApplicationTest {
    @TempDir Path temporary;

    @Test void bootsWithNoCoreNoModulesAndNoReasoningConnector() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        try (MadreApplication application = MadreApplication.start(bareProperties())) {
            assertTrue(application.resolvedCore().isEmpty());
            assertTrue(application.installedModules().isEmpty());
        }
    }

    @Test void configuredButAbsentCoreIsOnlyUnresolvedRoleState() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        Properties properties = bareProperties();
        properties.setProperty("roles.core", "ordinary.module");
        try (MadreApplication application = MadreApplication.start(properties)) {
            assertTrue(application.resolvedCore().isEmpty());
        }
    }

    @Test void acceptsUnixSocketAndLoopbackHttpAsCoexistingTextInferenceCapabilities()
            throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        Properties properties = connectorProperties();
        properties.setProperty("connector.llamacpp-unix.enabled", "true");
        properties.setProperty("connector.llamacpp-unix.id", "test-llama-uds");
        properties.setProperty("connector.llamacpp-unix.socket",
                temporary.resolve("llama.sock").toAbsolutePath().toString());
        properties.setProperty("connector.llamacpp-unix.model", "test-model");
        properties.setProperty("connector.llamacpp-unix.privacy", "SECRET");
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

    @Test void acceptsSemanticAndRankPrivacyConfigurationNames() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        Properties properties = connectorProperties();
        properties.setProperty("connector.llamacpp.privacy", "PUBLIC");
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

    @Test void rejectsSystemReservedPrivacyAsInstalledConnectorFact() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        Properties properties = connectorProperties();
        properties.setProperty("connector.llamacpp.privacy", "SYSTEM_RESERVED");
        assertThrows(IllegalArgumentException.class, () -> MadreApplication.start(properties));
    }

    private Properties bareProperties() {
        Properties properties = new Properties();
        properties.setProperty("kernel.database", temporary.resolve("kernel.sqlite").toString());
        properties.setProperty("kernel.result-retention-seconds", "3600");
        properties.setProperty("modules.directory",
                temporary.resolve("empty-modules").toAbsolutePath().toString());
        properties.setProperty("modules.state-directory",
                temporary.resolve("module-state").toAbsolutePath().toString());
        return properties;
    }

    private Properties connectorProperties() {
        Properties properties = bareProperties();
        properties.setProperty("resources.model-slot", "1");
        properties.setProperty("connector.llamacpp.enabled", "true");
        properties.setProperty("connector.llamacpp.id", "test-llama");
        properties.setProperty("connector.llamacpp.endpoint", "http://127.0.0.1:1/");
        properties.setProperty("connector.llamacpp.model", "test-model");
        properties.setProperty("connector.llamacpp.privacy", "SECRET");
        properties.setProperty("connector.llamacpp.integrity", "I5");
        properties.setProperty("connector.llamacpp.expected-latency-ms", "100");
        properties.setProperty("connector.llamacpp.preference", "100");
        properties.setProperty("connector.llamacpp.resource.model-slot", "1");
        return properties;
    }
}
