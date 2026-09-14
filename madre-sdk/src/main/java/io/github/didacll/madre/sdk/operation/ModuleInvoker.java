package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.sdk.material.Material;
import java.util.concurrent.CompletionStage;

/**
 * Caller-bound runtime port for invoking another installed Module's exact PUBLIC Operation.
 * The receiver remains a Module: contract-valid callee Material is returned unchanged when
 * that foreign Material can structurally reach the calling Module.
 */
public interface ModuleInvoker {
    <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call);
}
