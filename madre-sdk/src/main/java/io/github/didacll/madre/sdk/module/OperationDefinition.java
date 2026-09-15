package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.OperationId;
import java.util.Map;
import java.util.Objects;

/**
 * Portable contract for one bounded callable piece of Module behavior.
 *
 * <p>This value is deliberately language-neutral and structural: independently decoded/adapted
 * contracts with the same canonical facts represent the same Operation. Java payload typing lives
 * on {@link OperationBinding}, {@code OperationCall} and {@code Operation}; it is not encoded by
 * phantom type parameters on this portable contract.</p>
 */
public final class OperationDefinition {
    private final OperationId id;
    private final String purpose;
    private final OperationVisibility visibility;
    private final Map<MaterialTypeId, Privacy> acceptedMaterial;
    private final Map<MaterialTypeId, Sensitivity> producedMaterial;
    private final Map<EffectProfileId, EffectProfile> effectProfiles;

    public OperationDefinition(OperationId id, String purpose, OperationVisibility visibility,
            Map<MaterialTypeId, Privacy> acceptedMaterial,
            Map<MaterialTypeId, Sensitivity> producedMaterial,
            Map<EffectProfileId, EffectProfile> effectProfiles) {
        this.id = Objects.requireNonNull(id, "id");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        this.purpose = purpose.strip();
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.acceptedMaterial = Map.copyOf(acceptedMaterial);
        this.producedMaterial = Map.copyOf(producedMaterial);
        this.effectProfiles = Map.copyOf(effectProfiles);
        if (this.acceptedMaterial.isEmpty()) {
            throw new IllegalArgumentException("an Operation must accept Material");
        }
        if (this.acceptedMaterial.containsValue(Privacy.SYSTEM_RESERVED)) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Privacy is not an ordinary Operation boundary");
        }
        if (this.producedMaterial.containsValue(Sensitivity.SYSTEM_RESERVED)) {
            throw new IllegalArgumentException(
                    "SYSTEM_RESERVED Sensitivity is not an ordinary Operation result");
        }
        if (!this.effectProfiles.entrySet().stream().allMatch(entry ->
                entry.getKey().equals(entry.getValue().id())
                        && entry.getKey().operationId().equals(this.id))) {
            throw new IllegalArgumentException(
                    "EffectProfiles must be identified by and owned by this Operation");
        }
    }

    public OperationId id() { return id; }
    public String purpose() { return purpose; }
    public OperationVisibility visibility() { return visibility; }
    public Map<MaterialTypeId, Privacy> acceptedMaterial() { return acceptedMaterial; }
    public Map<MaterialTypeId, Sensitivity> producedMaterial() { return producedMaterial; }
    public Map<EffectProfileId, EffectProfile> effectProfiles() { return effectProfiles; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OperationDefinition that)) return false;
        return id.equals(that.id)
                && purpose.equals(that.purpose)
                && visibility == that.visibility
                && acceptedMaterial.equals(that.acceptedMaterial)
                && producedMaterial.equals(that.producedMaterial)
                && effectProfiles.equals(that.effectProfiles);
    }

    @Override public int hashCode() {
        return Objects.hash(id, purpose, visibility, acceptedMaterial, producedMaterial,
                effectProfiles);
    }

    @Override public String toString() {
        return id.toString();
    }
}
