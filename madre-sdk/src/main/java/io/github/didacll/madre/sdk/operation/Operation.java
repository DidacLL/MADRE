package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/** Executable binding for one bounded Module-owned Operation. */
@FunctionalInterface
public interface Operation<I, O> {
    CompletionStage<Material<O>> invoke(Material<I> input);
}
