package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationField;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModuleConfigurationManagerTest {
    @TempDir Path temporary;

    @Test void persistsOnlyTargetModuleAndUnrelatedPropertiesSurviveWithoutMaterialization()
            throws IOException {
        Path configurationFile = temporary.resolve("madre.properties");
        Properties properties = new Properties();
        properties.setProperty("kernel.database", "kernel.sqlite");
        properties.setProperty("reasoning.fixture.value", "reasoning-owned");
        properties.setProperty("modules.config[other.module].setting", "other-owned");
        properties.setProperty("modules.config[fixture.module].setting", "old");
        HostEnvironment.replaceConfiguration(configurationFile, properties);
        AtomicBoolean materialized = new AtomicBoolean();
        ModuleProvider provider = provider(materialized, false);
        ModuleConfigurationManager manager = new ModuleConfigurationManager(
                configurationFile, properties, List.of(provider));

        manager.configure("fixture.module", Map.of("setting", " new-value "));

        assertFalse(materialized.get());
        Properties persisted = load(configurationFile);
        assertEquals("new-value", persisted.getProperty("modules.config[fixture.module].setting"));
        assertEquals("other-owned", persisted.getProperty("modules.config[other.module].setting"));
        assertEquals("reasoning-owned", persisted.getProperty("reasoning.fixture.value"));
        assertEquals("kernel.sqlite", persisted.getProperty("kernel.database"));
        assertEquals("new-value", properties.getProperty("modules.config[fixture.module].setting"));
    }

    @Test void providerRejectionLeavesOriginalFileAndInMemoryConfigurationUnchanged()
            throws IOException {
        Path configurationFile = temporary.resolve("madre.properties");
        Properties properties = new Properties();
        properties.setProperty("kernel.database", "kernel.sqlite");
        properties.setProperty("modules.config[fixture.module].setting", "accepted");
        HostEnvironment.replaceConfiguration(configurationFile, properties);
        String before = Files.readString(configurationFile);
        ModuleConfigurationManager manager = new ModuleConfigurationManager(
                configurationFile, properties, List.of(provider(new AtomicBoolean(), false)));

        assertThrows(IllegalArgumentException.class,
                () -> manager.configure("fixture.module", Map.of("setting", "   ")));

        assertEquals(before, Files.readString(configurationFile));
        assertEquals("accepted", properties.getProperty("modules.config[fixture.module].setting"));
    }

    @Test void rejectedProviderCannotEscapeItsCanonicalIdentity() throws IOException {
        Path configurationFile = temporary.resolve("madre.properties");
        Properties properties = new Properties();
        properties.setProperty("kernel.database", "kernel.sqlite");
        HostEnvironment.replaceConfiguration(configurationFile, properties);
        String before = Files.readString(configurationFile);
        ModuleConfigurationManager manager = new ModuleConfigurationManager(configurationFile,
                properties, List.of(provider(new AtomicBoolean(), true)));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.configure("fixture.module", Map.of("setting", "value")));

        assertTrue(failure.getMessage().contains("escaped identity"));
        assertEquals(before, Files.readString(configurationFile));
        assertFalse(properties.stringPropertyNames().stream()
                .anyMatch(name -> name.startsWith("modules.config[other.module].")));
    }

    @Test void uninstalledModuleCannotBeConfigured() throws IOException {
        Path configurationFile = temporary.resolve("madre.properties");
        Properties properties = new Properties();
        properties.setProperty("kernel.database", "kernel.sqlite");
        HostEnvironment.replaceConfiguration(configurationFile, properties);
        ModuleConfigurationManager manager = new ModuleConfigurationManager(configurationFile,
                properties, List.of(provider(new AtomicBoolean(), false)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> manager.configure("missing.module", Map.of("setting", "value")));

        assertTrue(failure.getMessage().contains("not installed"));
    }

    private static ModuleProvider provider(AtomicBoolean materialized, boolean escapeIdentity) {
        ModuleId id = new ModuleId("fixture.module");
        ModuleConfigurationDescriptor descriptor = new ModuleConfigurationDescriptor(id,
                "Fixture", "Fixture configuration.", List.of(ModuleConfigurationField.text(
                        "setting", "Setting", "Fixture setting.", false, null)));
        return new ModuleProvider() {
            @Override public ModuleId moduleId() { return id; }
            @Override public ModuleConfigurationDescriptor configurationDescriptor() {
                return descriptor;
            }
            @Override public ModuleProviderConfiguration validateConfiguration(
                    ModuleProviderConfiguration configuration) {
                String value = configuration.value("setting").orElse("").strip();
                if (value.isEmpty()) throw new IllegalArgumentException("setting must not be blank");
                return new ModuleProviderConfiguration(
                        escapeIdentity ? new ModuleId("other.module") : id,
                        Map.of("setting", value));
            }
            @Override public Module create(ModuleContext context,
                    ModuleProviderConfiguration configuration) {
                materialized.set(true);
                throw new AssertionError("configuration management must not materialize Modules");
            }
        };
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
