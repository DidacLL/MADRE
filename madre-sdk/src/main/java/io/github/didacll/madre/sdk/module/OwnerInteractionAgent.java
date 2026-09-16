package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.operation.OwnerInteractionInvoker;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Optional semantic owner-interaction surface implemented by an Agent of a Module that can serve
 * the installation's CORE role. The host transports owner text and presents approved messages; the
 * Agent owns the semantic choice of its internal Operations, reasoning and continuation behavior.
 * This contract grants no privilege and is not required of ordinary or agentless Modules.
 */
public interface OwnerInteractionAgent extends Agent {
    CompletionStage<OwnerMessage> respond(OwnerInteractionInvoker invoker, String ownerText,
            Sensitivity sensitivity);

    CompletionStage<List<OwnerMessage>> followUps(OwnerInteractionInvoker invoker);
}
