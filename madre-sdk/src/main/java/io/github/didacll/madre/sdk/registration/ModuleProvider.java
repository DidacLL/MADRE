package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleInstance;

/** Java service-provider entrypoint for one installable executable MADRE Module artifact. */
public interface ModuleProvider {
    /** Canonical identity used to associate owner installation configuration before materialization. */
    ModuleId moduleId();

    /** Materializes exactly that Module from ordinary SDK services and its scoped owner settings. */
    ModuleInstance create(ModuleContext context, ModuleProviderConfiguration configuration);
}
