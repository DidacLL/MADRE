package io.github.didacll.madre.sdk;

/** One WorkPlan assignment; its Agent is bound to this step, not the whole plan. */
public interface Step {
    MADREAgent agent();

    Executable executable();
}
