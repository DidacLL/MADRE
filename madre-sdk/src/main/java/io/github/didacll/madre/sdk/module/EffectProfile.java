package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import java.util.Objects;

/** One bounded consequential execution variant of an Operation. */
public record EffectProfile(EffectProfileId id, Risk risk, Autonomy autonomy) {
    public EffectProfile { Objects.requireNonNull(id, "id"); Objects.requireNonNull(risk, "risk"); Objects.requireNonNull(autonomy, "autonomy"); }
    public int nonUserCausalDemandRank() { return Math.min(risk.rank(), autonomy.rank()); }
}
