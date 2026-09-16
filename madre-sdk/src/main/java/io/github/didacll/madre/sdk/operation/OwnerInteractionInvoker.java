package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/**
 * Host/product-only port for invoking an Operation explicitly bound as an owner-interaction entry
 * in the ordinary installed Module assigned the CORE role. This port is not exposed through
 * ModuleContext and grants no cross-Module authority.
 */
public interface OwnerInteractionInvoker {
    <I, O> CompletionStage<Material<O>> invokeOwnerInteraction(OperationCall<I, O> call);
}
