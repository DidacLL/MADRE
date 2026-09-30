package io.github.didacll.madre.sdk;

/** Ordered consequence of the concrete Operation or effect selected now. */
public enum Risk {
    SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
}
