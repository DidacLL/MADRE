package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record MaterialTypeId(ModuleId moduleId, String name) {
    public MaterialTypeId { Objects.requireNonNull(moduleId, "moduleId"); name = IdentityValues.requireName(name, "material-type name"); }
}
