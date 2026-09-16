package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HostConfigurationPersistenceTest {
    @TempDir Path temporaryDirectory;

    @Test void replacementIsDeterministicAndPreservesUnrelatedValues() throws IOException {
        Path configuration = temporaryDirectory.resolve("madre.properties");
        Properties properties = new Properties();
        properties.setProperty("kernel.database", temporaryDirectory.resolve("kernel.sqlite").toString());
        properties.setProperty("modules.config[example.module].result-prefix", "owner-value");
        properties.setProperty("reasoning.foreign.instances", "keep");
        properties.setProperty("reasoning.foreign.keep.enabled", "false");

        HostEnvironment.replaceConfiguration(configuration, properties);
        String first = Files.readString(configuration);
        HostEnvironment.replaceConfiguration(configuration, properties);
        String second = Files.readString(configuration);

        assertEquals(first, second);
        assertTrue(first.indexOf("kernel.database") < first.indexOf("modules.config"));
        Properties loaded = new Properties();
        try (InputStream input = Files.newInputStream(configuration)) {
            loaded.load(input);
        }
        assertEquals("owner-value",
                loaded.getProperty("modules.config[example.module].result-prefix"));
        assertEquals("keep", loaded.getProperty("reasoning.foreign.instances"));
        assertEquals("false", loaded.getProperty("reasoning.foreign.keep.enabled"));
    }
}
