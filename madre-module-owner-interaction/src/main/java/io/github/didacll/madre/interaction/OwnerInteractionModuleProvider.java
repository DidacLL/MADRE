package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;

/** Installation entrypoint for the shipped ordinary owner-interaction Module. */
public final class OwnerInteractionModuleProvider implements ModuleProvider {
    @Override public ModuleId moduleId() { return OwnerInteractionModule.ID; }

    @Override public ModuleInstance create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        OwnerInteractionSettings settings = OwnerInteractionSettings.fromInstallation(configuration);
        return new OwnerInteractionModule(context.reasoning(),
                context.stateDirectory().resolve("owner-interaction-background.state"), settings)
                .instance();
    }
}
