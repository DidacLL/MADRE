package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.identity.WorkflowId;
import java.util.concurrent.CompletionStage;

/**
 * Executable Agent-owned semantic behavior. A Workflow is ordinary Java composition, not a
 * Kernel plan, universal DAG, or portable scheduler language.
 */
public interface Workflow<I, O> {
    WorkflowId id();
    String purpose();
    CompletionStage<O> execute(AgentContext context, I input);
}
