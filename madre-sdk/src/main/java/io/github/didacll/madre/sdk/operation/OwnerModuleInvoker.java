package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/**
 * Host/application expert/debug port for invoking one exact installed Operation without treating
 * the call as Module-to-Module exposure, owner semantic interaction, or external/public disclosure.
 *
 * <p>This port is deliberately not part of {@code ModuleContext}. Installed Modules receive only
 * {@link ModuleInvoker} for cross-Module invocation.</p>
 */
public interface OwnerModuleInvoker {
    <I, O> CompletionStage<Material<O>> invokeOwner(OperationCall<I, O> call);
}
