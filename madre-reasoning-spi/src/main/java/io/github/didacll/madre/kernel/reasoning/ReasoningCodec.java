package io.github.didacll.madre.kernel.reasoning;

/** Durable byte codec for one reasoning computation or result type. */
public interface ReasoningCodec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
}
