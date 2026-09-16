package io.github.didacll.madre.sdk.module;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Optional Java authoring base for an {@link Agent} that owns private typed state.
 *
 * <p>This is an execution-side convenience, not a portable memory schema and not a second Agent
 * execution model. Subclasses still expose behavior through ordinary MADRE Operations. The state
 * type and its semantics remain owned by the concrete Agent/Module.</p>
 *
 * <p>State transitions are serialized. A caller may supply a state committer, for example a
 * Module-owned persistence function. The committer runs before the new state becomes visible; if
 * it throws, the in-memory state remains unchanged.</p>
 */
public abstract class StatefulAgent<S> implements Agent {
    private final Object stateMonitor = new Object();
    private final Consumer<? super S> stateCommitter;
    private S state;

    protected StatefulAgent(S initialState) {
        this(initialState, ignored -> { });
    }

    protected StatefulAgent(S initialState, Consumer<? super S> stateCommitter) {
        state = Objects.requireNonNull(initialState, "initialState");
        this.stateCommitter = Objects.requireNonNull(stateCommitter, "stateCommitter");
    }

    /** Reads the current Agent-owned state under the same serialization boundary as updates. */
    protected final <R> R readState(Function<? super S, ? extends R> reader) {
        Objects.requireNonNull(reader, "reader");
        synchronized (stateMonitor) {
            return reader.apply(state);
        }
    }

    /** Applies and commits one atomic typed state transition. */
    protected final S updateState(UnaryOperator<S> transition) {
        Objects.requireNonNull(transition, "transition");
        synchronized (stateMonitor) {
            S next = Objects.requireNonNull(transition.apply(state),
                    "state transition returned null");
            stateCommitter.accept(next);
            state = next;
            return next;
        }
    }
}
