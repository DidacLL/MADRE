package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record MaterialId(ModuleId moduleId, String value) {
    public MaterialId { Objects.requireNonNull(moduleId, "moduleId"); value = IdentityValues.requireName(value, "material identity"); }
}
