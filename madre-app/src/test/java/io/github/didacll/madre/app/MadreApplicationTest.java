package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreApplicationTest {
    @TempDir Path temporary;

    @Test void bootsWithNoCoreNoModulesAndAbsentReasoningDirectory() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        try (MadreApplication application = MadreApplication.start(bareProperties())) {
            assertTrue(application.resolvedCore().isEmpty());
            assertTrue(application.installedModules().isEmpty());
            assertTrue(application.kernel().reasoningCapabilities()
                    .contractForComputation(TestComputation.class, String.class).isEmpty());
        }
    }

    @Test void bootsWithEmptyReasoningDirectory() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        Files.createDirectories(temporary.resolve("empty-reasoning"));
        Properties properties = bareProperties();
        properties.setProperty("reasoning.directory",
                temporary.resolve("empty-reasoning").toAbsolutePath().toString());
        try (MadreApplication application = MadreApplication.start(properties)) {
            assertTrue(application.kernel().reasoningCapabilities()
                    .contractForComputation(TestComputation.class, String.class).isEmpty());
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

    @Test void rejectsReasoningDirectoryThatIsAFile() throws Exception {
        Files.createDirectories(temporary.resolve("empty-modules"));
        Path file = Files.writeString(temporary.resolve("not-a-directory"), "x");
        Properties properties = bareProperties();
        properties.setProperty("reasoning.directory", file.toString());
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
        properties.setProperty("reasoning.directory",
                temporary.resolve("missing-reasoning").toAbsolutePath().toString());
        return properties;
    }

    private record TestComputation() implements ReasoningComputation<String> {
        @Override public Class<String> resultType() { return String.class; }
    }
}
