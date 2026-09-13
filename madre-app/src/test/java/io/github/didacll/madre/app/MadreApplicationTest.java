package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.interaction.OwnerInteractionModule;
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
