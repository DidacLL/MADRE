package io.github.didacll.madre.sdk.invocation;

import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/** Executable boundary retained inside a running Module. */
@FunctionalInterface
public interface ModuleEndpoint {
    CompletionStage<Material<?>> invoke(OperationId operation, Material<?> input);
}
