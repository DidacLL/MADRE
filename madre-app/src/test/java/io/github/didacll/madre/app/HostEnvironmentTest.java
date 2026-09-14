package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HostEnvironmentTest {
    @TempDir Path temporary;

    @Test void linuxUsesXdgLocationsAndBootstrapsOnce() throws Exception {
        Path program = temporary.resolve("program");
        Path defaults = program.resolve("defaults/madre.properties");
        Files.createDirectories(defaults.getParent());
        Files.writeString(defaults, "roles.core=example.core\nresources.model-slot=1\n");
        Path configRoot = temporary.resolve("xdg-config");
        Path dataRoot = temporary.resolve("xdg-data");
        Path stateRoot = temporary.resolve("xdg-state");
        HostEnvironment environment = HostEnvironment.resolve(Map.of(
                "XDG_CONFIG_HOME", configRoot.toString(),
                "XDG_DATA_HOME", dataRoot.toString(),
                "XDG_STATE_HOME", stateRoot.toString()), "Linux", temporary.resolve("home"), program);

        HostEnvironment.LoadedConfiguration first =
                environment.loadConfiguration(Optional.empty());
        assertTrue(first.bootstrapped());
        assertFalse(first.explicit());
        assertTrue(environment.hasPackagedDefaults());
        assertEquals(configRoot.resolve("madre/madre.properties").toAbsolutePath().normalize(),
                first.path());
        assertEquals("example.core", first.properties().getProperty("roles.core"));
        assertEquals(stateRoot.resolve("madre/kernel-work.sqlite").toAbsolutePath().normalize(),
                Path.of(first.properties().getProperty("kernel.database")).toAbsolutePath().normalize());
        assertEquals(stateRoot.resolve("madre/module-state").toAbsolutePath().normalize(),
                Path.of(first.properties().getProperty("modules.state-directory"))
                        .toAbsolutePath().normalize());
        assertTrue(Files.isDirectory(dataRoot.resolve("madre/modules")));
        assertTrue(Files.isDirectory(dataRoot.resolve("madre/reasoning")));

        String persisted = Files.readString(first.path());
        HostEnvironment.LoadedConfiguration second =
                environment.loadConfiguration(Optional.empty());
        assertFalse(second.bootstrapped());
        assertEquals(persisted, Files.readString(second.path()));
    }

    @Test void windowsSeparatesRoamingConfigurationFromLocalData() {
        Path roaming = temporary.resolve("Roaming");
        Path local = temporary.resolve("Local");
        Path program = temporary.resolve("Program Files/MADRE/app");
        HostEnvironment environment = HostEnvironment.resolve(Map.of(
                "APPDATA", roaming.toString(),
                "LOCALAPPDATA", local.toString()), "Windows 11", temporary.resolve("home"), program);

        assertEquals(roaming.resolve("MADRE/madre.properties").toAbsolutePath().normalize(),
                environment.configurationFile());
        assertEquals(local.resolve("MADRE").toAbsolutePath().normalize(),
                environment.dataDirectory());
        assertEquals(local.resolve("MADRE/state").toAbsolutePath().normalize(),
                environment.stateDirectory());
        assertEquals(program.resolve("modules").toAbsolutePath().normalize(),
                environment.moduleDirectories().get(0));
        assertEquals(local.resolve("MADRE/modules").toAbsolutePath().normalize(),
                environment.moduleDirectories().get(1));
    }

    @Test void explicitConfigurationIsLoadedWithoutRewritingIt() throws Exception {
        Path explicit = temporary.resolve("custom.properties");
        Files.writeString(explicit, "kernel.database=/tmp/custom.sqlite\n");
        HostEnvironment environment = HostEnvironment.resolve(Map.of(), "Linux",
                temporary.resolve("home"), temporary.resolve("program"));

        HostEnvironment.LoadedConfiguration loaded =
                environment.loadConfiguration(Optional.of(explicit));
        assertTrue(loaded.explicit());
        assertFalse(loaded.bootstrapped());
        assertEquals("/tmp/custom.sqlite", loaded.properties().getProperty("kernel.database"));
        assertEquals("kernel.database=/tmp/custom.sqlite\n", Files.readString(explicit));
    }
}
