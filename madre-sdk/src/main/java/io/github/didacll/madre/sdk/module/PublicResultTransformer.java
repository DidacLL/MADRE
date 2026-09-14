package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.sdk.material.Material;

/**
 * Module-owned semantic transformation applied before a PUBLIC Operation result leaves
 * the installed Module boundary.
 */
@FunctionalInterface
public interface PublicResultTransformer<O> {
    Material<O> transform(Material<O> internalResult);
}
