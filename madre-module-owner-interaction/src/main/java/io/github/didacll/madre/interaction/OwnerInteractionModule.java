package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationResult;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentContext;
import io.github.didacll.madre.sdk.module.ConversationMessage;
import io.github.didacll.madre.sdk.module.ConversationalAgent;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Ordinary SDK-built conversational Module shipped as the initial CORE candidate. */
public final class OwnerInteractionModule implements Module {
    public static final ModuleId ID = new ModuleId("owner-interaction");
    public static final MaterialType<String> OWNER_TEXT = new MaterialType<>(
            new MaterialTypeId(ID, "owner-text"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    public static final MaterialType<String> RESPONSE_TEXT = new MaterialType<>(
            new MaterialTypeId(ID, "response-text"), String.class,
            "text/plain; charset=utf-8", MaterialCodecs.utf8String());
    public static final OperationId RESPOND = new OperationId(ID, "respond");
    public static final AgentId CONVERSATION_AGENT = new AgentId(ID, "conversation");

    private final OperationDefinition respondDefinition = new OperationDefinition(RESPOND,
            "Respond to one owner conversation message", Map.of(OWNER_TEXT.id(), Privacy.SECRET),
            Map.of(RESPONSE_TEXT.id(), Sensitivity.S5), Map.of());
    private final OperationBinding<String, String> respond = OperationBinding.operation(
            respondDefinition, Operation.of((context, call) -> {
                TextGenerationCommand command = TextGenerationCommand.prompt(
                        call.input().payload(), 512);
                ReasoningRequest<TextGenerationResult, TextGenerationCommand> request =
                        ReasoningRequest.immediate(context.actor(), call, command, 100,
                                Duration.ofSeconds(90), ReasoningRetryPolicy.none(),
                                Optional.empty(), ReasoningPreferences.requirements());
                return context.reasoning().execute(request).thenApply(result ->
                        text(RESPONSE_TEXT, result.text(), request.sensitivity()));
            }));
    private final ConversationAgent agent = new ConversationAgent();

    @Override public ModuleId id() { return ID; }
    @Override public String version() { return "0.5.0"; }
    @Override public String purpose() { return "General owner conversation and fallback agency"; }
    @Override public Collection<? extends MaterialType<?>> materialTypes() {
        return List.of(OWNER_TEXT, RESPONSE_TEXT);
    }
    @Override public Collection<? extends Agent> agents() { return List.of(agent); }
    @Override public Collection<? extends OperationBinding<?, ?>> operations() {
        return List.of(respond);
    }
    @Override public Set<OperationId> exposedOperations() { return Set.of(RESPOND); }

    public ConversationalAgent conversationalAgent() { return agent; }

    private final class ConversationAgent implements ConversationalAgent {
        @Override public AgentId id() { return CONVERSATION_AGENT; }
        @Override public String purpose() { return "Understand and answer the Owner"; }
        @Override public Integrity integrity() { return Integrity.I3; }
        @Override public Set<OperationId> operations() { return Set.of(RESPOND); }

        @Override
        public CompletionStage<ConversationMessage> respond(AgentContext context,
                ConversationMessage message) {
            if (context.actor() != this) {
                throw new IllegalArgumentException("AgentContext belongs to another acting Agent");
            }
            if (message.role() != ConversationMessage.Role.HUMAN) {
                throw new IllegalArgumentException("Owner interaction accepts HUMAN messages");
            }
            Material<String> input = text(OWNER_TEXT, message.text(), message.sensitivity());
            OperationCall<String, String> bounded = OperationCall.withoutEffect(respondDefinition,
                    input);
            return context.modules().invoke(bounded).thenApply(result ->
                    new ConversationMessage(ConversationMessage.Role.AGENT, result.payload(),
                            result.sensitivity()));
        }
    }

    private static Material<String> text(MaterialType<String> type, String value,
            Sensitivity sensitivity) {
        return new Material<>(new MaterialId(ID, UUID.randomUUID().toString()), type, value,
                sensitivity);
    }
}
