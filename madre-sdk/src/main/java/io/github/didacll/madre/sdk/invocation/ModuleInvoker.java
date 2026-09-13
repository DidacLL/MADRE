package io.github.didacll.madre.sdk.invocation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/** Routes an invocation into one selected target Module Operation. */
public interface ModuleInvoker {
    CompletionStage<Material<?>> invoke(ModuleInvocation invocation);
}
