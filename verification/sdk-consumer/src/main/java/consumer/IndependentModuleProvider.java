package consumer;

import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;

/** Service-provider entrypoint for the independently built executable Module artifact. */
public final class IndependentModuleProvider implements ModuleProvider {
    @Override public ModuleInstance create(ModuleContext context) {
        java.util.Objects.requireNonNull(context, "context");
        return IndependentDefinition.instance();
    }
}
