package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record EffectProfileId(ModuleId moduleId, String name) {
    public EffectProfileId { Objects.requireNonNull(moduleId, "moduleId"); name = IdentityValues.requireName(name, "effect-profile name"); }
}
