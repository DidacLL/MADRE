package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;

/** Installation entrypoint for the shipped ordinary owner-interaction Module. */
public final class OwnerInteractionModuleProvider implements ModuleProvider {
    @Override public ModuleInstance create(ModuleContext context) {
        return new OwnerInteractionModule(context.reasoning(),
                context.stateDirectory().resolve("owner-interaction-background.state")).instance();
    }
}
