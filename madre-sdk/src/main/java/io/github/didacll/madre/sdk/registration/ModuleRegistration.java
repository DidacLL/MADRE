package io.github.didacll.madre.sdk.registration;

import io.github.didacll.madre.sdk.invocation.ModuleEndpoint;
import io.github.didacll.madre.sdk.module.ModuleDefinition;

/** Port through which a running Module joins and leaves the live registry. */
public interface ModuleRegistration {
    Registration register(ModuleDefinition definition, ModuleEndpoint endpoint);

    interface Registration extends AutoCloseable {
        ModuleDefinition definition();
        @Override void close();
    }
}
