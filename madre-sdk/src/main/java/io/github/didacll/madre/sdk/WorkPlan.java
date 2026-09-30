package io.github.didacll.madre.sdk;

import java.util.Collection;

/** Objective-specific semantic planning that may coordinate Agents and Workflows. */
public interface WorkPlan {
    Collection<? extends Step> steps();
}
