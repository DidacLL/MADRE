package consumer;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;

/** Service-provider entrypoint for the independently built executable Module artifact. */
public final class IndependentModuleProvider implements ModuleProvider {
    private static final String RESULT_PREFIX = "result-prefix";

    @Override public ModuleId moduleId() { return IndependentDefinition.ID; }

    @Override public ModuleInstance create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
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
        String prefix = configuration.value(RESULT_PREFIX).map(value -> {
            String normalized = value.strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("result-prefix must not be blank");
            }
            if (!normalized.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException(
                        "result-prefix contains unsupported characters: " + value);
            }
            return normalized;
        }).orElse("");
        return IndependentDefinition.instance(context.reasoning(), prefix);
    }
}
