package io.github.didacll.madre.sdk.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.identity.WorkflowId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.testing.ProgrammableReasoningService;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

final class AgentExecutionTest {
    private static final ModuleId MODULE = new ModuleId("test.agent-execution");
    private static final Agent ACTOR = new Agent() {
        @Override public AgentId id() { return new AgentId(MODULE, "actor"); }
        @Override public String purpose() { return "Execute semantic behavior"; }
        @Override public Set<OperationId> operations() { return Set.of(); }
    };

    @Test void workflowIsExecutableBehaviorOwnedByItsActingAgent() {
        AgentContext context = context(ACTOR);
        Workflow<Integer, Integer> workflow = workflow(ACTOR.id(), value -> value + 1);

        assertEquals(2, ACTOR.execute(context, workflow, 1).toCompletableFuture().join());
        assertThrows(IllegalArgumentException.class, () -> ACTOR.execute(context,
                workflow(new AgentId(MODULE, "other"), value -> value), 1));
    }

    private static AgentContext context(Agent actor) {
        ModuleInvoker unavailable = new ModuleInvoker() {
            @Override public <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        return new AgentContext(actor, new ProgrammableReasoningService(), query -> List.of(),
                unavailable, Path.of("."));
    }

    private static Workflow<Integer, Integer> workflow(AgentId owner,
            java.util.function.IntUnaryOperator behavior) {
        return new Workflow<>() {
            @Override public WorkflowId id() { return new WorkflowId(owner, "increment"); }
            @Override public String purpose() { return "Increment a value"; }
            @Override public CompletionStage<Integer> execute(AgentContext context, Integer input) {
                return CompletableFuture.completedFuture(behavior.applyAsInt(input));
            }
        };
    }
}
