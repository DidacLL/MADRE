package consumer;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationField;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.util.List;
import java.util.Map;

/** Service-provider entrypoint for the independently built executable Module artifact. */
public final class IndependentModuleProvider implements ModuleProvider {
    private static final String RESULT_PREFIX = "result-prefix";
    private static final ModuleConfigurationDescriptor CONFIGURATION =
            new ModuleConfigurationDescriptor(IndependentDefinition.ID, "Independent SDK consumer",
                    "Configuration owned by the independently compiled verification Module.",
                    List.of(ModuleConfigurationField.text(RESULT_PREFIX, "Result prefix",
                            "Optional prefix prepended to the Module's semantic result payloads.",
                            false, null)));

    @Override public ModuleId moduleId() { return IndependentDefinition.ID; }

    @Override public ModuleConfigurationDescriptor configurationDescriptor() { return CONFIGURATION; }

    @Override public ModuleProviderConfiguration validateConfiguration(
            ModuleProviderConfiguration configuration) {
        return canonical(configuration);
    }

    @Override public ModuleInstance create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        ModuleProviderConfiguration canonical = canonical(configuration);
        String prefix = canonical.value(RESULT_PREFIX).orElse("");
        return IndependentDefinition.instance(context.reasoning(), prefix);
    }

    private static ModuleProviderConfiguration canonical(ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(configuration, "configuration");
        if (!configuration.moduleId().equals(IndependentDefinition.ID)) {
            throw new IllegalArgumentException("configuration targets the wrong Module: "
                    + configuration.moduleId());
        }
        configuration.keys().stream().filter(key -> !key.equals(RESULT_PREFIX)).findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException(
                            "unsupported independent Module configuration key: " + key);
                });
        if (configuration.value(RESULT_PREFIX).isEmpty()) {
            return new ModuleProviderConfiguration(IndependentDefinition.ID, Map.of());
        }
        String value = configuration.value(RESULT_PREFIX).orElseThrow();
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("result-prefix must not be blank");
        }
        if (!normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "result-prefix contains unsupported characters: " + value);
        }
        return new ModuleProviderConfiguration(IndependentDefinition.ID,
                Map.of(RESULT_PREFIX, normalized));
    }
}
