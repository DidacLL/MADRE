package io.github.didacll.madre.sdk;

import java.util.Collection;

/** Reusable graph of Operations whose transitions may depend on results. */
public interface Workflow extends Executable {
    /** Starting Operations of this graph. */
    Collection<ModuleOperation> starts();

    /** Successors after an Operation result; this does not execute them. */
    <R> Collection<ModuleOperation> next(ModuleOperation completed, R result);
}
