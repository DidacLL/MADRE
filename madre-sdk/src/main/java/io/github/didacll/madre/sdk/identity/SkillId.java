package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record SkillId(ModuleId moduleId, String name) {
    public SkillId { Objects.requireNonNull(moduleId, "moduleId"); name = IdentityValues.requireName(name, "skill name"); }
}
