package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InstalledReasoningLoaderTest {
    @TempDir Path temporary;

    @Test void rejectsNullProviderMaterialization() throws Exception {
        Path directory = providerDirectory(NullMaterializationTestProvider.class);
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(directory)) {
            assertThrows(IllegalStateException.class,
                    () -> loader.materialize(new ReasoningProviderConfiguration(Map.of())));
        }
    }

    @Test void rejectsNullMechanismElement() throws Exception {
        Path directory = providerDirectory(NullMechanismTestProvider.class);
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(directory)) {
            assertThrows(NullPointerException.class,
                    () -> loader.materialize(new ReasoningProviderConfiguration(Map.of())));
        }
    }

    @Test void startupFailureRollsBackRegistrationsAndClosesProvider() throws Exception {
        Files.createDirectories(temporary.resolve("modules"));
        Path reasoning = providerDirectory(DuplicateMaterializationTestProvider.class);
        Properties properties = new Properties();
        properties.setProperty("kernel.database", temporary.resolve("kernel.sqlite").toString());
        properties.setProperty("kernel.result-retention-seconds", "3600");
        properties.setProperty("modules.directory", temporary.resolve("modules").toString());
        properties.setProperty("modules.state-directory",
                temporary.resolve("module-state").toString());
        properties.setProperty("reasoning.directory", reasoning.toString());

        assertThrows(IllegalStateException.class, () -> MadreApplication.start(properties));
        assertTrue(DuplicateMaterializationTestProvider.CLOSED.get(),
                "provider resources must be closed after registration rollback");

        properties.setProperty("reasoning.directory",
                temporary.resolve("missing-reasoning").toString());
        try (MadreApplication application = MadreApplication.start(properties)) {
            assertTrue(application.installedModules().isEmpty(),
                    "failed reasoning startup must not contaminate a later clean boot");
        }
    }

    private Path providerDirectory(Class<? extends ReasoningMechanismProvider> provider)
            throws IOException {
        Path directory = temporary.resolve(provider.getSimpleName());
        Files.createDirectories(directory);
        Path jar = directory.resolve("provider.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            String service = "META-INF/services/" + ReasoningMechanismProvider.class.getName();
            output.putNextEntry(new JarEntry(service));
            output.write((provider.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return directory;
    }
}
