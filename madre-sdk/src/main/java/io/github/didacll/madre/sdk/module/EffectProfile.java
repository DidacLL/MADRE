package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import java.util.Objects;

/** One bounded consequential execution variant of an Operation. */
public record EffectProfile(EffectProfileId id, Risk risk, Autonomy autonomy) {
    public EffectProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(autonomy, "autonomy");
        if (risk == Risk.SYSTEM_RESERVED || autonomy == Autonomy.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED values are not an ordinary EffectProfile");
        }
    }

    /** Returns whether the actual combined non-user causal Integrity can carry this profile. */
    public boolean isSupportedBy(Integrity causalIntegrity) {
        return Math.min(risk.rank(), autonomy.rank())
                <= Objects.requireNonNull(causalIntegrity, "causalIntegrity").rank();
    }
}
