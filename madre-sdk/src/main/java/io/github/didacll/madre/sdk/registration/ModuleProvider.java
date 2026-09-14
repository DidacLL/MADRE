package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.module.ModuleInstance;

/** Java service-provider entrypoint for one installable executable MADRE Module artifact. */
@FunctionalInterface
public interface ModuleProvider {
    ModuleInstance create(ModuleContext context);
}
