package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.Module;

/** Java service-provider entrypoint for one installable executable MADRE Module artifact. */
public interface ModuleProvider {
    /** Canonical identity used to associate owner installation configuration before materialization. */
    ModuleId moduleId();

    /**
     * Materializes the executable semantic Module from ordinary SDK services. Module-specific
     * configuration is ordinary Module/provider code rather than a universal metadata schema.
     */
    Module create(ModuleContext context);
}
