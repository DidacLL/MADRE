package io.github.didacll.madre.sdk.module;
import io.github.didacll.madre.sdk.identity.SkillId;
import java.util.Objects;
public record SkillDefinition(SkillId id, String purpose) {
    public SkillDefinition { Objects.requireNonNull(id, "id"); if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose must not be blank"); purpose = purpose.strip(); }
}
