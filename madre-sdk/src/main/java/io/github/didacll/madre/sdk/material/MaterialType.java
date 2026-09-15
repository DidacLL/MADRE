package io.github.didacll.madre.sdk.material;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import java.util.Objects;

/** Java execution binding for one portable Material content type. */
public final class MaterialType<T> {
    private final MaterialTypeDefinition definition;
    private final Class<T> javaType;
    private final MaterialCodec<T> codec;

    public MaterialType(MaterialTypeId id, Class<T> javaType, String contentType,
            MaterialCodec<T> codec) {
        this(new MaterialTypeDefinition(id, contentType), javaType, codec);
    }

    public MaterialType(MaterialTypeDefinition definition, Class<T> javaType,
            MaterialCodec<T> codec) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public MaterialTypeDefinition definition() { return definition; }
    public MaterialTypeId id() { return definition.id(); }
    public Class<T> javaType() { return javaType; }
    public String contentType() { return definition.contentType(); }
    public MaterialCodec<T> codec() { return codec; }

    @Override public boolean equals(Object other) {
        return other instanceof MaterialType<?> that && id().equals(that.id());
    }
    @Override public int hashCode() { return id().hashCode(); }
    @Override public String toString() { return id().toString(); }
}
