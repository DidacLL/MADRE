package org.madre.runtime;

import java.util.Objects;
import java.util.UUID;

public record BoundaryProfile(
        UUID profileId,
        BoundaryValue scope,
        BoundaryValue sensitivity,
        BoundaryValue privacy,
        BoundaryValue risk,
        BoundaryValue authority,
        BoundaryValue locality,
        BoundaryValue reversibility,
        boolean validationRequired,
        BoundaryValue trustLevel
) {
    public BoundaryProfile {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(sensitivity, "sensitivity");
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(locality, "locality");
        Objects.requireNonNull(reversibility, "reversibility");
        Objects.requireNonNull(trustLevel, "trustLevel");
    }

    public static BoundaryProfile publicProfile() {
        return uniform(BoundaryValue.PUBLIC, false);
    }

    public static BoundaryProfile internalProfile() {
        return uniform(BoundaryValue.INTERNAL, false);
    }

    public static BoundaryProfile restrictedProfile() {
        return uniform(BoundaryValue.RESTRICTED, true);
    }

    public static BoundaryProfile uniform(BoundaryValue value, boolean validationRequired) {
        return new BoundaryProfile(
                UUID.randomUUID(),
                value,
                value,
                value,
                value,
                value,
                value,
                value,
                validationRequired,
                value
        );
    }

    public UUID id() {
        return profileId;
    }

    public boolean compatibleWith(BoundaryProfile consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return scope.rank() <= consumer.scope.rank()
                && sensitivity.rank() <= consumer.sensitivity.rank()
                && privacy.rank() <= consumer.privacy.rank()
                && risk.rank() <= consumer.risk.rank()
                && authority.rank() <= consumer.authority.rank()
                && locality.rank() <= consumer.locality.rank()
                && reversibility.rank() <= consumer.reversibility.rank()
                && trustLevel.rank() <= consumer.trustLevel.rank()
                && (!validationRequired || consumer.validationRequired);
    }

    public BoundaryProfile join(BoundaryProfile other) {
        Objects.requireNonNull(other, "other");
        return new BoundaryProfile(
                UUID.randomUUID(),
                BoundaryValue.mostRestrictive(scope, other.scope),
                BoundaryValue.mostRestrictive(sensitivity, other.sensitivity),
                BoundaryValue.mostRestrictive(privacy, other.privacy),
                BoundaryValue.mostRestrictive(risk, other.risk),
                BoundaryValue.mostRestrictive(authority, other.authority),
                BoundaryValue.mostRestrictive(locality, other.locality),
                BoundaryValue.mostRestrictive(reversibility, other.reversibility),
                validationRequired || other.validationRequired,
                BoundaryValue.mostRestrictive(trustLevel, other.trustLevel)
        );
    }
}
