package io.github.didacll.madre.sdk.identity;

import java.util.Objects;

/** Identity of one Workflow owned by one Agent. */
public record WorkflowId(AgentId agentId, String name) {
    public WorkflowId {
        Objects.requireNonNull(agentId, "agentId");
        name = IdentityValues.requireName(name, "workflow name");
    }

    public ModuleId moduleId() { return agentId.moduleId(); }
}
