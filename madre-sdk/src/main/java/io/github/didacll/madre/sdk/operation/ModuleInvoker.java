package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/** Public runtime port for invoking one installed Module's reachable PUBLIC Operation. */
public interface ModuleInvoker {
    <I, O> CompletionStage<Material<O>> invokePublic(OperationCall<I, O> call);
}
