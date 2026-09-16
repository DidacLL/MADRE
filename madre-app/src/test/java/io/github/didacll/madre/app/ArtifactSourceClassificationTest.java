package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArtifactSourceClassificationTest {
    @TempDir Path temporary;

    @Test void moduleClassificationSeparatesManagedOwnerFromManualPlacement() {
        HostEnvironment host = host();
        ModuleId id = new ModuleId("example.module");
        Path shipped = host.moduleDirectories().getFirst().resolve("shipped.jar");
        Path managed = ManagedJarFiles.managedPath(host.ownerModuleDirectory(), "module",
                id.value());
        Path manual = host.ownerModuleDirectory().resolve("manual.jar");
        Path development = temporary.resolve("development/module.jar");

        assertEquals(ModuleArtifactLifecycle.Source.SHIPPED,
                ModuleArtifactLifecycle.classify(host, id, shipped));
        assertEquals(ModuleArtifactLifecycle.Source.OWNER,
                ModuleArtifactLifecycle.classify(host, id, managed));
        assertEquals(ModuleArtifactLifecycle.Source.MANUAL,
                ModuleArtifactLifecycle.classify(host, id, manual));
        assertEquals(ModuleArtifactLifecycle.Source.DEVELOPMENT,
                ModuleArtifactLifecycle.classify(host, id, development));
    }

    @Test void reasoningClassificationSeparatesManagedOwnerFromManualPlacement() {
        HostEnvironment host = host();
        ReasoningProviderId id = new ReasoningProviderId("example-provider");
        Path shipped = host.reasoningDirectories().getFirst().resolve("shipped.jar");
        Path managed = ManagedJarFiles.managedPath(host.ownerReasoningDirectory(), "reasoning",
                id.value());
        Path manual = host.ownerReasoningDirectory().resolve("manual.jar");
        Path development = temporary.resolve("development/reasoning.jar");

        assertEquals(ReasoningArtifactLifecycle.Source.SHIPPED,
                ReasoningArtifactLifecycle.classify(host, id, shipped));
        assertEquals(ReasoningArtifactLifecycle.Source.OWNER,
                ReasoningArtifactLifecycle.classify(host, id, managed));
        assertEquals(ReasoningArtifactLifecycle.Source.MANUAL,
                ReasoningArtifactLifecycle.classify(host, id, manual));
        assertEquals(ReasoningArtifactLifecycle.Source.DEVELOPMENT,
                ReasoningArtifactLifecycle.classify(host, id, development));
    }

    private HostEnvironment host() {
        return HostEnvironment.resolve(Map.of(
                "XDG_CONFIG_HOME", temporary.resolve("config").toString(),
                "XDG_DATA_HOME", temporary.resolve("data").toString(),
                "XDG_STATE_HOME", temporary.resolve("state").toString()),
                "Linux", temporary.resolve("home"), temporary.resolve("program"));
    }
}
