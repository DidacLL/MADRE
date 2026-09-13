package io.github.didacll.madre.sdk.operation;

import java.util.concurrent.CompletionStage;

/** Executable binding for one bounded Module-owned Operation. */
@FunctionalInterface
public interface Operation<I, O> {
    CompletionStage<io.github.didacll.madre.sdk.material.Material<O>> invoke(OperationCall<I, O> call);
}
