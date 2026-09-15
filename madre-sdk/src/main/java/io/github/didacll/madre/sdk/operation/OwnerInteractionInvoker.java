package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/**
 * Host/product-only port for invoking an Operation explicitly bound as an owner-interaction entry.
 * This port is not exposed through ModuleContext and grants no Module authority over another
 * Module's PRIVATE Operations.
 */
public interface OwnerInteractionInvoker {
    <I, O> CompletionStage<Material<O>> invokeOwnerInteraction(OperationCall<I, O> call);
}
