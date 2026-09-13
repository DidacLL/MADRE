package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record WorkflowId(ModuleId moduleId, String name) {
    public WorkflowId { Objects.requireNonNull(moduleId, "moduleId"); name = IdentityValues.requireName(name, "workflow name"); }
}
