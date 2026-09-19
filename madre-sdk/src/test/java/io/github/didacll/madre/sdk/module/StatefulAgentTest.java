package io.github.didacll.madre.sdk.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class StatefulAgentTest {
    private static final ModuleId MODULE = new ModuleId("test.stateful-agent");

    @Test void transitionsTypedStateAndCommitsBeforePublishingIt() {
        List<Integer> committed = new ArrayList<>();
        CounterAgent agent = new CounterAgent(1, committed::add);

        assertEquals(1, agent.value());
        assertEquals(2, agent.increment());
        assertEquals(List.of(2), committed);
        assertEquals(2, agent.value());
    }

    @Test void failedCommitLeavesPreviousStateVisible() {
        CounterAgent agent = new CounterAgent(1, value -> {
            throw new IllegalStateException("cannot persist " + value);
        });

        assertThrows(IllegalStateException.class, agent::increment);
        assertEquals(1, agent.value());
    }

    private static final class CounterAgent extends StatefulAgent<Integer> {
        private CounterAgent(int initialState, Consumer<Integer> committer) {
            super(initialState, committer);
        }

        private int value() { return readState(value -> value); }
        private int increment() { return updateState(value -> value + 1); }

        @Override public AgentId id() { return new AgentId(MODULE, "counter"); }
        @Override public String purpose() { return "Exercise optional Agent-owned state"; }
        @Override public Set<OperationId> operations() { return Set.of(); }
    }
}
