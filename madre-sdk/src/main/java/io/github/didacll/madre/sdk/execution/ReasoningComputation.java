package io.github.didacll.madre.sdk.execution;

/**
 * A typed reasoning or inference computation that may be executed by Kernel.
 *
 * <p>This marker is intentionally broader than text generation and intentionally
 * narrower than arbitrary I/O, application actions, tools, search, filesystems or
 * operating-system commands.</p>
 */
public interface ReasoningComputation<R> {
    /** Runtime result type used to bind a computation to a compatible reasoning mechanism. */
    Class<R> resultType();
}
