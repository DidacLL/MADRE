package io.github.didacll.madre.sdk;

import java.nio.file.Path;

/** Shared installation mechanics available to an installed Module. */
public interface ModuleEnvironment {
    /** Stable owner-local directory. The Module owns its contents and their meaning. */
    Path dataDirectory();

    /** Run Module-owned code on the MADRE loop. Posted code is not durable. */
    void post(Runnable action);
}
