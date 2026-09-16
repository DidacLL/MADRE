package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ManagedJarFilesTest {
    @TempDir Path temporary;

    @Test void managedPathIsDeterministicAndScopedToOwnerRoot() {
        Path owner = temporary.resolve("owner/modules");

        Path first = ManagedJarFiles.managedPath(owner, "module", "phd.module");
        Path second = ManagedJarFiles.managedPath(owner, "module", "phd.module");

        assertEquals(first, second);
        assertEquals(owner.toAbsolutePath().normalize(), first.getParent());
        assertTrue(first.getFileName().toString().startsWith("module-"));
        assertTrue(first.getFileName().toString().endsWith(".jar"));
    }

    @Test void exactManagedSlotIsAccepted() {
        Path owner = temporary.resolve("owner/reasoning");
        Path managed = ManagedJarFiles.managedPath(owner, "reasoning", "fixture.provider");

        assertTrue(ManagedJarFiles.isManagedPath(owner, "reasoning", "fixture.provider", managed));
        assertEquals(managed, ManagedJarFiles.requireManagedPath(
                owner, "reasoning", "fixture.provider", managed));
    }

    @Test void manuallyPlacedOwnerJarIsNeverTreatedAsLifecycleManaged() {
        Path owner = temporary.resolve("owner/modules");
        Path manual = owner.resolve("my-module.jar");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ManagedJarFiles.requireManagedPath(owner, "module", "phd.module", manual));

        assertTrue(failure.getMessage().contains("manually placed owner JAR"));
        assertFalse(ManagedJarFiles.isManagedPath(owner, "module", "phd.module", manual));
    }

    @Test void managedSlotDoesNotTransferAcrossIdentityOrRoot() {
        Path owner = temporary.resolve("owner/modules");
        Path first = ManagedJarFiles.managedPath(owner, "module", "first.module");
        Path otherRoot = temporary.resolve("other/modules");

        assertFalse(ManagedJarFiles.isManagedPath(owner, "module", "second.module", first));
        assertFalse(ManagedJarFiles.isManagedPath(otherRoot, "module", "first.module", first));
    }

    @Test void commitReplacesTheManagedSlotFromSameFilesystemStaging() throws Exception {
        Path owner = temporary.resolve("owner/modules");
        Files.createDirectories(owner);
        Path destination = ManagedJarFiles.managedPath(owner, "module", "phd.module");
        Files.writeString(destination, "old");
        Path staged = Files.createTempFile(owner, ".madre-install-", ".jar.tmp");
        Files.writeString(staged, "new");

        ManagedJarFiles.commit(staged, destination);

        assertEquals("new", Files.readString(destination));
        assertFalse(Files.exists(staged));
    }
}
