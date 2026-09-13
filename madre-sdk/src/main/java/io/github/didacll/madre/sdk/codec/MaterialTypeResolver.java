package io.github.didacll.madre.sdk.codec;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.material.MaterialType;

/** Resolves a public declaration to the Module-owned typed codec used at runtime. */
@FunctionalInterface
public interface MaterialTypeResolver {
    MaterialType<?> resolve(MaterialTypeId id, String contentType);
}
