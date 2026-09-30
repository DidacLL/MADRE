package io.github.didacll.madre.sdk;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** Shared installation mechanics available to an installed Module. */
public interface ModuleEnvironment {
    /** Stable owner-local directory. The Module owns its contents and their meaning. */
    Path dataDirectory();

    /** Persist a wakeup for this Module. The reference is opaque to Runtime. */
    String schedule(Instant due, String reference) throws IOException;

    /** Cancel one of this Module's pending wakeups. */
    boolean cancel(String wakeupId) throws IOException;
}
