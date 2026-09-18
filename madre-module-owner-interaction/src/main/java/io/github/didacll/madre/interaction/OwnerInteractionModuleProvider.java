package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;

/** Installation entrypoint for the ordinary shipped owner-interaction Module. */
public final class OwnerInteractionModuleProvider implements ModuleProvider {
    @Override public ModuleId moduleId() { return OwnerInteractionModule.ID; }
    @Override public Module create(ModuleContext context) {
        java.util.Objects.requireNonNull(context, "context");
        return new OwnerInteractionModule();
    }
}
