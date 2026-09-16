package io.github.didacll.madre.aaaat;

import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationField;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Service-provider entrypoint for an owner-installed AAAAT application/workspace. */
public final class AaaatModuleProvider implements ModuleProvider {
    public static final String EXECUTABLE_PATH = "executable-path";
    public static final String WORKSPACE_PATH = "workspace-path";
    private static final Set<String> CONFIG_KEYS = Set.of(EXECUTABLE_PATH, WORKSPACE_PATH);

    @Override public io.github.didacll.madre.sdk.identity.ModuleId moduleId() {
        return AaaatContracts.MODULE_ID;
    }

    @Override public ModuleConfigurationDescriptor configurationDescriptor() {
        return new ModuleConfigurationDescriptor(moduleId(), "AAAAT",
                "Connect an existing owner-installed AAAAT application and workspace through AAAAT's official bounded stdio interface.",
                List.of(
                        ModuleConfigurationField.text(EXECUTABLE_PATH, "AAAAT executable",
                                "Path to the installed AAAAT executable that supports --mcp.", true, null),
                        ModuleConfigurationField.text(WORKSPACE_PATH, "AAAAT workspace",
                                "Path to an existing AAAAT workspace. MADRE does not create or inspect it directly.", true, null)));
    }

    @Override public ModuleProviderConfiguration validateConfiguration(
            ModuleProviderConfiguration configuration) {
        ModuleProviderConfiguration candidate = ModuleProvider.super.validateConfiguration(configuration);
        if (!candidate.keys().equals(CONFIG_KEYS)) {
            throw new IllegalArgumentException("AAAAT Module requires exactly " + CONFIG_KEYS);
        }
        Map<String, String> normalized = Map.of(
                EXECUTABLE_PATH, required(candidate, EXECUTABLE_PATH),
                WORKSPACE_PATH, required(candidate, WORKSPACE_PATH));
        return new ModuleProviderConfiguration(moduleId(), normalized);
    }

    @Override public Module create(ModuleContext context, ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        ModuleProviderConfiguration validated = validateConfiguration(configuration);
        return new AaaatModule(new AaaatMcpClient(
                Path.of(validated.value(EXECUTABLE_PATH).orElseThrow()),
                Path.of(validated.value(WORKSPACE_PATH).orElseThrow())));
    }

    private static String required(ModuleProviderConfiguration configuration, String key) {
        String value = configuration.value(key).orElseThrow(
                () -> new IllegalArgumentException("missing AAAAT configuration: " + key)).strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("AAAAT configuration must not be blank: " + key);
        }
        return value;
    }
}
