package io.github.didacll.madre.sdk.module;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Optional reusable specialization for Agents whose domain includes conversation. It has no
 * CORE, host protocol, or privilege semantics.
 */
public interface ConversationalAgent extends Agent {
    CompletionStage<ConversationMessage> respond(AgentContext context,
            ConversationMessage message);

    default CompletionStage<List<ConversationMessage>> followUps(AgentContext context) {
        context.requireActor(this);
        return CompletableFuture.completedFuture(List.of());
    }
}
