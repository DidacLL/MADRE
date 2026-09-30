package io.github.didacll.madre.sdk;

import java.util.Collection;

/** Objective-specific semantic planning whose state belongs to its Module. */
public interface WorkPlan {
    Collection<? extends Step> steps();

    /** The acting Agent is bound to this step's work. */
    interface Step {
        MADREAgent agent();

        Executable executable();
    }
}
