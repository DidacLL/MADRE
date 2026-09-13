package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record AgentId(ModuleId moduleId, String name) {
    public AgentId { Objects.requireNonNull(moduleId, "moduleId"); name = IdentityValues.requireName(name, "agent name"); }
}
