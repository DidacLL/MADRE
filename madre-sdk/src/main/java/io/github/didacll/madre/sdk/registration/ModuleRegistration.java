package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;

/** Port through which one executable Module instance joins and leaves the live registry. */
public interface ModuleRegistration {
    Registration register(ModuleInstance instance);

    interface Registration extends AutoCloseable {
        ModuleInstance instance();
        default ModuleDefinition definition() { return instance().definition(); }
        @Override void close();
    }
}
