package io.github.didacll.madre.sdk.identity;
import java.util.Objects;
public record EffectProfileId(OperationId operationId, String name) {
    public EffectProfileId { Objects.requireNonNull(operationId, "operationId"); name = IdentityValues.requireName(name, "effect-profile name"); }
}
