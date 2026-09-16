package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record OperationId(ModuleId moduleId, String name) {
    public OperationId { Objects.requireNonNull(moduleId, "moduleId"); name = IdentityValues.requireName(name, "operation name"); }
}
