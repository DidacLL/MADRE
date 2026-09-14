package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/**
 * Host-side external/PUBLIC invocation boundary. PUBLIC semantic transformation is mandatory
 * on this port. It is intentionally not supplied through ModuleContext.
 */
public interface PublicModuleInvoker {
    <I, O> CompletionStage<Material<O>> invokePublic(OperationCall<I, O> call);
}
