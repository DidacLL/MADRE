package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.core.CoreModule;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreApplicationTest {
    @TempDir Path temporary;

    @Test void assemblesShippedOrdinaryModuleAndResolvesCoreRole() {
        try (MadreApplication application = MadreApplication.start(properties())) {
            assertEquals(CoreModule.ID, application.kernel().transport().core().orElseThrow());
            assertEquals(CoreModule.ID, application.core().definition().id());
        }
    }

    @Test void refusesToTreatAnotherIdentityAsTheShippedCoreModule() {
        Properties properties = properties();
        properties.setProperty("roles.core", "another.module");
        assertThrows(IllegalArgumentException.class, () -> MadreApplication.start(properties));
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("roles.core", CoreModule.ID.value());
        properties.setProperty("kernel.bind", "127.0.0.1");
        properties.setProperty("kernel.database", temporary.resolve("kernel.sqlite").toString());
        properties.setProperty("kernel.result-retention-seconds", "3600");
        properties.setProperty("core.state", temporary.resolve("core.state").toString());
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
