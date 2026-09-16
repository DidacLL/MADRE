package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.Module;
import java.util.Objects;

/** Java service-provider entrypoint for one installable executable MADRE Module artifact. */
public interface ModuleProvider {
    /** Canonical identity used to associate owner installation configuration before materialization. */
    ModuleId moduleId();

    /** Owner-facing installation configuration metadata available without Module materialization. */
    default ModuleConfigurationDescriptor configurationDescriptor() {
        return ModuleConfigurationDescriptor.none(moduleId());
    }

    /**
     * Validates and may canonicalize a complete candidate owner configuration for this Module.
     * This method must not materialize the Module or execute Module Operations.
     */
    default ModuleProviderConfiguration validateConfiguration(
            ModuleProviderConfiguration configuration) {
        ModuleProviderConfiguration candidate = Objects.requireNonNull(configuration, "configuration");
        ModuleId expected = Objects.requireNonNull(moduleId(), "moduleId()");
        if (!candidate.moduleId().equals(expected)) {
            throw new IllegalArgumentException("Module configuration targets " + candidate.moduleId()
                    + " instead of " + expected);
        }
        return candidate;
    }

    /**
     * Materializes exactly that executable semantic Module from ordinary SDK services and its
     * scoped owner settings. Runtime assembly and portable contract projection are host concerns.
     */
    Module create(ModuleContext context, ModuleProviderConfiguration configuration);
}
