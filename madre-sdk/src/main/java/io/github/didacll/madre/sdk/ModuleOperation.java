package io.github.didacll.madre.sdk;

/** Bounded action supplied by a Module; its providing Module need not have an Agent. */
public interface ModuleOperation extends Executable {
    String id();
}
