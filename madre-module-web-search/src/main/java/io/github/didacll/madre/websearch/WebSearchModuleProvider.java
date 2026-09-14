package io.github.didacll.madre.websearch;

import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;

/** Installation entrypoint for the shipped ordinary WebSearch Module. */
public final class WebSearchModuleProvider implements ModuleProvider {
    @Override public ModuleInstance create(ModuleContext context) {
        return new WebSearchModule(context.execution()).instance();
    }
}
