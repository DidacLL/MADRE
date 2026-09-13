package io.github.didacll.madre.sdk.material;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import java.util.Objects;

/** A Module semantic content type and the codec used by its owning Module. */
public final class MaterialType<T> {
    private final MaterialTypeId id;
    private final Class<T> javaType;
    private final String contentType;
    private final MaterialCodec<T> codec;

    public MaterialType(MaterialTypeId id, Class<T> javaType, String contentType, MaterialCodec<T> codec) {
        this.id = Objects.requireNonNull(id, "id");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
        if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("contentType must not be blank");
        this.contentType = contentType.strip();
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public MaterialTypeId id() { return id; }
    public Class<T> javaType() { return javaType; }
    public String contentType() { return contentType; }
    public MaterialCodec<T> codec() { return codec; }

    @Override public boolean equals(Object other) {
        return other instanceof MaterialType<?> that && id.equals(that.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id.toString(); }
}
